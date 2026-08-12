package com.slideindex.app.shell

enum class ShellCommandIconType {
    URI,
    TEXT,
    OTHER,
    ;

    companion object {
        fun fromStored(value: String?): ShellCommandIconType =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
