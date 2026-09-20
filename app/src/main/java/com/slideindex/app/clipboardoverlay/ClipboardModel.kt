package com.slideindex.app.clipboardoverlay

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.view.textclassifier.TextLinks
import androidx.core.net.toUri
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPayload
import com.slideindex.app.clipboard.hasImageContent
import java.io.IOException

data class ClipboardModel(
    val clipData: ClipData,
    val type: Type,
    val text: CharSequence?,
    val textLinks: TextLinks?,
    val uri: Uri?,
    val isSensitive: Boolean,
) {
    enum class Type { TEXT, IMAGE, URI, OTHER }

    private var bitmap: Bitmap? = null

    fun dataMatches(other: ClipboardModel?): Boolean {
        if (other == null) return false
        return type == other.type &&
            text == other.text &&
            uri == other.uri &&
            isSensitive == other.isSensitive
    }

    fun loadThumbnail(context: Context, previewSizePx: Int): Bitmap? {
        if (bitmap == null && type == Type.IMAGE && uri != null) {
            try {
                val size = previewSizePx.coerceAtLeast(1)
                bitmap = context.contentResolver.loadThumbnail(uri, Size(size, size * 4), null)
            } catch (e: IOException) {
                Log.e(TAG, "Thumbnail loading failed!", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Thumbnail loading failed!", e)
            }
        }
        return bitmap
    }

    companion object {
        private const val TAG = "ClipboardModel"

        fun fromPayload(context: Context, payload: ClipboardPayload): ClipboardModel {
            val clipData = payload.toClipData(context)
            val item = clipData.getItemAt(0)
            val mime = payload.mimeType ?: clipData.description.getMimeType(0).orEmpty()
            return ClipboardModel(
                clipData = clipData,
                type = getType(payload, item, mime),
                text = item.text ?: payload.text,
                textLinks = item.textLinks,
                uri = item.uri ?: payload.firstContentUri(),
                isSensitive = false,
            )
        }

        private fun getType(payload: ClipboardPayload, item: ClipData.Item, mimeType: String): Type {
            return when {
                payload.hasImageContent() || mimeType.startsWith("image") -> Type.IMAGE
                !TextUtils.isEmpty(item.text) || payload.text.isNotBlank() -> Type.TEXT
                item.uri != null -> Type.URI
                else -> Type.OTHER
            }
        }
    }
}

internal fun ClipboardPayload.toClipData(context: Context): ClipData {
    val imageUri = firstContentUri()
    return if (hasImageContent() && imageUri != null) {
        val mime = mimeType?.takeIf { it.isNotBlank() } ?: "image/*"
        ClipData("clipboard", arrayOf(mime), ClipData.Item(imageUri))
    } else {
        ClipData.newPlainText("clipboard", text.ifBlank { context.getString(R.string.clipboard_overlay_text_copied) })
    }
}

internal fun ClipboardPayload.firstContentUri(): Uri? {
    val raw = resolvedImageUris().firstOrNull() ?: uri
    return raw?.takeIf { it.isNotBlank() }?.toUri()
}
