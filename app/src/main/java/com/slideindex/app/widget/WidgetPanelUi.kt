package com.slideindex.app.widget

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

object WidgetPanelUi {
    val PanelSurfaceBaseColor = Color(0xFF1C1C1E)

    /**
     * 与 [com.slideindex.app.widget.WidgetPopupCardLayout] 浮层面板背景一致。
     */
    fun panelSurfaceColor(
        overlayAlpha: Float,
        editMode: Boolean = false,
        blurEnabled: Boolean = false,
    ): Color {
        val panelAlpha = overlayAlpha.coerceIn(0f, 1f)
        if (panelAlpha <= 0f) {
            return PanelSurfaceBaseColor.copy(alpha = 0f)
        }
        val alpha = when {
            editMode -> (panelAlpha + 0.2f).coerceAtMost(0.88f)
            blurEnabled -> (panelAlpha * 0.55f + 0.18f).coerceAtMost(0.72f)
            else -> (panelAlpha + 0.1f).coerceAtMost(0.82f)
        }
        return PanelSurfaceBaseColor.copy(alpha = alpha)
    }

    fun panelSurfaceColorInt(
        overlayAlpha: Float,
        editMode: Boolean = false,
        blurEnabled: Boolean = false,
    ): Int {
        val panelAlpha = overlayAlpha.coerceIn(0f, 1f)
        if (panelAlpha <= 0f) {
            return 0x001C1C1E
        }
        val alpha = when {
            editMode -> (panelAlpha + 0.2f).coerceAtMost(0.88f)
            blurEnabled -> (panelAlpha * 0.55f + 0.18f).coerceAtMost(0.72f)
            else -> (panelAlpha + 0.1f).coerceAtMost(0.82f)
        }
        val alphaInt = (alpha * 255).roundToInt().coerceIn(0, 255)
        return (alphaInt shl 24) or 0x001C1C1E
    }
}
