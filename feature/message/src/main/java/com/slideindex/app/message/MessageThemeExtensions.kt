package com.slideindex.app.message

import kotlin.math.roundToInt

fun MessageThemeSpec.effectiveSideBackgroundResId(): Int =
    sideRightResId.takeIf { it != 0 } ?: backgroundResId

/** 弹幕沿用侧边气泡皮肤，仅容器与动画不同（对齐微泡泡 float_item_id）。 */
fun MessageThemeSpec.toDanmakuPresentation(): MessageThemeSpec {
    if (style == MessageStyle.Danmaku) return this
    val bubbleBackground = effectiveSideBackgroundResId()
    return copy(
        style = MessageStyle.Danmaku,
        backgroundResId = bubbleBackground,
        sideLeftResId = 0,
        sideRightResId = 0,
        cornerRadiusDp = cornerRadiusDp.takeIf { it > 0f } ?: 10f,
    )
}

fun MessageThemeSpec.danmakuBackgroundResId(): Int = effectiveSideBackgroundResId()

fun MessageThemeSpec.overlayAlpha(opacity: Float): Float {
    val themeAlpha = backgroundAlpha.coerceIn(0, 255) / 255f
    return opacity.coerceIn(0.2f, 1f) * themeAlpha
}

fun MessageThemeSpec.overlayAlphaInt(opacity: Float): Int =
    (overlayAlpha(opacity) * 255f).roundToInt().coerceIn(0, 255)
