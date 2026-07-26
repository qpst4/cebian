package com.slideindex.app.overlay.animation

import com.slideindex.app.overlay.PanelSide

enum class GestureAnimationPosition {
    Left,
    Right,
    Bottom,
    Top,
    ;

    companion object {
        fun fromPanelSide(side: PanelSide): GestureAnimationPosition = when (side) {
            PanelSide.LEFT -> Left
            PanelSide.RIGHT -> Right
            PanelSide.BOTTOM -> Bottom
            PanelSide.TOP -> Top
        }
    }
}
