package com.slideindex.app.imageeditor

import android.content.Context
import android.util.TypedValue

internal object EditorViewMetrics {
    const val CROP_MASK_ALPHA = 150
    const val SURFACE_CONTAINER_ALPHA = 220
    const val BLUE_RGB_G = 150

    @JvmStatic
    fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        )
    }
}
