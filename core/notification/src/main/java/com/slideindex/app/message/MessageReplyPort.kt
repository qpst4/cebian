package com.slideindex.app.message

import android.content.Context

interface MessageReplyPort {
    fun showQuickReply(
        context: Context,
        data: NotificationData,
        onSent: () -> Unit,
        onCancelled: () -> Unit,
    )

    fun showQuickReplyUnavailable(
        context: Context,
        data: NotificationData,
    )
}
