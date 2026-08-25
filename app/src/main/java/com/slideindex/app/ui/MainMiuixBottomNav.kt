package com.slideindex.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.BottomNavBlurDefaults
import com.slideindex.app.ui.a11y.cdBottomNavExtension
import com.slideindex.app.ui.a11y.cdBottomNavHome
import com.slideindex.app.ui.a11y.cdBottomNavNotification
import com.slideindex.app.ui.a11y.cdBottomNavShake
import com.slideindex.app.ui.miuix.bottombar.FloatingBottomBar
import com.slideindex.app.ui.miuix.bottombar.FloatingBottomBarDefaults
import com.slideindex.app.ui.miuix.bottombar.FloatingBottomBarItem
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** WeKit 式液态玻璃底栏高度（含内边距）。 */
val MainMiuixBottomNavBarHeight = 64.dp

/** 胶囊与系统导航栏之间的留白，对齐 Mishka IosLiquidGlass（8dp）。 */
val MainMiuixBottomNavOuterPadding = 8.dp

/** Mishka [IosLiquidGlassNavigationBar] 同款底部间距：outer + navigationBars，无 inset 时 36dp。 */
@Composable
fun mainMiuixLiquidGlassBottomNavPadding(): Dp {
    val navBarBottomPadding = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    return if (navBarBottomPadding != 0.dp) {
        MainMiuixBottomNavOuterPadding + navBarBottomPadding
    } else {
        36.dp
    }
}

@Composable
fun MiuixFloatingBottomNavBar(
    backdrop: Backdrop,
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
    val blurRadius = blurRadiusDp.coerceIn(
        BottomNavBlurDefaults.MIN_RADIUS_DP,
        BottomNavBlurDefaults.MAX_RADIUS_DP,
    ).dp

    FloatingBottomBar(
        modifier = modifier,
        selectedIndex = { targetTabIndex },
        progress = progress,
        isTracking = isTracking,
        onSelected = { index ->
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onTabSelected(destinations[index])
        },
        onTabReselected = { index ->
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onTabReselected(destinations[index])
        },
        backdrop = backdrop,
        tabsCount = destinations.size,
        isBlurEnabled = glassEnabled,
        blurRadius = blurRadius,
        colors = FloatingBottomBarDefaults.colors(
            containerColor = MiuixTheme.colorScheme.surfaceContainer,
            indicatorColor = MiuixTheme.colorScheme.primary,
            contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            activeContentColor = MiuixTheme.colorScheme.primary,
        ),
    ) {
        destinations.forEachIndexed { index, destination ->
            FloatingBottomBarItem(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onTabSelected(destination)
                },
                modifier = Modifier.defaultMinSize(minWidth = if (showLabel) 76.dp else 52.dp),
            ) {
                Crossfade(
                    targetState = index == targetTabIndex,
                    animationSpec = tween(200),
                    label = "mainNavIcon-$index",
                ) { selected ->
                    MainBottomNavTabIcon(destination = destination, selected = selected)
                }
                if (showLabel) {
                    Text(
                        text = mainBottomNavLabel(destination),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainBottomNavTabIcon(
    destination: MainBottomNavDestination,
    selected: Boolean,
) {
    when (destination) {
        MainBottomNavDestination.Home -> Icon(
            imageVector = if (selected) Icons.Default.Home else Icons.Outlined.Home,
            contentDescription = cdBottomNavHome(),
            modifier = Modifier.size(MainBottomNavIconSize),
        )
        MainBottomNavDestination.Shake -> Icon(
            painter = painterResource(
                if (selected) R.drawable.ic_nav_shake else R.drawable.ic_nav_shake_outlined,
            ),
            contentDescription = cdBottomNavShake(),
            modifier = Modifier.size(MainBottomNavIconSize),
        )
        MainBottomNavDestination.Notification -> Icon(
            imageVector = if (selected) Icons.Default.Notifications else Icons.Outlined.Notifications,
            contentDescription = cdBottomNavNotification(),
            modifier = Modifier.size(MainBottomNavIconSize),
        )
        MainBottomNavDestination.Extension -> Icon(
            imageVector = if (selected) Icons.Default.Widgets else Icons.Outlined.Widgets,
            contentDescription = cdBottomNavExtension(),
            modifier = Modifier.size(MainBottomNavIconSize),
        )
    }
}

@Composable
private fun mainBottomNavLabel(destination: MainBottomNavDestination): String = when (destination) {
    MainBottomNavDestination.Home -> stringResource(R.string.main_nav_home)
    MainBottomNavDestination.Shake -> stringResource(R.string.main_nav_shake)
    MainBottomNavDestination.Notification -> stringResource(R.string.main_nav_notification)
    MainBottomNavDestination.Extension -> stringResource(R.string.main_nav_extension)
}

private val MainBottomNavIconSize = 24.dp
