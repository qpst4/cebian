package com.slideindex.app.settings

object FloatBallPositionFractions {
    const val MIN_VISIBLE = 0.5f
    /** 键盘「收窄」时允许露出比例低于 [MIN_VISIBLE]（与变窄比例滑条一致）。 */
    const val MIN_KEYBOARD_NARROW_VISIBLE = 0.001f
    const val MAX_VISIBLE = 1f
    const val MIN_Y = 0f
    const val MAX_Y = 1f
    /** Legacy CUSTOM-mode ball center X as fraction of screen width. */
    const val MIN_CUSTOM_CENTER_X = 0f
    const val MAX_CUSTOM_CENTER_X = 1f

    fun coerceVisible(fraction: Float): Float = fraction.coerceIn(MIN_VISIBLE, MAX_VISIBLE)

    fun coerceKeyboardNarrowVisible(fraction: Float): Float =
        fraction.coerceIn(MIN_KEYBOARD_NARROW_VISIBLE, MAX_VISIBLE)

    fun coerceCustomCenterX(fraction: Float): Float =
        fraction.coerceIn(MIN_CUSTOM_CENTER_X, MAX_CUSTOM_CENTER_X)

    fun coerceY(fraction: Float): Float = fraction.coerceIn(MIN_Y, MAX_Y)
}
