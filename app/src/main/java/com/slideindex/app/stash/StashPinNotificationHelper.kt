package com.slideindex.app.stash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.slideindex.app.R
import com.slideindex.app.overlay.ScreenPinManager
import com.slideindex.app.receiver.PinNotificationRestoreReceiver
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.json.Json

internal object StashPinNotificationHelper {
    private const val CHANNEL_ID = "stash_pin_image"
    private const val NOTIFICATION_ID = 23001
    private const val RESTORE_REQUEST_CODE = 23002
    private const val CACHE_DIR_NAME = "pin_notification"
    private const val SNAPSHOT_FILE_NAME = "snapshot.json"
    private const val IMAGE_FILE_NAME = "pin_image.png"

    const val ACTION_RESTORE_PIN = "com.slideindex.app.action.RESTORE_PIN_FROM_NOTIFICATION"

    private val json = Json { ignoreUnknownKeys = true }

    fun postPinSnapshot(
        context: Context,
        snapshot: PinNotificationSnapshot,
        displayBitmap: Bitmap,
        title: String,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        saveSnapshot(context, snapshot, displayBitmap)
        ensureChannel(context)
        val restoreIntent = Intent(context, PinNotificationRestoreReceiver::class.java).apply {
            action = ACTION_RESTORE_PIN
        }
        val contentIntent = PendingIntent.getBroadcast(
            context,
            RESTORE_REQUEST_CODE,
            restoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.stash_pin_notification_text))
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(displayBitmap))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun restoreToScreenPin(context: Context) {
        val snapshot = loadSnapshot(context) ?: return
        val imageBitmap = when (snapshot.type) {
            PinNotificationSnapshot.TYPE_IMAGE -> {
                pinImageFile(context)?.let { BitmapFactory.decodeFile(it.absolutePath) } ?: return
            }
            else -> null
        }
        ScreenPinManager.restoreFromNotification(context, snapshot, imageBitmap) { success ->
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            if (success) {
                clearSnapshotFiles(context)
                Toast.makeText(context, R.string.stash_pin_notification_restored, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.stash_pin_notification_restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        clearSnapshotFiles(context)
    }

    private fun saveSnapshot(context: Context, snapshot: PinNotificationSnapshot, displayBitmap: Bitmap) {
        val dir = cacheDir(context)
        runCatching {
            FileOutputStream(File(dir, IMAGE_FILE_NAME)).use { output ->
                displayBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            File(dir, SNAPSHOT_FILE_NAME).writeText(
                json.encodeToString(PinNotificationSnapshot.serializer(), snapshot),
            )
        }
    }

    private fun loadSnapshot(context: Context): PinNotificationSnapshot? {
        val file = File(cacheDir(context), SNAPSHOT_FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(PinNotificationSnapshot.serializer(), file.readText())
        }.getOrNull()
    }

    private fun clearSnapshotFiles(context: Context) {
        val dir = cacheDir(context)
        File(dir, SNAPSHOT_FILE_NAME).delete()
        File(dir, IMAGE_FILE_NAME).delete()
    }

    private fun cacheDir(context: Context): File {
        return File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    }

    private fun pinImageFile(context: Context): File? {
        val file = File(cacheDir(context), IMAGE_FILE_NAME)
        return file.takeIf { it.exists() }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.stash_pin_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.stash_pin_notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}

internal fun savePinShareImage(context: Context, bitmap: Bitmap): File? {
    val dir = File(context.cacheDir, "pin_share").apply { mkdirs() }
    val file = File(dir, "pin_${System.currentTimeMillis()}.png")
    return runCatching {
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        file
    }.getOrNull()
}
