package com.slideindex.app.message

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification

data class NotificationData(
    val packageName: String,
    val key: String,
    val title: String,
    val content: String,
    val largeIcon: Bitmap?,
    val appIcon: Bitmap?,
    val contentIntent: PendingIntent?,
    val postTime: Long,
) {
    companion object {
        private const val ICON_SIZE_PX = 144
        private const val BADGE_SIZE_PX = 48

        fun fromSbn(context: Context, sbn: StatusBarNotification): NotificationData? {
            val notification = sbn.notification ?: return null
            val extras = notification.extras ?: return null
            val text = NotificationTextExtractor.extract(extras)
            if (text.title.isBlank() && text.text.isBlank()) return null

            val appContext = context.applicationContext
            val appIcon = loadAppIcon(appContext, sbn.packageName)
            val conversationIcon = extractConversationIcon(appContext, notification, extras)
            val largeIcon = conversationIcon ?: appIcon

            return NotificationData(
                packageName = sbn.packageName,
                key = sbn.key,
                title = text.title,
                content = text.text,
                largeIcon = largeIcon,
                appIcon = if (conversationIcon != null) appIcon else null,
                contentIntent = notification.contentIntent,
                postTime = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        }

        private fun extractConversationIcon(
            context: Context,
            notification: Notification,
            extras: Bundle,
        ): Bitmap? {
            loadNotificationIcon(context, notification.getLargeIcon())?.let { return it }
            extractBitmapFromExtras(context, extras)?.let { return it }
            extractMessagingStyleIcon(context, notification, extras)?.let { return it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                extractConversationIconExtra(context, extras)?.let { return it }
            }
            legacyLargeIcon(notification)?.let { bitmap ->
                if (!bitmap.isBlank()) return scaleIcon(bitmap)
            }
            extractAnyIconFromExtras(context, extras)?.let { return it }
            return null
        }

        private fun extractAnyIconFromExtras(context: Context, extras: Bundle): Bitmap? {
            for (key in extras.keySet()) {
                if (key.contains("picture", ignoreCase = true)) continue
                when (val value = extras.get(key)) {
                    is Bitmap -> if (!value.isBlank()) return scaleIcon(value)
                    is Icon -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        loadNotificationIcon(context, value)?.let { return it }
                    }
                }
            }
            return null
        }

        private fun extractBitmapFromExtras(context: Context, extras: Bundle): Bitmap? {
            readBitmapExtra(extras, Notification.EXTRA_LARGE_ICON)?.let { return scaleIcon(it) }
            readBitmapExtra(extras, Notification.EXTRA_LARGE_ICON_BIG)?.let { return scaleIcon(it) }
            readBitmapExtra(extras, "android.largeIcon")?.let { return scaleIcon(it) }
            readBitmapExtra(extras, "android.largeIcon.big")?.let { return scaleIcon(it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                loadNotificationIcon(context, readIconExtra(extras, Notification.EXTRA_LARGE_ICON))
                    ?.let { return it }
                loadNotificationIcon(context, readIconExtra(extras, Notification.EXTRA_LARGE_ICON_BIG))
                    ?.let { return it }
                loadNotificationIcon(context, readIconExtra(extras, "android.largeIcon"))
                    ?.let { return it }
            }
            return null
        }

        private fun extractMessagingStyleIcon(
            context: Context,
            notification: Notification,
            extras: Bundle,
        ): Bitmap? {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
            for (index in messages.indices.reversed()) {
                val message = messages[index] as? Bundle ?: continue
                extractPersonIcon(context, message)?.let { return it }
            }
            return null
        }

        private fun extractPersonIcon(context: Context, message: Bundle): Bitmap? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                readPersonExtra(message, "sender_person")
                    ?.let { person -> loadNotificationIcon(context, person.icon)?.let { return it } }
                readPersonExtra(message, Notification.EXTRA_MESSAGING_PERSON)
                    ?.let { person -> loadNotificationIcon(context, person.icon)?.let { return it } }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                loadNotificationIcon(context, readIconExtra(message, "sender_icon"))?.let { return it }
            }
            readBitmapExtra(message, "sender_avatar")?.let { return scaleIcon(it) }
            return null
        }

        private fun extractConversationIconExtra(context: Context, extras: Bundle): Bitmap? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            return loadNotificationIcon(context, readIconExtra(extras, "android.conversationIcon"))
        }

        private fun readBitmapExtra(bundle: Bundle, key: String): Bitmap? {
            val value = bundle.get(key) ?: return null
            return when (value) {
                is Bitmap -> value
                else -> null
            }
        }

        private fun readIconExtra(bundle: Bundle, key: String): Icon? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(key, Icon::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable(key)
            }
        }

        private fun readPersonExtra(bundle: Bundle, key: String): Person? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(key, Person::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable(key)
            }
        }

        private fun loadNotificationIcon(context: Context, icon: Icon?): Bitmap? {
            if (icon == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
            val drawable = runCatching { icon.loadDrawable(context) }.getOrNull() ?: return null
            val bitmap = drawableToBitmap(drawable)
            return if (bitmap.isBlank()) null else scaleIcon(bitmap)
        }

        @Suppress("DEPRECATION")
        private fun legacyLargeIcon(notification: Notification): Bitmap? =
            notification.largeIcon as? Bitmap

        private fun loadAppIcon(context: Context, packageName: String): Bitmap? {
            return runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                scaleIcon(drawableToBitmap(drawable), BADGE_SIZE_PX)
            }.getOrNull()
        }

        private fun scaleIcon(source: Bitmap, sizePx: Int = ICON_SIZE_PX): Bitmap {
            if (source.width == sizePx && source.height == sizePx) return source
            return Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
        }

        private fun drawableToBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val width = drawable.intrinsicWidth.coerceIn(1, ICON_SIZE_PX)
            val height = drawable.intrinsicHeight.coerceIn(1, ICON_SIZE_PX)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
            }
        }

        private fun Bitmap.isBlank(): Boolean {
            if (width <= 1 || height <= 1) return true
            val sample = getPixel(width / 2, height / 2)
            val alpha = sample ushr 24
            return alpha < 16
        }
    }
}
