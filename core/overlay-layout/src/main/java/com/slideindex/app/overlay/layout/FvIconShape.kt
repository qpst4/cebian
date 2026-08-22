package com.slideindex.app.overlay.layout

/**
 * 圆环启动器图标裁切形状。
 */
enum class FvIconShape(val cornerRadiusRatio: Float) {
    /** 经典圆角矩形（默认，圆角 22%）。 */
    ROUNDED_RECT(0.22f),

    /** 圆形（圆角 50%）。 */
    CIRCLE(0.50f),

    /** 方圆 / 超椭圆（圆角 35%）。 */
    SQUIRCLE(0.35f),

    /** 微圆角方形（圆角 8%）。 */
    SQUARE(0.08f);

    companion object {
        fun fromName(name: String?): FvIconShape =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ROUNDED_RECT
    }
}
