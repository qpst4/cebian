package com.slideindex.app.widget

import androidx.compose.ui.graphics.Color

object WidgetPanelUi {
    val PanelSurfaceBaseColor = Color(0xFF1C1C1E)

    /**
     * 与 [com.slideindex.app.overlay.WidgetPopupContentRenderer] 浮层面板背景一致。
     */
    fun panelSurfaceColor(
        overlayAlpha: Float,
        editMode: Boolean = false,
        blurEnabled: Boolean = false,
    ): Color {
        val panelAlpha = overlayAlpha.coerceIn(0.2f, 0.95f)
        val alpha = when {
            editMode -> (panelAlpha + 0.2f).coerceAtMost(0.88f)
            blurEnabled -> (panelAlpha * 0.55f + 0.18f).coerceAtMost(0.72f)
            else -> (panelAlpha + 0.1f).coerceAtMost(0.82f)
        }
        return PanelSurfaceBaseColor.copy(alpha = alpha)
    }
}
