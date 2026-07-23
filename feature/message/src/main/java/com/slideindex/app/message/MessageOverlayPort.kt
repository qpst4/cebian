package com.slideindex.app.message

import android.content.Context
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.NotificationData

/** Hosts message reminder overlay windows; implemented in :app. */
interface MessageOverlayPort {
    fun containsNotification(style: MessageStyle, data: NotificationData): Boolean

    fun dismissEntry(style: MessageStyle, key: String, postTime: Long)

    fun resumeAutoDismiss(style: MessageStyle, key: String, postTime: Long)

    fun pauseAutoDismiss(style: MessageStyle, key: String, postTime: Long)

    fun dismissImmediate(style: MessageStyle?)

    fun showPlan(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
    )

    fun detachDanmaku()
}
