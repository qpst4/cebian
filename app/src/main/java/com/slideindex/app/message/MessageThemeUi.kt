package com.slideindex.app.message

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

import com.slideindex.app.message.MessageThemeColors

fun MessageThemeSpec.effectiveSideBackgroundResId(): Int =
    sideRightResId.takeIf { it != 0 } ?: backgroundResId

fun MessageThemeSpec.overlayAlpha(opacity: Float): Float {
    val themeAlpha = backgroundAlpha.coerceIn(0, 255) / 255f
    return opacity.coerceIn(0.2f, 1f) * themeAlpha
}

fun applyMessageThemeBackground(view: View, theme: MessageThemeSpec, opacity: Float = 1f) {
    val drawable = view.context.getDrawable(theme.backgroundResId)?.mutate() ?: run {
        view.setBackgroundResource(theme.backgroundResId)
        view.alpha = theme.overlayAlpha(opacity)
        return
    }
    theme.backgroundTintArgb?.let(drawable::setTint)
    drawable.alpha = (theme.overlayAlpha(opacity) * 255f).roundToInt().coerceIn(0, 255)
    view.background = drawable
}

private fun drawThemeBackground(
    context: Context,
    theme: MessageThemeSpec,
    width: Int,
    height: Int,
    opacity: Float,
    canvas: android.graphics.Canvas,
) {
    if (width <= 0 || height <= 0) return
    val drawable = context.getDrawable(theme.backgroundResId)?.mutate() ?: return
    theme.backgroundTintArgb?.let(drawable::setTint)
    drawable.alpha = (theme.overlayAlpha(opacity) * 255f).roundToInt().coerceIn(0, 255)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
}

@Composable
fun Modifier.messageThemeBackground(
    theme: MessageThemeSpec,
    opacity: Float = 1f,
): Modifier {
    val context = LocalContext.current
    return drawBehind {
        drawIntoCanvas { canvas ->
            drawThemeBackground(
                context = context,
                theme = theme,
                width = size.width.toInt(),
                height = size.height.toInt(),
                opacity = opacity,
                canvas = canvas.nativeCanvas,
            )
        }
    }
}
