package com.slideindex.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.BottomNavBlurDefaults
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.slideindex.app.ui.a11y.cdBottomNavExtension
import com.slideindex.app.ui.a11y.cdBottomNavHome
import com.slideindex.app.ui.a11y.cdBottomNavNotification
import com.slideindex.app.ui.a11y.cdBottomNavShake
import kotlin.math.min

enum class MainBottomNavDestination {
    Home,
    Shake,
    Notification,
    Extension,
}

val MainBottomNavHeight = 72.dp
val MainBottomNavOuterPadding = 16.dp
val MainBottomNavHorizontalPadding = 24.dp
private val MainBottomNavCornerRadius = 28.dp
private const val MainBottomNavGlassTintAlpha = 0.72f
private val MainBottomNavIndicatorInset = 4.dp
private val MainBottomNavContentPadding = 6.dp
private const val MainBottomNavPressOverlayAlpha = 0.08f
private val MainBottomNavIconSize = 24.dp
private val MainBottomNavIndicatorSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
)
private const val MainBottomNavItemTweenDurationMs = 180
private val MainBottomNavItemTween = tween<Float>(
    durationMillis = MainBottomNavItemTweenDurationMs,
    easing = FastOutSlowInEasing,
)
private val MainBottomNavItemColorTween = tween<Color>(
    durationMillis = MainBottomNavItemTweenDurationMs,
    easing = FastOutSlowInEasing,
)

@Composable
private fun rememberBottomNavGlassStyle(blurRadius: Dp) = HazeDefaults.style(
    backgroundColor = MaterialTheme.colorScheme.surface,
    tint = HazeTint(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = MainBottomNavGlassTintAlpha)),
    blurRadius = blurRadius,
    noiseFactor = 0f,
)

private fun DrawScope.drawBottomNavCapsule(
    itemIndex: Float,
    itemCount: Int,
    color: Color,
    inset: Dp = MainBottomNavIndicatorInset,
) {
    if (itemCount <= 0 || size.width <= 0f || size.height <= 0f) return
    val itemWidthPx = size.width / itemCount
    val insetPx = inset.toPx()
    val capsuleWidthPx = itemWidthPx - insetPx * 2f
    val capsuleHeightPx = size.height
    val left = itemWidthPx * itemIndex + insetPx
    val radius = min(capsuleWidthPx, capsuleHeightPx) / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(left, 0f),
        size = Size(capsuleWidthPx, capsuleHeightPx),
        cornerRadius = CornerRadius(radius, radius),
    )
}

@Composable
fun FloatingBottomNavBar(
    hazeState: HazeState,
    glassEnabled: Boolean,
    selected: MainBottomNavDestination,
    blurRadiusDp: Float,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val selectedIndex = destinations.indexOf(selected).coerceAtLeast(0)
    val itemCount = destinations.size
    val barShape = RoundedCornerShape(MainBottomNavCornerRadius)
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val pressOverlayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MainBottomNavPressOverlayAlpha)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val blurRadius = blurRadiusDp.coerceIn(
        BottomNavBlurDefaults.MIN_RADIUS_DP,
        BottomNavBlurDefaults.MAX_RADIUS_DP,
    ).dp
    val glassStyle = rememberBottomNavGlassStyle(blurRadius)
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MainBottomNavIndicatorSpring,
        label = "bottomNavIndicatorOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MainBottomNavHeight)
            .shadow(4.dp, barShape, clip = false)
            .clip(barShape),
    ) {
        if (glassEnabled) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = barShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = glassStyle),
            )
        } else {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = barShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 0.5.dp, color = borderColor, shape = barShape),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = MainBottomNavContentPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .drawBehind {
                        val pressed = pressedIndex
                        when {
                            pressed == null -> {
                                drawBottomNavCapsule(
                                    itemIndex = indicatorOffset,
                                    itemCount = itemCount,
                                    color = indicatorColor,
                                )
                            }
                            pressed == selectedIndex -> {
                                drawBottomNavCapsule(
                                    itemIndex = pressed.toFloat(),
                                    itemCount = itemCount,
                                    color = indicatorColor,
                                )
                                drawBottomNavCapsule(
                                    itemIndex = pressed.toFloat(),
                                    itemCount = itemCount,
                                    color = pressOverlayColor,
                                )
                            }
                            else -> {
                                drawBottomNavCapsule(
                                    itemIndex = indicatorOffset,
                                    itemCount = itemCount,
                                    color = indicatorColor,
                                )
                                drawBottomNavCapsule(
                                    itemIndex = pressed.toFloat(),
                                    itemCount = itemCount,
                                    color = indicatorColor,
                                )
                                drawBottomNavCapsule(
                                    itemIndex = pressed.toFloat(),
                                    itemCount = itemCount,
                                    color = pressOverlayColor,
                                )
                            }
                        }
                    },
            ) {
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Home,
                    showLabel = showLabels,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 0
                            pressedIndex == 0 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Home)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Home else Icons.Outlined.Home,
                            contentDescription = cdBottomNavHome(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_home),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Shake,
                    showLabel = showLabels,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 1
                            pressedIndex == 1 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Shake)
                    },
                    icon = { isSelected ->
                        Icon(
                            painter = painterResource(
                                if (isSelected) R.drawable.ic_nav_shake else R.drawable.ic_nav_shake_outlined,
                            ),
                            contentDescription = cdBottomNavShake(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_shake),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Notification,
                    showLabel = showLabels,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 2
                            pressedIndex == 2 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Notification)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Notifications else Icons.Outlined.Notifications,
                            contentDescription = cdBottomNavNotification(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_notification),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Extension,
                    showLabel = showLabels,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 3
                            pressedIndex == 3 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Extension)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Widgets else Icons.Outlined.Widgets,
                            contentDescription = cdBottomNavExtension(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_extension),
                )
            }
        }
    }
}

private fun DrawScope.drawSideNavCapsule(
    itemIndex: Float,
    itemCount: Int,
    color: Color,
    inset: Dp = MainBottomNavIndicatorInset,
) {
    if (itemCount <= 0 || size.width <= 0f || size.height <= 0f) return
    val itemHeightPx = size.height / itemCount
    val insetPx = inset.toPx()
    val capsuleHeightPx = itemHeightPx - insetPx * 2f
    val capsuleWidthPx = size.width
    val top = itemHeightPx * itemIndex + insetPx
    val radius = min(capsuleWidthPx, capsuleHeightPx) / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, top),
        size = Size(capsuleWidthPx, capsuleHeightPx),
        cornerRadius = CornerRadius(radius, radius),
    )
}

@Composable
fun FloatingSideNavRail(
    hazeState: HazeState,
    glassEnabled: Boolean,
    selected: MainBottomNavDestination,
    blurRadiusDp: Float,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val selectedIndex = destinations.indexOf(selected).coerceAtLeast(0)
    val itemCount = destinations.size
    val barShape = RoundedCornerShape(MainBottomNavCornerRadius)
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val pressOverlayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MainBottomNavPressOverlayAlpha)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val blurRadius = blurRadiusDp.coerceIn(
        BottomNavBlurDefaults.MIN_RADIUS_DP,
        BottomNavBlurDefaults.MAX_RADIUS_DP,
    ).dp
    val glassStyle = rememberBottomNavGlassStyle(blurRadius)
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MainBottomNavIndicatorSpring,
        label = "sideNavIndicatorOffset",
    )

    Box(
        modifier = modifier
            .width(MainNavRailWidth)
            .fillMaxHeight()
            .shadow(4.dp, barShape, clip = false)
            .clip(barShape),
    ) {
        if (glassEnabled) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = barShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState, style = glassStyle),
            )
        } else {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = barShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 0.5.dp, color = borderColor, shape = barShape),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MainBottomNavContentPadding, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .drawBehind {
                        val pressed = pressedIndex
                        when {
                            pressed == null -> {
                                drawSideNavCapsule(
                                    itemIndex = indicatorOffset,
                                    itemCount = itemCount,
                                    color = indicatorColor,
                                )
                            }
                            pressed == selectedIndex -> {
                                drawSideNavCapsule(
                                    itemIndex = pressed.toFloat(),
                                    itemCount = itemCount,
                                    color = indicatorColor,
                                )
                                drawSideNavCapsule(
                                    itemIndex = pressed.toFloat(),
                                    itemCount = itemCount,
                                    color = pressOverlayColor,
                                )
                            }
                            else -> {
                                drawSideNavCapsule(
                                    itemIndex = indicatorOffset,
                                    itemCount = itemCount,
                                    color = indicatorColor,
                                )
                                drawSideNavCapsule(
                                    itemIndex = pressed.toFloat(),
                                    itemCount = itemCount,
                                    color = indicatorColor,
                                )
                                drawSideNavCapsule(
                                    itemIndex = pressed.toFloat(),
                                    itemCount = itemCount,
                                    color = pressOverlayColor,
                                )
                            }
                        }
                    },
            ) {
                FloatingSideNavItem(
                    selected = selected == MainBottomNavDestination.Home,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 0
                            pressedIndex == 0 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Home)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Home else Icons.Outlined.Home,
                            contentDescription = cdBottomNavHome(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_home),
                )
                FloatingSideNavItem(
                    selected = selected == MainBottomNavDestination.Shake,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 1
                            pressedIndex == 1 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Shake)
                    },
                    icon = { isSelected ->
                        Icon(
                            painter = painterResource(
                                if (isSelected) R.drawable.ic_nav_shake else R.drawable.ic_nav_shake_outlined,
                            ),
                            contentDescription = cdBottomNavShake(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_shake),
                )
                FloatingSideNavItem(
                    selected = selected == MainBottomNavDestination.Notification,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 2
                            pressedIndex == 2 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Notification)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Notifications else Icons.Outlined.Notifications,
                            contentDescription = cdBottomNavNotification(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_notification),
                )
                FloatingSideNavItem(
                    selected = selected == MainBottomNavDestination.Extension,
                    onPressedChange = { isPressed ->
                        pressedIndex = when {
                            isPressed -> 3
                            pressedIndex == 3 -> null
                            else -> pressedIndex
                        }
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Extension)
                    },
                    icon = { isSelected ->
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Widgets else Icons.Outlined.Widgets,
                            contentDescription = cdBottomNavExtension(),
                            modifier = Modifier.size(MainBottomNavIconSize),
                        )
                    },
                    label = stringResource(R.string.main_nav_extension),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.FloatingSideNavItem(
    selected: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    icon: @Composable (selected: Boolean) -> Unit,
    label: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        onPressedChange(pressed)
    }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MainBottomNavItemColorTween,
        label = "sideNavItemColor",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = MainBottomNavIndicatorInset,
                vertical = 6.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = MainBottomNavItemTween,
            label = "sideNavIcon",
        ) { isSelected ->
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon(isSelected)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.FloatingBottomNavItem(
    selected: Boolean,
    showLabel: Boolean = true,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    icon: @Composable (selected: Boolean) -> Unit,
    label: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        onPressedChange(pressed)
    }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MainBottomNavItemColorTween,
        label = "bottomNavItemColor",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = MainBottomNavIndicatorInset,
                vertical = 6.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Crossfade(
            targetState = selected,
            animationSpec = MainBottomNavItemTween,
            label = "bottomNavIcon",
        ) { isSelected ->
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon(isSelected)
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
