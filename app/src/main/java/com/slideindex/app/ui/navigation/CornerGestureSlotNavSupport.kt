package com.slideindex.app.ui.navigation

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerSlotSubMenuConfig

internal const val CORNER_SLOT_CORNER_RIGHT = "right"

internal fun cornerSlotEditorKey(corner: String, slotIndex: Int): AppNavKey.HomeCornerGestureSlotEditor =
    AppNavKey.HomeCornerGestureSlotEditor(corner, slotIndex)

internal fun cornerSlotCurrentAction(
    corner: String,
    slotIndex: Int,
    settings: CornerGestureSettings,
): GestureAction =
    when (corner) {
        CORNER_SLOT_CORNER_RIGHT -> {
            if (settings.unifiedSlots) {
                settings.leftSlots
            } else {
                settings.rightSlots
            }.getOrElse(slotIndex) { GestureAction.None }
        }
        else -> settings.leftSlots.getOrElse(slotIndex) { GestureAction.None }
    }

internal fun cornerSlotSubMenuConfig(
    corner: String,
    slotIndex: Int,
    settings: CornerGestureSettings,
): CornerSlotSubMenuConfig =
    when {
        corner == CORNER_SLOT_CORNER_RIGHT && !settings.unifiedSlots ->
            settings.rightSlotSubMenus.getOrElse(slotIndex) { CornerSlotSubMenuConfig() }
        else ->
            settings.leftSlotSubMenus.getOrElse(slotIndex) { CornerSlotSubMenuConfig() }
    }
