package com.slideindex.app.shizuku

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream

internal object ShortcutIconBitmapLoader {
    private const val TAG = "ShortcutIconBitmapLoader"
    private const val ICON_SIZE_PX = 144

    fun loadPngBytes(
        packageName: String,
        shortcutId: String,
        userId: Int,
    ): ByteArray? {
        if (packageName.isBlank() || shortcutId.isBlank()) return null
        val bytes = ShortcutSystemApiFetcher.readShortcutIconBytes(packageName, shortcutId, userId)
            ?: run {
                Log.w(TAG, "shortcut icon load failed: $packageName/$shortcutId user=$userId")
                return null
            }
        return normalizePng(bytes)
    }

    private fun normalizePng(bytes: ByteArray): ByteArray? {
        if (bytes.isEmpty()) return null
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        return bitmapToPng(scaleBitmap(bitmap))
    }

    private fun scaleBitmap(source: Bitmap): Bitmap {
        if (source.width == ICON_SIZE_PX && source.height == ICON_SIZE_PX) return source
        return source.scale(ICON_SIZE_PX, ICON_SIZE_PX)
    }

    private fun bitmapToPng(bitmap: Bitmap): ByteArray? =
        ByteArrayOutputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) null else stream.toByteArray()
        }
}
