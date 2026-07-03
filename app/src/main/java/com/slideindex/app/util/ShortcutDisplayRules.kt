package com.slideindex.app.util

/**
 * Universal rules for whether a shortcut id/label pair is safe to show in the task switcher menu.
 * Applies to every app — not only WeChat / QQ / Alipay.
 */
object ShortcutDisplayRules {

    fun isDisplayable(id: String?, label: String): Boolean {
        val text = label.trim()
        if (text.isEmpty()) return false
        val key = id?.trim().orEmpty()
        if (text.all { it.isDigit() }) return false
        if (key.isNotEmpty() && text == key && !looksHumanReadable(text)) return false
        if (isInternalKey(text) && text == key) return false
        return true
    }

    private fun looksHumanReadable(text: String): Boolean {
        if (text.any { it in '\u4e00'..'\u9fff' || it.isWhitespace() }) return true
        if (text.length >= 3 && (text.contains('_') || text.contains('-'))) return true
        return false
    }

    fun isInternalKey(key: String): Boolean {
        val lower = key.lowercase()
        if (lower.startsWith("shortcut_id_")) return true
        if (lower.startsWith("shortcut_") && !key.any { it in '\u4e00'..'\u9fff' }) return true
        if (lower == "subscriptions-shortcut") return true
        if (key.all { it.isDigit() }) return true
        if (key.matches(Regex("^\\d+$"))) return true
        if (key.none { it.isWhitespace() || it in '\u4e00'..'\u9fff' } &&
            key.matches(Regex("^[a-z][a-z0-9_.-]*$"))
        ) {
            return true
        }
        return false
    }
}
