package com.slideindex.app.overlay

enum class PanelSide {
    LEFT,
    RIGHT,
    ;

    fun opposite(): PanelSide = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
    }
}
