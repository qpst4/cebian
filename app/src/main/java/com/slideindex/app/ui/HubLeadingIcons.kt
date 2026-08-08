package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hive
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Password
import com.slideindex.app.gesture.GestureActionType

/** 消息 / 扩展 Hub 列表项 leading icon（与首页 `outlinedLeadingIcons` 一致）。 */
internal object HubLeadingIcons {
    fun notifications(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.Notifications, Icons.Outlined.Notifications)

    fun notificationHistory(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.History, Icons.Outlined.History)

    fun otpHub(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.Password, Icons.Outlined.Password)

    fun layoutSettings(outlined: Boolean) =
        if (outlined) gestureActionTypeOutlinedIcon(GestureActionType.OPEN_INDEX) else Icons.Default.SortByAlpha

    fun quickLauncher(outlined: Boolean) =
        if (outlined) gestureActionTypeOutlinedIcon(GestureActionType.QUICK_LAUNCHER) else Icons.Default.Apps

    fun honeycombLauncher(outlined: Boolean) =
        if (outlined) gestureActionTypeOutlinedIcon(GestureActionType.HONEYCOMB_LAUNCHER) else Icons.Default.Hive

    fun activityShortcut(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.AutoMirrored.Filled.Launch, Icons.AutoMirrored.Outlined.Launch)

    fun shellCommand(outlined: Boolean) =
        if (outlined) gestureActionTypeOutlinedIcon(GestureActionType.SHELL_COMMAND_PANEL) else Icons.Default.Code

    fun widgetPanel(outlined: Boolean) =
        if (outlined) gestureActionTypeOutlinedIcon(GestureActionType.WIDGET_POPUP_OVERLAY) else Icons.Default.Widgets

    fun floatingPointer(outlined: Boolean) =
        if (outlined) gestureActionTypeOutlinedIcon(GestureActionType.FLOATING_POINTER) else Icons.Default.MyLocation

    fun stashClipboard(outlined: Boolean) =
        if (outlined) gestureActionTypeOutlinedIcon(GestureActionType.OPEN_CLIPBOARD_PANEL) else Icons.Default.ContentPaste

    fun searchPanel(outlined: Boolean) =
        if (outlined) gestureActionTypeOutlinedIcon(GestureActionType.SEARCH_PANEL) else Icons.Default.Search

    fun settingsBackup(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.Backup, Icons.Outlined.Backup)

    fun about(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.Info, Icons.Outlined.Info)
}
