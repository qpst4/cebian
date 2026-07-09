package com.slideindex.app.settings

enum class HapticStrength(val level: Int) {
    LIGHT(0),
    MEDIUM(1),
    STRONG(2),
    ;

    companion object {
        fun fromLevel(level: Int): HapticStrength =
            entries.firstOrNull { it.level == level } ?: MEDIUM
    }
}
