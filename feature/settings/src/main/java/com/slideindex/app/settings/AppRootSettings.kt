package com.slideindex.app.settings

data class ThemeSettings(
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val dynamicColorEnabled: Boolean = true,
)

data class AppRootSettings(
    val themeColorArgb: Int = 0xFF6750A4.toInt(),
    val dynamicColorEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val hideFromRecents: Boolean = false,
) {
    companion object {
        fun from(settings: AppSettings): AppRootSettings = AppRootSettings(
            themeColorArgb = settings.themeColorArgb,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            onboardingCompleted = settings.onboardingCompleted,
            hideFromRecents = settings.hideFromRecents,
        )
    }
}
