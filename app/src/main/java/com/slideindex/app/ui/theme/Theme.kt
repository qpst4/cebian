package com.slideindex.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun SlideIndexTheme(
    seedColor: Color = Purple40,
    dynamicColor: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> expressiveDarkColorScheme(seedColor)
        else -> expressiveLightColorScheme(seedColor)
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        typography = SlideIndexTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
