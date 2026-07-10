package com.slideindex.app.overlay

import androidx.compose.animation.core.Easing

internal data class OverlayScreenBounds(
    val width: Float,
    val height: Float,
)

internal const val FLOATING_POINTER_PRESENCE_ANIMATION_MS = 280L
internal const val FLOATING_POINTER_RADIAL_MENU_ANIMATION_MS = 220L

/** Matches QC DecelerateInterpolator used for the click ring shrink. */
internal val QcPointerClickEasing = Easing { fraction ->
    val t = fraction.coerceIn(0f, 1f)
    1f - (1f - t) * (1f - t)
}
