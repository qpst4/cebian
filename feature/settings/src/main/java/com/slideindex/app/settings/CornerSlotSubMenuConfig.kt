package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction

data class CornerSlotSubMenuConfig(
    val enabled: Boolean = false,
    val items: List<GestureAction.LaunchShortcut> = emptyList(),
) {
    fun isActive(): Boolean = enabled && items.isNotEmpty()
}
