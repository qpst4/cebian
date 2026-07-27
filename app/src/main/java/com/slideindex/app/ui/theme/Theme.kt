package com.slideindex.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SlideIndexTheme(
    seedColor: Color = Purple40,
    dynamicColor: Boolean = false,
    paletteStyle: com.slideindex.app.settings.ThemePaletteStyle =
        com.slideindex.app.settings.ThemePaletteStyle.TONAL_SPOT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = rememberSlideIndexColorScheme(
        seedColor = seedColor,
        dynamicColor = dynamicColor,
        paletteStyle = paletteStyle,
        darkTheme = darkTheme,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = SlideIndexTypography,
        content = content,
    )
}
