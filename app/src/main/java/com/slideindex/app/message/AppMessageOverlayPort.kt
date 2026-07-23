package com.slideindex.app.message

import android.content.Context
import com.slideindex.app.overlay.DanmakuOverlayWindow
import com.slideindex.app.overlay.FloatIconOverlayWindow
import com.slideindex.app.overlay.SideBubbleOverlayWindow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMessageOverlayPort @Inject constructor() : MessageOverlayPort {
    override fun containsNotification(style: MessageStyle, data: NotificationData): Boolean =
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.containsNotification(data)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.containsNotification(data)
            else -> false
        }

    override fun dismissEntry(style: MessageStyle, key: String, postTime: Long) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissEntry(key, postTime)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissEntry(key, postTime)
            else -> Unit
        }
    }

    override fun dismissEntriesForKey(style: MessageStyle, key: String) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissEntriesForKey(key)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissEntriesForKey(key)
            else -> Unit
        }
    }

    override fun resumeAutoDismiss(style: MessageStyle, key: String, postTime: Long) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.resumeAutoDismiss(key, postTime)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.resumeAutoDismiss(key, postTime)
            else -> Unit
        }
    }

    override fun pauseAutoDismiss(style: MessageStyle, key: String, postTime: Long) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.pauseAutoDismiss(key, postTime)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.pauseAutoDismiss(key, postTime)
            else -> Unit
        }
    }

    override fun dismissImmediate(style: MessageStyle?) {
        when (style) {
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissImmediate()
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissImmediate()
            null -> {
                SideBubbleOverlayWindow.dismissImmediate()
                FloatIconOverlayWindow.dismissImmediate()
            }
            else -> Unit
        }
    }

    override fun snapshotDisplayedKeys(): Set<String> =
        buildSet {
            addAll(SideBubbleOverlayWindow.snapshotDisplayedKeys())
            addAll(FloatIconOverlayWindow.snapshotDisplayedKeys())
        }

    override fun dismissAllReminders() {
        SideBubbleOverlayWindow.dismiss()
        FloatIconOverlayWindow.dismiss()
        DanmakuOverlayWindow.detach()
    }

    override fun dismissSameSourceReminders(sourceKey: String) {
        SideBubbleOverlayWindow.dismissSameSource(sourceKey)
        FloatIconOverlayWindow.dismissSameSource(sourceKey)
    }

    override fun snapshotDisplayedKeysForSource(sourceKey: String): Set<String> =
        buildSet {
            addAll(SideBubbleOverlayWindow.snapshotDisplayedKeysForSource(sourceKey))
            addAll(FloatIconOverlayWindow.snapshotDisplayedKeysForSource(sourceKey))
        }

    override fun showPlan(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val danmakuTheme = plan.danmakuTheme
        if (plan.showDanmaku && danmakuTheme != null) {
            DanmakuOverlayWindow.show(
                context = context,
                data = plan.data,
                theme = danmakuTheme,
                opacity = plan.settings.danmakuOpacity,
                maxLines = plan.settings.danmakuMaxLines,
                speedLevel = plan.settings.danmakuSpeedLevel,
                fontSizeLevel = plan.settings.sideBubbleFontSizeLevel,
            )
        }
        if (plan.showFloatIcon) {
            FloatIconOverlayWindow.show(
                context = context,
                plan = plan,
                onAction = onAction,
                onDismiss = onDismiss,
            )
        }
        if (plan.showSideBubble && plan.sideTheme != null) {
            SideBubbleOverlayWindow.show(
                context = context,
                plan = plan,
                onAction = onAction,
                onDismiss = onDismiss,
            )
        }
        if (plan.showDanmaku) {
            DanmakuOverlayWindow.bringToFront()
        }
    }

    override fun detachDanmaku() {
        DanmakuOverlayWindow.detach()
    }
}
