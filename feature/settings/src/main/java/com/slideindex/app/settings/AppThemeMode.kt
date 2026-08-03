package com.slideindex.app.settings

/** 应用主题明暗模式（对齐 WeKit Miuix 设置）。 */
enum class AppThemeMode(val id: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    ;

    fun resolveIsDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromId(id: Int): AppThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
/** Material 颜色规格版本（对齐 WeKit）。 */
enum class AppColorSpec(val id: Int) {
    SPEC_2021(0),
    SPEC_2025(1),
    ;

    companion object {
        fun fromId(id: Int): AppColorSpec = entries.firstOrNull { it.id == id } ?: SPEC_2025
    }
}

/** 主 Tab 底栏样式。 */
enum class BottomNavStyle(val id: Int) {
    CLASSIC(0),
    MIUIX(1),
    ;

    companion object {
        fun fromId(id: Int): BottomNavStyle = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}
