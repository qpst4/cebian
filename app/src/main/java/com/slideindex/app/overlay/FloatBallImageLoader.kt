package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.core.net.toUri

internal object FloatBallImageLoader {
    fun loadBitmap(context: Context, uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        return runCatching {
            val uri = uriString.toUri()
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrNull()
    }
}