package com.slideindex.app.settings

enum class GestureHintStyle(val id: Int) {
    WAVE(0),
    CAPSULE(1),
    BUBBLE(2),
    ANDROID(3),
    ;

    companion object {
        fun fromId(id: Int): GestureHintStyle =
            entries.firstOrNull { it.id == id } ?: BUBBLE
    }
}

/** 系统箭头胶囊配色：跟侧编主题，或跟壁纸动态色。 */
enum class AndroidBackColorSource(val id: Int) {
    APP_THEME(0),
    SYSTEM(1),
    ;

    companion object {
        fun fromId(id: Int): AndroidBackColorSource =
            entries.firstOrNull { it.id == id } ?: APP_THEME
    }
}
