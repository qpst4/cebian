package com.slideindex.app.settings

/** 悬浮球外观样式类型（参考 FooView）。 */
enum class FloatBallStyleType(val storageKey: String) {
    DEFAULT("default"),
    ANIMATED_PLANE("animated_plane"),
    ANIMATED_PULSE("animated_pulse"),
    ANIMATED_ORBIT("animated_orbit"),
    CUSTOM_IMAGE("custom_image"),
    SLIDESHOW("slideshow"),
    GIF("gif"),
    ;

    companion object {
        fun fromStorageKey(key: String?): FloatBallStyleType =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
