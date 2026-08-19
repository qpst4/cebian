package com.slideindex.app.settings

enum class ClipboardFloatListStyle(val id: Int) {
    CARD(0),
    SINGLE_LINE(1);

    companion object {
        fun fromId(id: Int): ClipboardFloatListStyle = entries.firstOrNull { it.id == id } ?: SINGLE_LINE
    }
}
