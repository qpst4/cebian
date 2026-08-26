package com.slideindex.app.message

import android.content.Context
import android.content.res.Configuration
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
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settingsWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 锁屏期间到达、待解锁后自动打开的最后一条消息。 */
    @Volatile
    private var pendingUnlockMessage: NotificationData? = null

    fun onNotificationRemoved(sbn: StatusBarNotification, reason: Int) {
        if (!shouldDismissReminderForRemoval(reason)) return

        val settings = settingsRepository.readSnapshot().messageReminderSettings
        if (!settings.enabled) return

        val key = sbn.key
        if (key.isBlank()) return

        if (pendingUnlockMessage?.key == key) {
            pendingUnlockMessage = null
        }

        mainHandler.post {
            MessageStyle.entries.forEach { style ->
                overlayPort.dismissEntriesForKey(style, key)
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
        if (!MessageNotificationFilter.dedup(data)) return

        val plan = MessagePlanBuilder.buildDisplayPlan(context, settings, data, themePort) ?: return
        if (environmentPort.isScreenLocked(context)) {
            pendingUnlockMessage = data
        }
        if (settings.interceptNotifications) {
            shadeActions.cancelDismissibleFromShadeOnMain(listener, sbn)
        }
        mainHandler.post {
            if (isAlreadyDisplayed(plan)) return@post
            showPlan(context, plan)
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
            it == MessageStyle.FloatIcon || it == MessageStyle.SideBubble
        }
        if (overlayStyles.isEmpty()) return false
        return overlayStyles.all { overlayPort.containsNotification(it, plan.data) }
    }

    private fun showPlan(context: Context, plan: MessageDisplayPlan) {
        overlayPort.showPlan(
            context = context,
            plan = plan,
            onAction = { action -> onAction(context, plan, action) },
            onDismiss = { dismissPlan(plan) },
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
