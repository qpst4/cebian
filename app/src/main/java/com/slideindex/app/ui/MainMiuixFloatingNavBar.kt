package com.slideindex.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix 官方浮动底栏内容区留白（栏高 + 底部悬浮间距）。 */
val MainFloatingNavBarHeight = 52.dp
val MainFloatingNavBarBottomOffset = 26.dp
val MainFloatingNavBarContentClearance = MainFloatingNavBarHeight + MainFloatingNavBarBottomOffset

/** 悬浮栏胶囊圆角（对齐 Mishka）。 */
private val MainFloatingNavCornerRadius = 50.dp

@Composable
fun MiuixOfficialFloatingBottomNavBar(
    selectedIndex: Int,
    onTabSelected: (MainBottomNavDestination) -> Unit,
    onTabReselected: (MainBottomNavDestination) -> Unit,
    showLabel: Boolean = true,
    backdrop: LayerBackdrop? = null,
    blurEnabled: Boolean = false,
    blurRadiusDp: Float = 25f,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(MainFloatingNavCornerRadius)
    // 毛玻璃降级：底层不支持 RenderEffect（isRenderEffectSupported 为 false）时
    // rememberMiuixBlurBackdrop 返回 null，这里自动退回纯色 surfaceContainer。
    val blurActive = blurEnabled && backdrop != null

    val blurModifier = if (blurActive) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadiusDp,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(
                        color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                    ),
                ),
            ),
            highlight = if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight,
        )
    } else {
        Modifier
    }

    FloatingNavigationBar(
        modifier = modifier.then(blurModifier),
        color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        cornerRadius = MainFloatingNavCornerRadius,
        defaultWindowInsetsPadding = true,
    ) {
        destinations.forEachIndexed { index, destination ->
            val selected = index == selectedIndex
            MiuixFloatingNavItem(
                selected = selected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (selected) {
                        onTabReselected(destination)
                    } else {
                        onTabSelected(destination)
                    }
                },
                icon = mainFloatingNavIcon(destination, selected),
                label = mainFloatingNavLabel(destination),
                showLabel = showLabel,
            )
        }
    }
}

/** 悬浮栏 Item：支持「图标+文字 / 仅图标」两种模式（对齐 Mishka）。 */
@Composable
private fun MiuixFloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseColor = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = when {
        isPressed -> baseColor.copy(alpha = if (selected) 0.7f else 0.5f)
        selected -> baseColor
        else -> baseColor.copy(alpha = 0.6f)
    }

    Column(
        modifier = Modifier
            .defaultMinSize(
                minWidth = if (showLabel) 56.dp else 48.dp,
                minHeight = 48.dp,
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(horizontal = if (showLabel) 8.dp else 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = icon,
            contentDescription = if (showLabel) null else label,
            tint = tint,
        )
        if (showLabel) {
            Text(
                text = label,
                color = tint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun mainFloatingNavIcon(
    destination: MainBottomNavDestination,
    selected: Boolean,
): ImageVector = when (destination) {
    MainBottomNavDestination.Home -> if (selected) Icons.Default.Home else Icons.Outlined.Home
    MainBottomNavDestination.Shake -> if (selected) Icons.Default.Vibration else Icons.Outlined.Vibration
    MainBottomNavDestination.Notification -> if (selected) {
        Icons.Default.Notifications
    } else {
        Icons.Outlined.Notifications
    }
    MainBottomNavDestination.Extension -> if (selected) Icons.Default.Widgets else Icons.Outlined.Widgets
}

@Composable
private fun mainFloatingNavLabel(destination: MainBottomNavDestination): String = when (destination) {
    MainBottomNavDestination.Home -> stringResource(R.string.main_nav_home)
    MainBottomNavDestination.Shake -> stringResource(R.string.main_nav_shake)
    MainBottomNavDestination.Notification -> stringResource(R.string.main_nav_notification)
    MainBottomNavDestination.Extension -> stringResource(R.string.main_nav_extension)
}
