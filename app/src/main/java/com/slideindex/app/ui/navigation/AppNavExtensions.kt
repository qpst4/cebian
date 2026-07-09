package com.slideindex.app.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import com.slideindex.app.overlay.PanelSide

fun PanelSide.toNavSide(): String = when (this) {
    PanelSide.LEFT -> "LEFT"
    PanelSide.RIGHT -> "RIGHT"
}

fun String.toPanelSide(): PanelSide = when (this) {
    "LEFT" -> PanelSide.LEFT
    "RIGHT" -> PanelSide.RIGHT
    else -> PanelSide.LEFT
}

fun AppNavKey.isNotificationBranch(): Boolean = when (this) {
    AppNavKey.NotificationHub,
    AppNavKey.NotificationHistory,
    AppNavKey.OtpHub,
    AppNavKey.OtpSettings,
    is AppNavKey.OtpRecords,
    AppNavKey.OtpRulesList,
    AppNavKey.OtpAutoInput,
    -> true
    else -> false
}

fun NavBackStack<AppNavKey>.navigate(key: AppNavKey) {
    add(key)
}

fun NavBackStack<AppNavKey>.navigateBackTo(key: AppNavKey) {
    while (isNotEmpty() && last() != key) {
        removeAt(lastIndex)
    }
}

fun NavBackStack<AppNavKey>.replaceRoot(key: AppNavKey) {
    clear()
    add(key)
}
