package com.slideindex.app.util

data class SystemShortcutEntry(
    val id: String,
    val label: String,
    val kinds: Set<ShortcutKind>,
    val targetComponent: String? = null,
)
