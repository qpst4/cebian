package com.slideindex.app.message

import android.content.Context
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.NotificationData
import com.slideindex.app.overlay.DanmakuOverlayWindow
import com.slideindex.app.overlay.FloatIconOverlayWindow
import com.slideindex.app.overlay.MessageCardOverlayWindow
import com.slideindex.app.overlay.SideBubbleOverlayWindow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMessageOverlayPort @Inject constructor() : MessageOverlayPort {
    override fun containsNotification(style: MessageStyle, data: NotificationData): Boolean =
        when (style) {
            MessageStyle.DarkCard -> MessageCardOverlayWindow.containsNotification(data)
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.containsNotification(data)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.containsNotification(data)
            else -> false
        }

    override fun dismissEntry(style: MessageStyle, key: String, postTime: Long) {
        when (style) {
            MessageStyle.DarkCard -> MessageCardOverlayWindow.dismissEntry(key, postTime)
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissEntry(key, postTime)
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissEntry(key, postTime)
            else -> Unit
        }
    }

    override fun dismissImmediate(style: MessageStyle?) {
        when (style) {
            MessageStyle.DarkCard -> MessageCardOverlayWindow.dismissImmediate()
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissImmediate()
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissImmediate()
            null -> {
                SideBubbleOverlayWindow.dismissImmediate()
                MessageCardOverlayWindow.dismissImmediate()
                FloatIconOverlayWindow.dismissImmediate()
            }
            else -> Unit
        }
    }

    override fun showPlan(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
    ) {
        when (plan.primaryStyle) {
            MessageStyle.DarkCard -> {
                dismissImmediate(MessageStyle.SideBubble)
                dismissImmediate(MessageStyle.FloatIcon)
            }
            MessageStyle.SideBubble -> {
                dismissImmediate(MessageStyle.DarkCard)
                dismissImmediate(MessageStyle.FloatIcon)
            }
            MessageStyle.FloatIcon -> {
                dismissImmediate(MessageStyle.DarkCard)
                dismissImmediate(MessageStyle.SideBubble)
            }
            else -> dismissImmediate(null)
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
                        onAction = onAction,
                        onDismiss = onDismiss,
                    )
                }
            }
            MessageStyle.SideBubble -> {
                if (plan.cardTheme != null) {
                    SideBubbleOverlayWindow.show(
                        context = context,
                        plan = plan,
                        onAction = onAction,
                        onDismiss = onDismiss,
                    )
                }
            }
            MessageStyle.FloatIcon -> {
                FloatIconOverlayWindow.show(
                    context = context,
                    plan = plan,
                    onAction = onAction,
                    onDismiss = onDismiss,
                )
            }
            else -> Unit
        }
        if (plan.showDanmaku) {
            DanmakuOverlayWindow.bringToFront()
        }
    }

    override fun detachDanmaku() {
        DanmakuOverlayWindow.detach()
    }
}
