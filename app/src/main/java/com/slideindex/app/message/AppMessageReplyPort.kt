package com.slideindex.app.message

import android.content.Context
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.overlay.MessageReplyOverlayWindow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMessageReplyPort @Inject constructor() : MessageReplyPort {
    override fun showQuickReply(
        context: Context,
        data: NotificationData,
        onSent: () -> Unit,
        onCancelled: () -> Unit,
    ) {
        MessageReplyOverlayWindow.show(context, data, onSent, onCancelled)
    }

    override fun showQuickReplyUnavailable(
        context: Context,
        data: NotificationData,
    ) {
        val hostContext = context.applicationContext
        Toast.makeText(
            hostContext,
            hostContext.getString(R.string.message_action_quick_reply_unavailable, data.title.ifBlank { data.packageName }),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
