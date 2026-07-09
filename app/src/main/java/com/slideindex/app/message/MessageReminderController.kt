package com.slideindex.app.message

import com.slideindex.app.di.AppEntryPoints
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.slideindex.app.notification.NotificationIntentLauncher
import com.slideindex.app.notification.NotificationSbnCache
import com.slideindex.app.overlay.DanmakuOverlayWindow
import com.slideindex.app.overlay.FloatIconOverlayWindow
import com.slideindex.app.overlay.MessageCardOverlayWindow
import com.slideindex.app.overlay.SideBubbleOverlayWindow
import com.slideindex.app.service.MediaNotificationListener
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.VolumeControlHelper

object MessageReminderController {
    private const val TAG = "MessageReminder"
    private const val DEDUP_WINDOW_MS = 15_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val recentPostKeys = LinkedHashMap<String, Long>()
    private val recentContentKeys = LinkedHashMap<String, Long>()
    private val dndUntilByPackage = mutableMapOf<String, Long>()
    private val dedupLock = Any()

    fun onNotificationPosted(
        context: Context,
        listener: NotificationListenerService,
        sbn: StatusBarNotification,
    ) {
        val deps = runCatching { AppEntryPoints.dependencies(context) }.getOrNull() ?: return
        val settings = deps.settingsRepository.readSnapshot().messageReminderSettings
        if (!settings.enabled) return

        NotificationSbnCache.cacheActive(sbn)

        val data = NotificationData.fromSbn(context, sbn) ?: return
        if (!shouldShowNotification(context, settings, sbn, data)) return
        if (!dedup(data)) return

        val plan = buildDisplayPlan(context, settings, data) ?: return
        if (isAlreadyDisplayed(plan)) return
        mainHandler.post { showPlan(context, plan) }
    }

    fun onAction(
        context: Context,
        plan: MessageDisplayPlan,
        action: MessageAction,
    ) {
        MessageActionExecutor.execute(context, plan.data, action)
        dismissPlan(plan)
    }

    fun dismissPlan(plan: MessageDisplayPlan) {
        mainHandler.post {
            when (plan.primaryStyle) {
                MessageStyle.DarkCard -> MessageCardOverlayWindow.dismissEntry(
                    plan.data.key,
                    plan.data.postTime,
                )
                MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissEntry(
                    plan.data.key,
                    plan.data.postTime,
                )
                MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissEntry(
                    plan.data.key,
                    plan.data.postTime,
                )
                else -> Unit
            }
        }
    }

    private fun shouldShowNotification(
        context: Context,
        settings: MessageSettings,
        sbn: StatusBarNotification,
        data: NotificationData,
    ): Boolean {
        if (sbn.packageName == context.packageName) return false
        if (!settings.isPackageAllowed(data.packageName)) return false
        if (!settings.passesAppFilter(data)) return false

        if (settings.suppressWhenSystemDnd && VolumeControlHelper.isDndEnabled(context)) {
            return false
        }

        val foregroundPackage = OverlayService.foregroundPackage
            ?: SlideIndexAccessibilityService.currentForegroundPackage()
        if (foregroundPackage != null && foregroundPackage in settings.dndPackages) {
            return false
        }

        val dndUntil = dndUntilByPackage[data.packageName] ?: 0L
        if (System.currentTimeMillis() < dndUntil) return false

        val notification = sbn.notification ?: return false
        if (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) return false
        if (notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return false

        val category = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            notification.category
        } else {
            null
        }
        if (category == android.app.Notification.CATEGORY_PROGRESS) return false
        if (category == android.app.Notification.CATEGORY_SERVICE) return false

        return true
    }

    private fun dedup(data: NotificationData): Boolean {
        val postKey = "${data.key}|${data.postTime}"
        val contentKey = contentSignature(data)
        val now = System.currentTimeMillis()
        synchronized(dedupLock) {
            recentPostKeys.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
            recentContentKeys.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
            if (recentPostKeys.containsKey(postKey)) return false
            // Ignore system re-posts of the same chat message (common when another group gets a new message).
            if (recentContentKeys.containsKey(contentKey)) return false
            recentPostKeys[postKey] = now
            recentContentKeys[contentKey] = now
        }
        return true
    }

    private fun contentSignature(data: NotificationData): String =
        "${data.key}|${data.title}|${data.content}"

    private fun isAlreadyDisplayed(plan: MessageDisplayPlan): Boolean {
        val data = plan.data
        return when (plan.primaryStyle) {
            MessageStyle.DarkCard -> MessageCardOverlayWindow.containsNotification(data)
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.containsNotification(data)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.containsNotification(data)
            else -> false
        }
    }

    private fun buildDisplayPlan(
        context: Context,
        settings: MessageSettings,
        data: NotificationData,
    ): MessageDisplayPlan? {
        val isLandscape = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

        val showPrimary = when (settings.style) {
            MessageStyle.FloatIcon -> true
            MessageStyle.DarkCard, MessageStyle.SideBubble -> settings.primaryStyleEnabled
            else -> false
        } && !(isLandscape && settings.hideInLandscape)

        val showDanmaku = settings.danmakuEnabled &&
            if (isLandscape) settings.landscapeDanmaku else settings.portraitDanmaku

        if (!showPrimary && !showDanmaku) return null

        val primaryStyle = if (showPrimary) settings.style else null
        val primaryTheme = if (showPrimary) {
            MessageThemeCatalog.themeFor(settings.style, settings.themeId)
        } else {
            null
        }

        return MessageDisplayPlan(
            data = data,
            primaryStyle = primaryStyle,
            cardTheme = primaryTheme,
            showDanmaku = showDanmaku,
            danmakuTheme = if (showDanmaku) {
                MessageThemeCatalog.themeFor(MessageStyle.Danmaku, settings.danmakuThemeId)
            } else {
                null
            },
            settings = settings,
        )
    }

    private fun showPlan(context: Context, plan: MessageDisplayPlan) {
        when (plan.primaryStyle) {
            MessageStyle.DarkCard -> {
                SideBubbleOverlayWindow.dismissImmediate()
                FloatIconOverlayWindow.dismissImmediate()
            }
            MessageStyle.SideBubble -> {
                MessageCardOverlayWindow.dismissImmediate()
                FloatIconOverlayWindow.dismissImmediate()
            }
            MessageStyle.FloatIcon -> {
                MessageCardOverlayWindow.dismissImmediate()
                SideBubbleOverlayWindow.dismissImmediate()
            }
            else -> {
                SideBubbleOverlayWindow.dismissImmediate()
                MessageCardOverlayWindow.dismissImmediate()
                FloatIconOverlayWindow.dismissImmediate()
            }
        }

        val danmakuTheme = plan.danmakuTheme
        if (plan.showDanmaku && danmakuTheme != null) {
            DanmakuOverlayWindow.show(
                context = context,
                data = plan.data,
                theme = danmakuTheme,
                opacity = plan.settings.danmakuOpacity,
                maxLines = plan.settings.danmakuMaxLines,
            )
        }
        when (plan.primaryStyle) {
            MessageStyle.DarkCard -> {
                if (plan.cardTheme != null) {
                    MessageCardOverlayWindow.show(
                        context = context,
                        plan = plan,
                        onAction = { action -> onAction(context, plan, action) },
                        onDismiss = { dismissPlan(plan) },
                    )
                }
            }
            MessageStyle.SideBubble -> {
                if (plan.cardTheme != null) {
                    SideBubbleOverlayWindow.show(
                        context = context,
                        plan = plan,
                        onAction = { action -> onAction(context, plan, action) },
                        onDismiss = { dismissPlan(plan) },
                    )
                }
            }
            MessageStyle.FloatIcon -> {
                FloatIconOverlayWindow.show(
                    context = context,
                    plan = plan,
                    onAction = { action -> onAction(context, plan, action) },
                    onDismiss = { dismissPlan(plan) },
                )
            }
            else -> Unit
        }
        if (plan.showDanmaku) {
            DanmakuOverlayWindow.bringToFront()
        }
    }

    fun onConfigurationChanged(context: Context, newConfig: Configuration) {
        if (newConfig.orientation != Configuration.ORIENTATION_PORTRAIT) return
        val deps = runCatching { AppEntryPoints.dependencies(context) }.getOrNull() ?: return
        val settings = deps.settingsRepository.readSnapshot().messageReminderSettings
        if (!settings.danmakuEnabled || settings.portraitDanmaku) return
        mainHandler.post { DanmakuOverlayWindow.detach() }
    }

    fun applyDnd(packageName: String, durationMs: Long) {
        dndUntilByPackage[packageName] = System.currentTimeMillis() + durationMs
    }

    fun cancelNotification(key: String): Boolean {
        val listener = MediaNotificationListener.instance ?: return false
        return runCatching {
            listener.cancelNotification(key)
            true
        }.getOrDefault(false)
    }
}

object MessageActionExecutor {
    private const val DND_DURATION_MS = 5 * 60 * 1000L

    fun execute(context: Context, data: NotificationData, action: MessageAction) {
        when (action) {
            MessageAction.Read -> openNotification(context, data)
            MessageAction.ReadInSmallWindow -> openNotificationInSmallWindow(context, data)
            MessageAction.Ignore -> Unit
            MessageAction.IgnoreAndRemove -> {
                MessageReminderController.cancelNotification(data.key)
            }
            MessageAction.Dnd5Min -> {
                MessageReminderController.applyDnd(data.packageName, DND_DURATION_MS)
            }
        }
    }

    private fun openNotification(context: Context, data: NotificationData) {
        val opened = NotificationIntentLauncher.open(context, data)
        if (!opened) {
            Log.w(TAG, "Failed to open notification for ${data.packageName}")
        }
    }

    private fun openNotificationInSmallWindow(context: Context, data: NotificationData) {
        val opened = NotificationIntentLauncher.openInSmallWindow(context, data)
        if (!opened) {
            Log.w(TAG, "Failed to open notification in small window for ${data.packageName}")
        }
    }

    private const val TAG = "MessageActionExecutor"
}
