package com.slideindex.app.activity

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.util.UUID

object ShortcutIconStorage {
    private const val DIR = "shortcut_icons"

    fun saveIconFromUri(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return saveIconFromBytes(context, bytes)
    }

    fun saveIconFromBytes(context: Context, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val fileName = "custom-${UUID.randomUUID()}.png"
        File(dir, fileName).writeBytes(bytes)
        return "$DIR/$fileName"
    }

    fun loadBitmap(context: Context, iconPath: String?, size: Int = 128): Bitmap? {
        val relative = iconPath?.takeIf { it.startsWith("$DIR/") } ?: return null
        val file = File(context.filesDir, relative)
        if (!file.isFile) return null
        return runCatching {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?.let { src ->
                    if (src.width == size && src.height == size) {
                        src
                    } else {
                        Bitmap.createScaledBitmap(src, size, size, true).also {
                            if (it !== src) src.recycle()
                        }
                    }
                }
        }.getOrNull()
    }

    fun deleteIconIfOwned(context: Context, iconPath: String?) {
        val relative = iconPath?.takeIf { it.startsWith("$DIR/") } ?: return
        val file = File(context.filesDir, relative)
        if (file.exists()) {
            runCatching { file.delete() }
        }
    }
}
