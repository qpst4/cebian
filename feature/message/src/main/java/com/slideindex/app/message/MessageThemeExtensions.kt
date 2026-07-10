package com.slideindex.app.message

import kotlin.math.roundToInt

fun MessageThemeSpec.effectiveSideBackgroundResId(): Int =
    sideRightResId.takeIf { it != 0 } ?: backgroundResId

fun MessageThemeSpec.overlayAlpha(opacity: Float): Float {
    val themeAlpha = backgroundAlpha.coerceIn(0, 255) / 255f
    return opacity.coerceIn(0.2f, 1f) * themeAlpha
}

fun MessageThemeSpec.overlayAlphaInt(opacity: Float): Int =
    (overlayAlpha(opacity) * 255f).roundToInt().coerceIn(0, 255)
