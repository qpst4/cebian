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
    /** 经典毛玻璃悬浮底栏。 */
    CLASSIC(0),
    /** WeKit 式液态玻璃底栏（原「Miuix 胶囊」）。 */
    LIQUID_GLASS(1),
    /** Miuix 官方 FloatingNavigationBar（仅图标）。 */
    FLOATING_NAV(2),
    ;

    companion object {
        fun fromId(id: Int): BottomNavStyle = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** 主 Tab 底栏内容模式（图标+文字 / 仅图标）。 */
enum class BottomNavMode(val id: Int) {
    ICON_AND_TEXT(0),
    ICON_ONLY(1),
    ;

    val showLabels: Boolean get() = this == ICON_AND_TEXT

    companion object {
        fun fromId(id: Int): BottomNavMode = entries.firstOrNull { it.id == id } ?: ICON_AND_TEXT
    }
}
