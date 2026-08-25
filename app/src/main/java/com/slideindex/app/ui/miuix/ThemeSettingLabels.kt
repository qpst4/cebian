package com.slideindex.app.ui.miuix

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppColorSpec
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.BottomNavMode
import com.slideindex.app.settings.BottomNavStyle
import com.slideindex.app.settings.DarkBackgroundStyle
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.settings.TopAppBarBlurStyle

@StringRes
fun AppThemeMode.labelRes(): Int = when (this) {
    AppThemeMode.SYSTEM -> R.string.theme_mode_system
    AppThemeMode.LIGHT -> R.string.theme_mode_light
    AppThemeMode.DARK -> R.string.theme_mode_dark
}

@Composable
fun AppThemeMode.displayName(): String = stringResource(labelRes())

@StringRes
fun DarkBackgroundStyle.labelRes(): Int = when (this) {
    DarkBackgroundStyle.QUIET_BLUE -> R.string.theme_dark_background_quiet_blue
    DarkBackgroundStyle.DEEP_BLACK -> R.string.theme_dark_background_deep_black
    DarkBackgroundStyle.AMOLED_BLACK -> R.string.theme_dark_background_amoled_black
}

@StringRes
fun AppColorSpec.labelRes(): Int = when (this) {
    AppColorSpec.SPEC_2021 -> R.string.theme_color_spec_2021
    AppColorSpec.SPEC_2025 -> R.string.theme_color_spec_2025
}

@Composable
fun AppColorSpec.displayName(): String = stringResource(labelRes())

@StringRes
fun TopAppBarBlurStyle.labelRes(): Int = when (this) {
    TopAppBarBlurStyle.GAUSSIAN -> R.string.top_app_bar_blur_style_gaussian
    TopAppBarBlurStyle.PROGRESSIVE -> R.string.top_app_bar_blur_style_progressive
}

@Composable
fun TopAppBarBlurStyle.displayName(): String = stringResource(labelRes())

@StringRes
fun BottomNavStyle.labelRes(): Int = when (this) {
    BottomNavStyle.CLASSIC -> R.string.bottom_nav_style_classic
    BottomNavStyle.LIQUID_GLASS -> R.string.bottom_nav_style_liquid_glass
    BottomNavStyle.FLOATING_NAV -> R.string.bottom_nav_style_floating_nav
}

@Composable
fun BottomNavStyle.displayName(): String = stringResource(labelRes())

@StringRes
fun BottomNavMode.labelRes(): Int = when (this) {
    BottomNavMode.ICON_AND_TEXT -> R.string.bottom_nav_mode_icon_and_text
    BottomNavMode.ICON_ONLY -> R.string.bottom_nav_mode_icon_only
}

@Composable
fun BottomNavMode.displayName(): String = stringResource(labelRes())

@StringRes
fun ThemePaletteStyle.labelRes(): Int = when (this) {
    ThemePaletteStyle.TONAL_SPOT -> R.string.theme_palette_tonal_spot
    ThemePaletteStyle.VIBRANT -> R.string.theme_palette_vibrant
    ThemePaletteStyle.EXPRESSIVE -> R.string.theme_palette_expressive
    ThemePaletteStyle.MONOCHROME -> R.string.theme_palette_monochrome
    ThemePaletteStyle.NEUTRAL -> R.string.theme_palette_neutral
    ThemePaletteStyle.FIDELITY -> R.string.theme_palette_fidelity
    ThemePaletteStyle.CONTENT -> R.string.theme_palette_content
    ThemePaletteStyle.RAINBOW -> R.string.theme_palette_rainbow
    ThemePaletteStyle.FRUIT_SALAD -> R.string.theme_palette_fruit_salad
}

@Composable
fun ThemePaletteStyle.displayName(): String = stringResource(labelRes())
