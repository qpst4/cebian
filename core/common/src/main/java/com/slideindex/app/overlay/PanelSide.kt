package com.slideindex.app.overlay

enum class PanelSide {
    LEFT,
    RIGHT,
    BOTTOM,
    ;

    fun opposite(): PanelSide = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        BOTTOM -> BOTTOM
    }

    val isHorizontalEdge: Boolean get() = this == LEFT || this == RIGHT
}
