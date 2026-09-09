package com.slideindex.app.overlay

import androidx.compose.animation.core.Easing

internal data class OverlayScreenBounds(
    val width: Float,
    val height: Float
)

internal const val FLOATING_POINTER_PRESENCE_ANIMATION_MS = 280L
internal const val GESTURE_RECORDER_POINTER_ANIMATION_MS = 400
/** QC default `gestureRecorderColor` (#b2fd746c). */
internal const val DefaultGestureRecorderColorArgb = 0xB2FD746C.toInt()
internal const val FLOATING_POINTER_RADIAL_MENU_ANIMATION_MS = 220L
internal const val FLOATING_POINTER_EDGE_PREVIEW_ANIMATION_MS = 300L

/** Matches QC DecelerateInterpolator used for the click ring shrink. */
internal val QcPointerClickEasing = Easing { fraction ->
    val t = fraction.coerceIn(0f, 1f)
    1f - (1f - t) * (1f - t)
}
