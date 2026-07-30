package com.slideindex.app.overlay.corner

import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.gesture.GestureAction

internal fun CornerGestureSettings.slotsFor(anchor: CornerAnchor): List<GestureAction> =
    if (unifiedSlots) {
        leftSlots
    } else {
        when (anchor) {
            CornerAnchor.LEFT -> leftSlots
            CornerAnchor.RIGHT -> rightSlots
        }
    }
