package com.slideindex.app.settings

enum class FloatingPointerTrailType(val id: Int) {
    OFF(0),
    SIMPLE(1),
    HIGH_DETAIL(2),
    ;

    companion object {
        fun fromId(id: Int): FloatingPointerTrailType =
            entries.firstOrNull { it.id == id } ?: SIMPLE
    }
}
