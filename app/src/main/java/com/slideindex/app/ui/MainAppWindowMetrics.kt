package com.slideindex.app.ui

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.slideindex.app.settings.BottomNavStyle

val MainNavRailWidth = 80.dp

@Composable
fun mainAppWindowAdaptiveInfo(): WindowAdaptiveInfo = currentWindowAdaptiveInfoV2()

/**
 * 宽度 ≥ Medium（600dp）时用侧栏；竖屏 Compact 宽度仍用底栏。
 * 官方 [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo] 在高度 Compact（手机横屏）时仍会选底栏。
 */
@Composable
fun mainAppPrefersNavigationRail(): Boolean {
    val adaptiveInfo = mainAppWindowAdaptiveInfo()
    return adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
}

/** 非 Compact 宽度时限制设置内容最大宽度并居中（与 Google 大屏单栏建议一致）。 */
@Composable
fun mainAppPrefersWideContentLayout(): Boolean = mainAppPrefersNavigationRail()

fun mainAppRootBottomContentPadding(
    prefersNavigationRail: Boolean,
    isRootDestination: Boolean,
    bottomNavStyle: BottomNavStyle = BottomNavStyle.CLASSIC,
): Dp = when {
    !isRootDestination -> 16.dp
    prefersNavigationRail -> MainBottomNavOuterPadding
    bottomNavStyle == BottomNavStyle.LIQUID_GLASS ->
        MainMiuixBottomNavBarHeight + MainMiuixBottomNavOuterPadding
    bottomNavStyle == BottomNavStyle.FLOATING_NAV ->
        MainFloatingNavBarContentClearance
    else -> MainBottomNavHeight + MainBottomNavOuterPadding
}
