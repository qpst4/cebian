package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 与当前设置页顶栏 [rememberMiuixBlurBackdrop] 共用，供 `scrollContent = false` 时嵌套列表参与毛玻璃采样。 */
val LocalMiuixScreenBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
fun Modifier.miuixScreenListBackdrop(): Modifier {
    val backdrop = LocalMiuixScreenBackdrop.current
    return then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
}

@Composable
fun rememberMiuixBlurBackdrop(enabled: Boolean = true): LayerBackdrop? {
    if (!enabled || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun LayerBackdrop?.miuixAppBarColor(): Color =
    if (this != null) Color.Transparent else MiuixTheme.colorScheme.surface

@Composable
fun Modifier.miuixAppBarBlur(
    backdrop: LayerBackdrop?,
    enabled: Boolean = true,
    blurRadius: Float = 25f,
    blendAlpha: Float = 0.82f,
    shape: Shape = RectangleShape,
): Modifier {
    if (!enabled || backdrop == null) return this
    val blendColor = MiuixTheme.colorScheme.surface.copy(alpha = blendAlpha)
    return then(
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = blendColor),
                ),
            ),
        ),
    )
}
