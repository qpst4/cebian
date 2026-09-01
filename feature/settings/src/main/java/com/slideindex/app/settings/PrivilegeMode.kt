package com.slideindex.app.settings

enum class PrivilegeMode(val storageValue: String) {
    SHIZUKU("shizuku"),
    ROOT("root"),
    ;

    companion object {
        fun fromStorage(value: String?): PrivilegeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SHIZUKU
        }
    }
}
