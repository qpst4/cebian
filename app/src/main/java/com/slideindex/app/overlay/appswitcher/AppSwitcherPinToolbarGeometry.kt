package com.slideindex.app.overlay.appswitcher

import com.slideindex.app.overlay.layout.AppSwitcherPanelLayout
import com.slideindex.app.overlay.layout.AppSwitcherSide
import kotlin.math.hypot

internal object AppSwitcherPinToolbarGeometry {
    const val BUTTON_COUNT = 2
    private const val BUTTON_SPACING_RATIO = 1.35f
    private const val INWARD_OFFSET_RATIO = 0.55f

    enum class Button {
        EDIT,
        DISMISS,
    }

    fun buttonCenter(
        layout: AppSwitcherPanelLayout,
        button: Button,
        density: Float,
    ): Pair<Float, Float> {
        val buttonRadius = layout.itemSizePx * 0.28f
        val spacing = buttonRadius * 2f * BUTTON_SPACING_RATIO
        val inward = layout.itemSizePx * INWARD_OFFSET_RATIO
        val anchorX = when (layout.side) {
            AppSwitcherSide.LEFT -> layout.anchorX + inward
            AppSwitcherSide.RIGHT -> layout.anchorX - inward
        }
        val index = when (button) {
            Button.EDIT -> 0
            Button.DISMISS -> 1
        }
        val centerY = layout.anchorY + (index - (BUTTON_COUNT - 1) / 2f) * spacing
        return anchorX to centerY
    }

    fun buttonRadius(layout: AppSwitcherPanelLayout): Float = layout.itemSizePx * 0.28f

    fun hitButton(
        layout: AppSwitcherPanelLayout,
        fingerX: Float,
        fingerY: Float,
        density: Float,
    ): Button? {
        val hitRadius = buttonRadius(layout) * 1.2f
        for (button in Button.entries) {
            val (cx, cy) = buttonCenter(layout, button, density)
            if (hypot(fingerX - cx, fingerY - cy) <= hitRadius) return button
        }
        return null
    }
}
