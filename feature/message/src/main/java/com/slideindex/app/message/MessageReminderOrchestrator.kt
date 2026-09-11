package com.slideindex.app.message

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.slideindex.app.notification.NotificationIntentLaunchPort
import com.slideindex.app.notification.NotificationSbnCache
import com.slideindex.app.notification.NotificationShadeActions
import com.slideindex.app.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageReminderOrchestrator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val launchPort: NotificationIntentLaunchPort,
    private val overlayPort: MessageOverlayPort,
    private val themePort: MessageThemePort,
    private val foregroundPort: MessageForegroundPort,
    private val environmentPort: MessageEnvironmentPort,
    private val actionExecutor: MessageActionExecutor,
    private val shadeActions: NotificationShadeActions,
    private val shortcutIconPort: NotificationShortcutIconPort,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settingsWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 锁屏期间到达、待解锁后自动打开的最后一条消息。 */
    @Volatile
    private var pendingUnlockMessage: NotificationData? = null

    fun onNotificationRemoved(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
        reason: Int,
    ) {
        if (!shouldDismissReminderForRemoval(reason)) return

        val settings = settingsRepository.readSnapshot().messageReminderSettings
        if (!settings.enabled) return

        val key = sbn.key
        if (key.isBlank()) return

        if (pendingUnlockMessage?.key == key) {
            pendingUnlockMessage = null
        }

        mainHandler.post {
            val removedData = NotificationData.fromSbn(context, sbn)
            val activeConversationKeys = runCatching { listener.activeNotifications?.toList() }
                .getOrNull()
                .orEmpty()
                .mapNotNull { active ->
                    NotificationData.fromSbn(context, active)?.let { NotificationData.conversationIdentityKey(it) }
                }
                .filter { it.isNotBlank() }
                .toSet()
            MessageStyle.entries.forEach { style ->
                if (style == MessageStyle.CNotice) {
                    overlayPort.reconcileCNoticeAfterRemoval(
                        removedNotificationKey = key,
                        removedConversationKey = removedData?.let { NotificationData.conversationIdentityKey(it) },
                        activeConversationKeys = activeConversationKeys,
                    )
                } else {
                    overlayPort.dismissEntriesForKey(style, key)
                }
            }
        }
    }

    fun onNotificationPosted(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
    ) {
        val settings = settingsRepository.readSnapshot().messageReminderSettings
        if (!settings.enabled || !settings.hasAnyStyleEnabled()) return

        NotificationSbnCache.cacheActive(sbn)

        val data = NotificationData.fromSbn(context, sbn) ?: return
        if (!MessageNotificationFilter.shouldShowNotification(
                context,
                settings,
                sbn,
                data,
                environmentPort,
                foregroundPort,
            )
        ) {
            return
        }
        val plan = MessagePlanBuilder.buildDisplayPlan(context, settings, data, themePort) ?: return
        val acceptedByDedup = MessageNotificationFilter.dedup(data)
        if (environmentPort.isScreenLocked(context)) {
            pendingUnlockMessage = data
        }
        if (settings.interceptNotifications) {
            shadeActions.cancelDismissibleFromShadeOnMain(listener, sbn)
        }
        mainHandler.post {
            if (!acceptedByDedup) {
                val conversationKey = NotificationData.conversationIdentityKey(data)
                val allowCNoticeUpdate = plan.showCNotice &&
                    overlayPort.containsCNoticeConversation(conversationKey)
                if (!allowCNoticeUpdate) return@post
            }
            if (isAlreadyDisplayed(plan) && !plan.showCNotice) return@post
            val showDanmaku = acceptedByDedup &&
                (!plan.showDanmaku || MessageNotificationFilter.acceptDanmaku(data))
            showPlan(context, plan, showDanmaku = showDanmaku)
            scheduleShortcutIconRefresh(context, sbn, data, plan)
        }
    }

    private fun scheduleShortcutIconRefresh(
        context: Context,
        sbn: StatusBarNotification,
        data: NotificationData,
        plan: MessageDisplayPlan,
    ) {
        if (!plan.showCNotice) return
        val shortcutId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sbn.notification?.shortcutId
        } else {
            null
        }
        if (shortcutId.isNullOrBlank()) return
        val conversationKey = NotificationData.conversationIdentityKey(data)
        val userId = NotificationData.shortcutUserId(sbn.user)
        settingsWriteScope.launch {
            val icon = shortcutIconPort.loadShortcutIcon(sbn.packageName, shortcutId, userId)
            if (icon != null) {
                mainHandler.post {
                    overlayPort.refreshCNoticeConversationIcon(conversationKey, icon)
                }
            }
        }
    }

    /**
     * 屏幕解锁（[android.content.Intent.ACTION_USER_PRESENT]）后调用：
     * 若开启“解锁后进入最后一条消息”，打开锁屏期间到达的最后一条消息。
     */
    fun onUserPresent(context: Context) {
        val settings = settingsRepository.readSnapshot().messageReminderSettings
        val pending = pendingUnlockMessage ?: return
        if (!shouldAutoOpenLastMessageOnUnlock(settings, pending)) return
        pendingUnlockMessage = null
        mainHandler.postDelayed({
            if (pending.packageName in settings.openLastMessageAlwaysPackages) {
                openPendingMessage(context, pending)
            } else {
                overlayPort.showUnlockConfirmation(
                    context = context,
                    data = pending,
                    autoDismissSeconds = settings.unlockConfirmationAutoDismissSeconds,
                    onConfirm = { alwaysAllow ->
                        if (alwaysAllow) {
                            settingsWriteScope.launch {
                                settingsRepository.setMessageOpenLastAlways(pending.packageName, true)
                            }
                        }
                        openPendingMessage(context, pending)
                    },
                    onDismiss = {},
                )
            }
        }, UNLOCK_OPEN_DELAY_MS)
    }

    private fun openPendingMessage(context: Context, data: NotificationData) {
        val opened = launchPort.open(context, data)
        if (!opened) {
            Log.w(TAG, "Failed to open last message after unlock for ${data.packageName}")
        }
    }

    fun onAction(context: Context, plan: MessageDisplayPlan, action: MessageAction) {
        when (action) {
            MessageAction.QuickReply,
            MessageAction.QuickReplyAndIgnore,
            MessageAction.QuickReplyAndRemove,
            -> {
                pauseAutoDismissForPlan(plan)
                val onSent = when (action) {
                    MessageAction.QuickReply -> { { resumeAutoDismissForPlan(plan) } }
                    MessageAction.QuickReplyAndIgnore -> { { dismissPlan(plan) } }
                    MessageAction.QuickReplyAndRemove -> {
                        {
                            actionExecutor.cancelNotification(plan.data.key)
                            dismissPlan(plan)
                        }
                    }
                }
                actionExecutor.execute(
                    context,
                    plan.data,
                    action,
                    settingsRepository.readSnapshot(),
                    launchPort,
                    onQuickReplySent = onSent,
                    onQuickReplyCancelled = { resumeAutoDismissForPlan(plan) },
                )
            }
            MessageAction.IgnoreAll -> overlayPort.dismissAllReminders()
            MessageAction.IgnoreAndRemoveAll -> {
                val keys = overlayPort.snapshotDisplayedKeys()
                keys.forEach { key -> actionExecutor.cancelNotification(key) }
                overlayPort.dismissAllReminders()
            }
            MessageAction.IgnoreSameSource ->
                overlayPort.dismissSameSourceReminders(plan.data.conversationSourceKey)
            MessageAction.IgnoreSameSourceAndRemove -> {
                val sourceKey = plan.data.conversationSourceKey
                val keys = overlayPort.snapshotDisplayedKeysForSource(sourceKey)
                keys.forEach { key -> actionExecutor.cancelNotification(key) }
                overlayPort.dismissSameSourceReminders(sourceKey)
            }
            else -> {
                actionExecutor.execute(
                    context,
                    plan.data,
                    action,
                    settingsRepository.readSnapshot(),
                    launchPort,
                )
                dismissPlan(plan)
            }
        }
    }

    fun dismissPlan(plan: MessageDisplayPlan) {
        mainHandler.post {
            plan.enabledStyles().forEach { style ->
                overlayPort.dismissEntry(style, plan.data.key, plan.data.postTime)
            }
        }
    }

    private fun pauseAutoDismissForPlan(plan: MessageDisplayPlan) {
        mainHandler.post {
            plan.enabledStyles().forEach { style ->
                overlayPort.pauseAutoDismiss(style, plan.data.key, plan.data.postTime)
            }
        }
    }

    private fun resumeAutoDismissForPlan(plan: MessageDisplayPlan) {
        mainHandler.post {
            plan.enabledStyles().forEach { style ->
                overlayPort.resumeAutoDismiss(style, plan.data.key, plan.data.postTime)
            }
        }
    }

    fun onConfigurationChanged(context: Context, newConfig: Configuration) {
        if (newConfig.orientation != Configuration.ORIENTATION_PORTRAIT) return
        val settings = settingsRepository.readSnapshot().messageReminderSettings
        if (!settings.danmakuEnabled || settings.portraitDanmaku) return
        mainHandler.post { overlayPort.detachDanmaku() }
    }

    private fun isAlreadyDisplayed(plan: MessageDisplayPlan): Boolean {
        val overlayStyles = plan.enabledStyles().filter {
            it == MessageStyle.FloatIcon ||
                it == MessageStyle.SideBubble ||
                it == MessageStyle.CNotice
        }
        if (overlayStyles.isEmpty()) return false
        return overlayStyles.all { overlayPort.containsNotification(it, plan.data) }
    }

    private fun showPlan(
        context: Context,
        plan: MessageDisplayPlan,
        showDanmaku: Boolean = true,
    ) {
        overlayPort.showPlan(
            context = context,
            plan = plan,
            onAction = { action -> onAction(context, plan, action) },
            onDismiss = { dismissPlan(plan) },
            showDanmaku = showDanmaku,
        )
    }

    private fun shouldDismissReminderForRemoval(reason: Int): Boolean =
        reason != NotificationListenerService.REASON_SNOOZED &&
            reason != NotificationListenerService.REASON_LISTENER_CANCEL

    private companion object {
        const val TAG = "MessageReminder"
        /** 解锁后稍作延迟再打开，避免与桌面/解锁动画竞争。 */
        const val UNLOCK_OPEN_DELAY_MS = 900L
    }
}
