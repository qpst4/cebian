package com.slideindex.app.message

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Log
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import com.slideindex.app.util.TaskManagerUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationShortcutIconPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationShortcutIconPort {
    private val cache = object : LruCache<String, Bitmap>(96) {}

    override fun loadShortcutIcon(packageName: String, shortcutId: String, userId: Int): Bitmap? {
        if (packageName.isBlank() || shortcutId.isBlank()) return null
        val cacheKey = "$userId|$packageName|$shortcutId"
        cache.get(cacheKey)?.let { return it }
        if (!TaskManagerUtil.hasShizukuPermission()) {
            Log.w(TAG, "skip shortcut icon: no Shizuku permission")
            return null
        }
        val service = ShizukuUserServiceHost.ensure(context, minApi = ShizukuUserServiceHost.SERVICE_BUILD)
            ?: ShizukuUserServiceHost.ensure(context, minApi = 0)
            ?: run {
                Log.w(TAG, "skip shortcut icon: Shizuku service unavailable")
                return null
            }
        val bytes = runCatching {
            service.getShortcutIconBytes(packageName, shortcutId, userId)
        }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            Log.w(TAG, "shortcut icon bytes empty: $packageName/$shortcutId")
            return null
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
            Log.w(TAG, "shortcut icon decode failed: $packageName/$shortcutId")
            return null
        }
        cache.put(cacheKey, bitmap)
        Log.d(TAG, "shortcut icon loaded: $packageName/$shortcutId ${bitmap.width}x${bitmap.height}")
        return bitmap
    }

    companion object {
        private const val TAG = "AppNotificationShortcutIconPort"
    }
}
