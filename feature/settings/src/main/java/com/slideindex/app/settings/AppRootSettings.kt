package com.slideindex.app.settings

data class ThemeSettings(
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val dynamicColorEnabled: Boolean = true,
    val themePaletteStyleId: Int = ThemePaletteStyle.TONAL_SPOT.id,
)

data class AppRootSettings(
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val dynamicColorEnabled: Boolean = true,
    val themePaletteStyleId: Int = ThemePaletteStyle.TONAL_SPOT.id,
    val onboardingCompleted: Boolean = false,
    val hideFromRecents: Boolean = false,
    val predictiveBackEnabled: Boolean = false,
    val privilegeMode: PrivilegeMode = PrivilegeMode.SHIZUKU,
) {
    companion object {
        fun from(settings: AppSettings): AppRootSettings = AppRootSettings(
            themeColorArgb = settings.themeColorArgb,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            themePaletteStyleId = settings.themePaletteStyleId,
            onboardingCompleted = settings.onboardingCompleted,
            hideFromRecents = settings.hideFromRecents,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            privilegeMode = settings.privilegeMode,
        )
    }
}
