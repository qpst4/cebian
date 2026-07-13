package com.slideindex.app.message

import android.app.Notification
import com.slideindex.app.util.BundleParcelCompat

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
        val messages = BundleParcelCompat.getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)
            ?: BundleParcelCompat.getParcelableArrayOfBundles(extras, "android.messages")
            ?: return ""
        return messages.mapNotNull { message ->
            message.getCharSequence("text")?.toString()
        }.joinToString("\n")
    }
}
