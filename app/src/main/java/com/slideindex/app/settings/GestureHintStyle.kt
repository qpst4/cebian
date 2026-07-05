package com.slideindex.app.settings

enum class GestureHintStyle(val id: Int) {
    WAVE(0),
    CAPSULE(1),
    BUBBLE(2),
    ;

    companion object {
        fun fromId(id: Int): GestureHintStyle =
            entries.firstOrNull { it.id == id } ?: BUBBLE
    }
}

fun AppSettings.gestureHintStyle(): GestureHintStyle = GestureHintStyle.fromId(gestureHintStyleId)
