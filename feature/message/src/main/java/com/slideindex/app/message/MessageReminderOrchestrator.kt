package com.slideindex.app.message

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
        if (isAlreadyDisplayed(plan)) return
        if (settings.interceptNotifications) {
            shadeActions.cancelDismissibleFromShadeOnMain(listener, sbn)
        }
        mainHandler.post { showPlan(context, plan) }
    }

    fun onAction(context: Context, plan: MessageDisplayPlan, action: MessageAction) {
        if (action == MessageAction.QuickReply) {
            pauseAutoDismissForPlan(plan)
            actionExecutor.execute(
                context,
                plan.data,
                action,
                settingsRepository.readSnapshot(),
                launchPort,
                onQuickReplySent = { dismissPlan(plan) },
                onQuickReplyCancelled = { resumeAutoDismissForPlan(plan) },
            )
            return
        }
        actionExecutor.execute(
            context,
            plan.data,
            action,
            settingsRepository.readSnapshot(),
            launchPort,
        )
        dismissPlan(plan)
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
        val styles = plan.enabledStyles()
        if (styles.isEmpty()) return false
        return styles.all { overlayPort.containsNotification(it, plan.data) }
    }

    private fun showPlan(context: Context, plan: MessageDisplayPlan) {
        overlayPort.showPlan(
            context = context,
            plan = plan,
            onAction = { action -> onAction(context, plan, action) },
            onDismiss = { dismissPlan(plan) },
        )
    }
}
