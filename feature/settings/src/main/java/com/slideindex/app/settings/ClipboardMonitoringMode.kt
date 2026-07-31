package com.slideindex.app.settings

enum class ClipboardMonitoringMode(val storageValue: String) {
    SHIZUKU_LOGS("shizuku_logs"),
    SHIZUKU_HIDDEN_API("shizuku_hidden_api"),
    ROOT_LOGS("root_logs"),
    ROOT_HIDDEN_API("root_hidden_api"),
    ;

    val usesRoot: Boolean
        get() = this == ROOT_LOGS || this == ROOT_HIDDEN_API

    val usesHiddenApi: Boolean
        get() = this == SHIZUKU_HIDDEN_API || this == ROOT_HIDDEN_API

    companion object {
        fun fromStorage(value: String?): ClipboardMonitoringMode {
            entries.firstOrNull { it.storageValue == value }?.let { return it }
            return when (value) {
                "logcat" -> SHIZUKU_LOGS
                "lsposed" -> SHIZUKU_HIDDEN_API
                else -> SHIZUKU_LOGS
            }
        }
    }
}
