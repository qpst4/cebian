package com.slideindex.app.util

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap

fun Drawable.toSafeImageBitmap(sizePx: Int = 96): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap.asImageBitmap()
    }
    val safeSize = sizePx.coerceIn(1, 256)
    val bitmap = createBitmap(safeSize, safeSize)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, safeSize, safeSize)
    draw(canvas)
    return bitmap.asImageBitmap()
}
