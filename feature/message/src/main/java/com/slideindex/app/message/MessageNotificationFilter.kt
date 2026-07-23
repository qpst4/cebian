package com.slideindex.app.message

import android.content.Context
import android.content.res.Configuration
import android.service.notification.StatusBarNotification
import com.slideindex.app.notification.NotificationSbnCache

internal object MessageNotificationFilter {
    private const val DEDUP_WINDOW_MS = 15_000L

    private val recentPostKeys = LinkedHashMap<String, Long>()
    private val recentContentKeys = LinkedHashMap<String, Long>()
    private val dndUntilByPackage = mutableMapOf<String, Long>()
    private val dedupLock = Any()

    fun shouldShowNotification(
        context: Context,
        settings: MessageSettings,
        sbn: StatusBarNotification,
        data: NotificationData,
        environmentPort: MessageEnvironmentPort,
        foregroundPort: MessageForegroundPort,
    ): Boolean {
        if (sbn.packageName == context.packageName) return false
        if (!settings.isPackageAllowed(data.packageName)) return false
        if (!settings.passesAppFilter(data)) return false

        if (settings.suppressWhenSystemDnd && environmentPort.isSystemDndEnabled(context)) {
            return false
        }

        val foregroundPackage = foregroundPort.foregroundPackage()
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

    fun dedup(data: NotificationData): Boolean {
        val postKey = "${data.key}|${data.postTime}"
        val contentKey = contentSignature(data)
        val now = System.currentTimeMillis()
        synchronized(dedupLock) {
            recentPostKeys.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
            recentContentKeys.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
            if (recentPostKeys.containsKey(postKey)) return false
            if (recentContentKeys.containsKey(contentKey)) return false
            recentPostKeys[postKey] = now
            recentContentKeys[contentKey] = now
        }
        return true
    }

    fun applyDnd(packageName: String, durationMs: Long) {
        dndUntilByPackage[packageName] = System.currentTimeMillis() + durationMs
    }

    internal fun resetForTesting() {
        synchronized(dedupLock) {
            recentPostKeys.clear()
            recentContentKeys.clear()
        }
        dndUntilByPackage.clear()
    }

    private fun contentSignature(data: NotificationData): String =
        "${data.key}|${data.title}|${data.content}"
}

internal object MessagePlanBuilder {
    fun buildDisplayPlan(
        context: Context,
        settings: MessageSettings,
        data: NotificationData,
        themePort: MessageThemePort,
    ): MessageDisplayPlan? {
        val isLandscape = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        val blockStackedStyles = isLandscape && settings.hideInLandscape

        val showFloatIcon = settings.floatIconEnabled && !blockStackedStyles
        val showSideBubble = settings.sideBubbleEnabled && !blockStackedStyles
        val showDanmaku = settings.danmakuEnabled &&
            if (isLandscape) settings.landscapeDanmaku else settings.portraitDanmaku

        if (!showFloatIcon && !showSideBubble && !showDanmaku) return null

        return MessageDisplayPlan(
            data = data,
            showFloatIcon = showFloatIcon,
            showSideBubble = showSideBubble,
            showDanmaku = showDanmaku,
            sideTheme = if (showSideBubble) {
                themePort.themeFor(MessageStyle.SideBubble, settings.sideThemeId)
            } else {
                null
            },
            danmakuTheme = if (showDanmaku) {
                themePort.themeFor(MessageStyle.Danmaku, settings.danmakuThemeId)
            } else {
                null
            },
            settings = settings,
        )
    }
}
