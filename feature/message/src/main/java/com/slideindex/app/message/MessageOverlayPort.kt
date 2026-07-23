package com.slideindex.app.message

import android.content.Context
import com.slideindex.app.message.MessageDisplayPlan
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.NotificationData

/** Hosts message reminder overlay windows; implemented in :app. */
interface MessageOverlayPort {
    fun containsNotification(style: MessageStyle, data: NotificationData): Boolean

    fun dismissEntry(style: MessageStyle, key: String, postTime: Long)

    fun dismissEntriesForKey(style: MessageStyle, key: String)

    fun resumeAutoDismiss(style: MessageStyle, key: String, postTime: Long)

    fun pauseAutoDismiss(style: MessageStyle, key: String, postTime: Long)

    fun dismissImmediate(style: MessageStyle?)

    fun snapshotDisplayedKeys(): Set<String>

    fun dismissAllReminders()

    fun dismissSameSourceReminders(sourceKey: String)

    fun snapshotDisplayedKeysForSource(sourceKey: String): Set<String>

    fun showPlan(
        context: Context,
        plan: MessageDisplayPlan,
        onAction: (MessageAction) -> Unit,
        onDismiss: () -> Unit,
    )

    fun detachDanmaku()
}
