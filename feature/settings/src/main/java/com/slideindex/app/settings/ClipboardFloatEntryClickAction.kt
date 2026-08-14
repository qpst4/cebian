package com.slideindex.app.settings

enum class ClipboardFloatEntryClickAction(val storageValue: String) {
    PASTE("paste"),
    COPY("copy"),
    COPY_AND_PASTE("copy_and_paste"),
    ;

    companion object {
        fun fromStorage(value: String?): ClipboardFloatEntryClickAction =
            entries.firstOrNull { it.storageValue == value } ?: PASTE
    }
}
