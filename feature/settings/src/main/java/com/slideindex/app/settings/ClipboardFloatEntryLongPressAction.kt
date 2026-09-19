package com.slideindex.app.settings

enum class ClipboardFloatEntryLongPressAction(val storageValue: String) {
    WORD_TAP("word_tap"),
    DRAG_DROP("drag_drop"),
    NONE("none"),
    ;

    companion object {
        fun fromStorage(value: String?): ClipboardFloatEntryLongPressAction =
            entries.firstOrNull { it.storageValue == value } ?: WORD_TAP
    }
}
