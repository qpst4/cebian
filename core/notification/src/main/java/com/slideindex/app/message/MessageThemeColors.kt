package com.slideindex.app.message

import androidx.annotation.ColorInt

object MessageThemeColors {
    fun parseHex(hex: String, @ColorInt fallback: Int = 0xFF474747.toInt()): Int {
        val normalized = hex.removePrefix("#")
        return when (normalized.length) {
            6 -> (0xFF000000 or normalized.toLong(16)).toInt()
            8 -> normalized.toLong(16).toInt()
            else -> fallback
        }
    }

    fun contentColor(@ColorInt titleColor: Int): Int =
        (titleColor and 0x00FFFFFF) or 0xB3000000.toInt()
}
