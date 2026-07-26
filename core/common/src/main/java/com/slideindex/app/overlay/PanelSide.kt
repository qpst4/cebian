package com.slideindex.app.overlay

enum class PanelSide {
    LEFT,
    RIGHT,
    BOTTOM,
    TOP,
    ;

    fun opposite(): PanelSide = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        BOTTOM -> BOTTOM
        TOP -> TOP
    }

    val isHorizontalEdge: Boolean get() = this == LEFT || this == RIGHT

    val isVerticalEdge: Boolean get() = this == BOTTOM || this == TOP
}
