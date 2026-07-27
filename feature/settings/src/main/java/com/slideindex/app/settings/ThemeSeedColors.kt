package com.slideindex.app.settings

/** 应用内可选主题种子色（ARGB）。 */
object ThemeSeedColors {
    data class Entry(
        val argb: Int,
        val labelResName: String,
    )

    val presets: List<Int> = listOf(
        0xFF6750A4.toInt(),
        0xFF0061A4.toInt(),
        0xFF386A20.toInt(),
        0xFF984061.toInt(),
        0xFF7D5260.toInt(),
        0xFF006874.toInt(),
        0xFFB3261E.toInt(),
        0xFF8C5000.toInt(),
        0xFF4A4458.toInt(),
        0xFF1B6B5A.toInt(),
        0xFF5B5FC7.toInt(),
        0xFF2E7D32.toInt(),
    )

    fun normalize(argb: Int): Int =
        presets.firstOrNull { it == argb } ?: presets.first()
}
