package com.slideindex.app.ui.miuix.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor as Material3LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.slideindex.app.settings.AppColorSpec
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.DarkBackgroundStyle
import com.slideindex.app.settings.OverlaySettings
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.ui.miuix.LocalTopAppBarBlurStyle
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/**
 * Miuix + Material 3 Expressive 双主题，对齐 WeKit [ModuleTheme]。
 * 配色由 [AppSettings] 驱动：customColor 关用 Miuix 默认蓝，开则 Monet + MaterialKolor。
 */
@Composable
fun ModuleTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeMode = AppThemeMode.fromId(settings.themeModeId)
    val darkTheme = themeMode.resolveIsDark(isSystemInDarkTheme())
    val paletteStyle = ThemePaletteStyle.fromId(settings.themePaletteStyleId)
    val colorSpec = AppColorSpec.fromId(settings.themeColorSpecId).let { spec ->
        if (paletteStyle.supportsMiuixSpec2025()) spec else AppColorSpec.SPEC_2021
    }

    val controller = if (!settings.customColorEnabled) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            darkColors = darkColorsFor(DarkBackgroundStyle.fromId(settings.darkBackgroundStyleId)),
            isDark = darkTheme,
        )
    } else {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = Color(MiuixSeedResolver.customSeed(context, settings, darkTheme)),
            colorSpec = colorSpec.toMiuix(),
            paletteStyle = paletteStyle.toMiuix(),
            isDark = darkTheme,
        )
    }

    val materialScheme = if (settings.customColorEnabled) {
        MiuixSeedResolver.materialScheme(
            MiuixSeedResolver.customSeed(context, settings, darkTheme),
            settings,
            darkTheme,
        )
    } else {
        defaultMiuixMaterialScheme(darkTheme, DarkBackgroundStyle.fromId(settings.darkBackgroundStyleId))
    }

    MiuixTheme(controller = controller) {
        MaterialExpressiveTheme(
            colorScheme = materialScheme,
            motionScheme = MotionScheme.expressive(),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MiuixTheme.colorScheme.onBackground,
                Material3LocalContentColor provides materialScheme.onBackground,
                LocalAppDarkTheme provides darkTheme,
                LocalTopAppBarBlurStyle provides TopAppBarBlurStyle.fromId(settings.topAppBarBlurStyleId),
            ) {
                content()
            }
        }
    }
}

/** 无 [AppSettings] 时用于独立 Activity 等场景的简化入口。 */
@Composable
fun ModuleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customColor: Boolean = false,
    seedColorArgb: Int = 0xFF6750A4.toInt(),
    dynamicWallpaper: Boolean = false,
    paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    colorSpec: AppColorSpec = AppColorSpec.SPEC_2025,
    content: @Composable () -> Unit,
) {
    ModuleTheme(
        settings = AppSettings(
            themeModeId = if (darkTheme) AppThemeMode.DARK.id else AppThemeMode.LIGHT.id,
            customColorEnabled = customColor,
            themeColorArgb = seedColorArgb,
            dynamicColorEnabled = dynamicWallpaper,
            themePaletteStyleId = paletteStyle.id,
            themeColorSpecId = colorSpec.id,
        ),
        content = content,
    )
}

/** 从 [OverlaySettings] 提取 ModuleTheme 所需字段。 */
fun OverlaySettings.toModuleThemeSettings(): AppSettings = AppSettings(
    themeColorArgb = themeColorArgb,
    dynamicColorEnabled = dynamicColorEnabled,
    themePaletteStyleId = themePaletteStyleId,
    themeModeId = themeModeId,
    customColorEnabled = customColorEnabled,
    darkBackgroundStyleId = darkBackgroundStyleId,
    themeColorSpecId = themeColorSpecId,
    topAppBarBlurStyleId = topAppBarBlurStyleId,
)

private fun defaultMiuixMaterialScheme(
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

private fun darkColorsFor(style: DarkBackgroundStyle) = miuixDarkColorScheme().copy(
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
