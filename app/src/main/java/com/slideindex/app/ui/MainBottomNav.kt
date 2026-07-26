package com.slideindex.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.min
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.slideindex.app.ui.a11y.cdBottomNavExtension
import com.slideindex.app.ui.a11y.cdBottomNavHome
import com.slideindex.app.ui.a11y.cdBottomNavNotification
import com.slideindex.app.ui.a11y.cdBottomNavShake

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
private val MainBottomNavBlurRadius = 24.dp
private const val MainBottomNavGlassTintAlpha = 0.72f
private val MainBottomNavIndicatorInset = 4.dp
private val MainBottomNavContentPadding = 6.dp

@Composable
private fun rememberBottomNavGlassStyle() = HazeDefaults.style(
    backgroundColor = MaterialTheme.colorScheme.surface,
    tint = HazeTint(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = MainBottomNavGlassTintAlpha)),
    blurRadius = MainBottomNavBlurRadius,
    noiseFactor = 0f,
)

@Composable
fun FloatingBottomNavBar(
    hazeState: HazeState,
    selected: MainBottomNavDestination,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val selectedIndex = destinations.indexOf(selected).coerceAtLeast(0)
    val itemCount = destinations.size
    val barShape = RoundedCornerShape(MainBottomNavCornerRadius)
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val glassStyle = rememberBottomNavGlassStyle()
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "bottomNavIndicatorOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MainBottomNavHeight)
            .shadow(4.dp, barShape, clip = false)
            .clip(barShape),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeEffect(state = hazeState, style = glassStyle),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 0.5.dp, color = borderColor, shape = barShape),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = MainBottomNavContentPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .drawBehind {
                        if (size.width <= 0f || size.height <= 0f || itemCount == 0) return@drawBehind
                        val itemWidthPx = size.width / itemCount
                        val insetPx = MainBottomNavIndicatorInset.toPx()
                        val indicatorWidthPx = itemWidthPx - insetPx * 2f
                        val indicatorHeightPx = size.height
                        val left = itemWidthPx * indicatorOffset + insetPx
                        val radius = min(indicatorWidthPx, indicatorHeightPx) / 2f
                        drawRoundRect(
                            color = indicatorColor,
                            topLeft = Offset(left, 0f),
                            size = Size(indicatorWidthPx, indicatorHeightPx),
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    },
            ) {
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Home,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Home)
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = cdBottomNavHome()) },
                    label = stringResource(R.string.main_nav_home),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Shake,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Shake)
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_shake),
                            contentDescription = cdBottomNavShake(),
                        )
                    },
                    label = stringResource(R.string.main_nav_shake),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Notification,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Notification)
                    },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = cdBottomNavNotification()) },
                    label = stringResource(R.string.main_nav_notification),
                )
                FloatingBottomNavItem(
                    selected = selected == MainBottomNavDestination.Extension,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onDestinationSelected(MainBottomNavDestination.Extension)
                    },
                    icon = { Icon(Icons.Default.Widgets, contentDescription = cdBottomNavExtension()) },
                    label = stringResource(R.string.main_nav_extension),
                )
            }
        }
    }
}

@Composable
private fun RowScope.FloatingBottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
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
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
