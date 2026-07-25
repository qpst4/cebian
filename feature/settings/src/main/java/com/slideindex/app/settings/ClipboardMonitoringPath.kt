package com.slideindex.app.settings

enum class ClipboardMonitoringPath(val storageValue: String) {
    LOGCAT("logcat"),
    LSPOSED("lsposed"),
    ;

    companion object {
        fun fromStorage(value: String?): ClipboardMonitoringPath =
            entries.firstOrNull { it.storageValue == value } ?: LOGCAT
    }
}
