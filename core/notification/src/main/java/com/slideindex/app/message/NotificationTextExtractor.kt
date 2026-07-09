package com.slideindex.app.message

import android.app.Notification
import android.os.Bundle

object NotificationTextExtractor {
    data class Content(
        val title: String,
        val text: String,
    )

    fun extract(extras: Bundle): Content {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ?: ""
        if (text.isBlank()) {
            text = extractMessagingStyleText(extras)
        }
        return Content(title = title, text = text)
    }

    private fun extractMessagingStyleText(extras: Bundle): String {
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?: extras.getParcelableArray("android.messages")
            ?: return ""
        return messages.mapNotNull { parcel ->
            when (parcel) {
                is Bundle -> parcel.getCharSequence("text")?.toString()
                else -> null
            }
        }.joinToString("\n")
    }
}
