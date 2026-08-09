package com.slideindex.app.ui

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState

/**
 * 宽屏主界面侧栏（对齐 Mishka [NavigationRail] + [NavigationRailItem]）。
 */
@Composable
fun MainMiuixNavigationRail(
    selected: MainBottomNavDestination,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shakeSelectedIcon = ImageVector.vectorResource(R.drawable.ic_nav_shake)
    val shakeUnselectedIcon = ImageVector.vectorResource(R.drawable.ic_nav_shake_outlined)
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        state = rememberNavigationRailState(),
    ) {
        NavigationRailItem(
            selected = selected == MainBottomNavDestination.Home,
            onClick = { onDestinationSelected(MainBottomNavDestination.Home) },
            icon = if (selected == MainBottomNavDestination.Home) Icons.Filled.Home else Icons.Outlined.Home,
            label = stringResource(R.string.main_nav_home),
        )
        NavigationRailItem(
            selected = selected == MainBottomNavDestination.Shake,
            onClick = { onDestinationSelected(MainBottomNavDestination.Shake) },
            icon = if (selected == MainBottomNavDestination.Shake) shakeSelectedIcon else shakeUnselectedIcon,
            label = stringResource(R.string.main_nav_shake),
        )
        NavigationRailItem(
            selected = selected == MainBottomNavDestination.Notification,
            onClick = { onDestinationSelected(MainBottomNavDestination.Notification) },
            icon = if (selected == MainBottomNavDestination.Notification) {
                Icons.Filled.Notifications
            } else {
                Icons.Outlined.Notifications
            },
            label = stringResource(R.string.main_nav_notification),
        )
        NavigationRailItem(
            selected = selected == MainBottomNavDestination.Extension,
            onClick = { onDestinationSelected(MainBottomNavDestination.Extension) },
            icon = if (selected == MainBottomNavDestination.Extension) Icons.Filled.Widgets else Icons.Outlined.Widgets,
            label = stringResource(R.string.main_nav_extension),
        )
    }
}
