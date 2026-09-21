package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.BottomNavBlurDefaults
import com.slideindex.app.ui.miuix.bottombar.liquid.IosLiquidGlassNavigationBar
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/** WeKit 式液态玻璃底栏高度（含内边距）。 */
val MainMiuixBottomNavBarHeight = 64.dp

/** 胶囊与系统导航栏之间的留白，对齐 Mishka IosLiquidGlass（8dp）。 */
val MainMiuixBottomNavOuterPadding = 8.dp

/**
 * 液态玻璃底栏：内部直接使用 Mishka 的 [IosLiquidGlassNavigationBar] 实现。
 *
 * 对外保持本项目原有入参（pager 进度跟手、模糊半径、重复点击回顶），
 * 由上游组件的三个本地扩展参数 progress / isTracking / blurRadiusDp 承接。
 */
@Composable
fun MiuixFloatingBottomNavBar(
    backdrop: LayerBackdrop?,
    targetTabIndex: Int,
    progress: () -> Float,
    isTracking: () -> Boolean,
    blurRadiusDp: Float,
    glassEnabled: Boolean,
    onTabSelected: (MainBottomNavDestination) -> Unit,
    onTabReselected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val items = destinations.map { destination ->
        NavigationItem(
            label = mainBottomNavLabel(destination),
            icon = mainLiquidGlassNavIcon(destination),
        )
    }

    IosLiquidGlassNavigationBar(
        items = items,
        selectedIndex = targetTabIndex,
        onItemClick = { index ->
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            val destination = destinations[index]
            if (index == targetTabIndex) {
                onTabReselected(destination)
            } else {
                onTabSelected(destination)
            }
        },
        backdrop = backdrop,
        isBlurActive = glassEnabled,
        isDark = LocalAppDarkTheme.current,
        showLabels = showLabel,
        modifier = modifier,
        progress = progress,
        isTracking = isTracking,
        blurRadiusDp = blurRadiusDp.coerceIn(
            BottomNavBlurDefaults.MIN_RADIUS_DP,
            BottomNavBlurDefaults.MAX_RADIUS_DP,
        ),
    )
}

@Composable
private fun mainLiquidGlassNavIcon(destination: MainBottomNavDestination): ImageVector = when (destination) {
    MainBottomNavDestination.Home -> Icons.Outlined.Home
    MainBottomNavDestination.Shake -> ImageVector.vectorResource(R.drawable.ic_nav_shake_outlined)
    MainBottomNavDestination.Notification -> Icons.Outlined.Notifications
    MainBottomNavDestination.Extension -> Icons.Outlined.Widgets
}

@Composable
private fun mainBottomNavLabel(destination: MainBottomNavDestination): String = when (destination) {
    MainBottomNavDestination.Home -> stringResource(R.string.main_nav_home)
    MainBottomNavDestination.Shake -> stringResource(R.string.main_nav_shake)
    MainBottomNavDestination.Notification -> stringResource(R.string.main_nav_notification)
    MainBottomNavDestination.Extension -> stringResource(R.string.main_nav_extension)
}
