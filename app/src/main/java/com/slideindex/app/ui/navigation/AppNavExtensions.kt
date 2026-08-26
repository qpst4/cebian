package com.slideindex.app.ui.navigation

import top.yukonga.miuix.kmp.nav.core.NavBackStack
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.FloatingPointerEdgeSide

fun PanelSide.toNavSide(): String = when (this) {
    PanelSide.LEFT -> "LEFT"
    PanelSide.RIGHT -> "RIGHT"
    PanelSide.BOTTOM -> "BOTTOM"
    PanelSide.TOP -> "TOP"
}

fun String.toPanelSide(): PanelSide = when (this) {
    "LEFT" -> PanelSide.LEFT
    "RIGHT" -> PanelSide.RIGHT
    "BOTTOM" -> PanelSide.BOTTOM
    "TOP" -> PanelSide.TOP
    else -> PanelSide.LEFT
}

fun FloatingPointerEdgeSide.toNavSide(): String = name

fun String.toFloatingPointerEdgeSide(): FloatingPointerEdgeSide =
    runCatching { FloatingPointerEdgeSide.valueOf(this) }.getOrDefault(FloatingPointerEdgeSide.TOP)

fun AppNavKey.isNotificationBranch(): Boolean = when (this) {
    AppNavKey.NotificationHub,
    AppNavKey.NotificationHistory,
    AppNavKey.NotificationFilterRules,
    is AppNavKey.NotificationFilterRuleEditor,
    AppNavKey.NotificationFilterSettings,
    AppNavKey.MessageReminder,
    AppNavKey.MessageReminderUnlockRules,
    AppNavKey.MessageReminderAllowedApps,
    is AppNavKey.MessageReminderAppFilterEdit,
    is AppNavKey.MessageReminderGestureActionPick,
    AppNavKey.MessageReminderDndApps,
    is AppNavKey.MessageStyleDetail,
    AppNavKey.MessageStyleSideBubbleCount,
    AppNavKey.OtpHub,
    AppNavKey.OtpSettings,
    is AppNavKey.OtpRecords,
    AppNavKey.OtpRulesList,
    AppNavKey.OtpAutoInput,
    is AppNavKey.OtpAutoFillStats,
    -> true
    else -> false
}

fun NavBackStack.navigate(key: AppNavKey) {
    add(key)
}

fun NavBackStack.navigateBackTo(key: AppNavKey) {
    while (isNotEmpty() && last() != key) {
        removeAt(lastIndex)
    }
}

fun NavBackStack.replaceRoot(key: AppNavKey) {
    clear()
    add(key)
}

fun NavBackStack.currentAppNavKey(): AppNavKey? = lastOrNull() as? AppNavKey
