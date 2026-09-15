package com.slideindex.app.ui.miuix.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.DarkBackgroundStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/** 与 [ModuleTheme] 一致的 Material3 [ColorScheme]，供 Compose 与 View 主题共用。 */
fun resolveAppMaterialColorScheme(
    context: Context,
    settings: AppSettings,
    darkTheme: Boolean,
): ColorScheme =
    if (settings.customColorEnabled) {
        MiuixSeedResolver.materialScheme(
            MiuixSeedResolver.customSeed(context, settings, darkTheme),
            settings,
            darkTheme,
        )
    } else {
        defaultMiuixMaterialScheme(darkTheme, DarkBackgroundStyle.fromId(settings.darkBackgroundStyleId))
    }

internal fun defaultMiuixMaterialScheme(
    darkTheme: Boolean,
    darkBackgroundStyle: DarkBackgroundStyle,
): ColorScheme {
    val miuixColors = if (darkTheme) darkColorsFor(darkBackgroundStyle) else miuixLightColorScheme()
    val dialogSurface = miuixColors.surfaceContainer

    return dynamicColorScheme(
        seedColor = miuixColors.primary,
        isDark = darkTheme,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2021,
    ).copy(
        primary = miuixColors.primary,
        onPrimary = miuixColors.onPrimary,
        primaryContainer = miuixColors.primaryContainer,
        onPrimaryContainer = miuixColors.onPrimaryContainer,
        error = miuixColors.error,
        onError = miuixColors.onError,
        errorContainer = miuixColors.errorContainer,
        onErrorContainer = miuixColors.onErrorContainer,
        background = miuixColors.background,
        onBackground = miuixColors.onBackground,
        surface = dialogSurface,
        onSurface = miuixColors.onSurfaceContainer,
        surfaceVariant = miuixColors.surfaceVariant,
        onSurfaceVariant = miuixColors.onSurfaceVariantSummary,
        surfaceTint = dialogSurface,
        outline = miuixColors.outline,
        outlineVariant = miuixColors.dividerLine,
        surfaceBright = dialogSurface,
        surfaceContainerLowest = dialogSurface,
        surfaceContainerLow = dialogSurface,
        surfaceContainer = dialogSurface,
        surfaceContainerHigh = miuixColors.surfaceContainerHigh,
        surfaceContainerHighest = miuixColors.surfaceContainerHighest,
        surfaceDim = miuixColors.surface,
    )
}

internal fun darkColorsFor(style: DarkBackgroundStyle) = miuixDarkColorScheme().copy(
    background = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF101820)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF090D12)
        DarkBackgroundStyle.AMOLED_BLACK -> Color.Black
    },
    surface = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF17232D)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF11161C)
        DarkBackgroundStyle.AMOLED_BLACK -> Color(0xFF080808)
    },
    surfaceContainer = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF1C2B36)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF171D24)
        DarkBackgroundStyle.AMOLED_BLACK -> Color(0xFF101010)
    },
    surfaceContainerHigh = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF243744)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF202832)
        DarkBackgroundStyle.AMOLED_BLACK -> Color(0xFF181818)
    },
    surfaceContainerHighest = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF2C4352)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF29333E)
        DarkBackgroundStyle.AMOLED_BLACK -> Color(0xFF202020)
    },
)
