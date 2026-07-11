package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.shake.ShakeGestureType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeActionSetSettingsScreen(
    title: String,
    subtitle: String,
    actions: Map<ShakeGestureType, GestureAction>,
    onBack: () -> Unit,
    onActionChange: (ShakeGestureType, GestureAction) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pickingGesture by remember { mutableStateOf<ShakeGestureType?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsHintText(subtitle)
            SettingsCard {
                ShakeGestureType.entries.forEach { type ->
                    ShakeGestureActionRow(
                        type = type,
                        action = actions[type] ?: GestureAction.None,
                        onClick = { pickingGesture = type },
                    )
                }
            }
        }
    }

    AnimatedFullScreenOverlay(visible = pickingGesture != null) {
        pickingGesture?.let { gestureType ->
            GestureActionPickerScreen(
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                current = actions[gestureType] ?: GestureAction.None,
                onDismiss = { pickingGesture = null },
                onSelect = { action ->
                    onActionChange(gestureType, action)
                    pickingGesture = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeIndependentSensitivityScreen(
    globalSensitivity: Float,
    perDirectionSensitivity: Map<ShakeGestureType, Float>,
    onBack: () -> Unit,
    onSensitivityChange: (ShakeGestureType, Float) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.shake_gestures_independent_sensitivity)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsHintText(stringResource(R.string.shake_gestures_sensitivity_hint))
            SettingsCard {
                ShakeGestureType.entries.forEach { type ->
                    SettingsSliderRow(
                        title = shakeGestureLabel(type),
                        value = perDirectionSensitivity[type] ?: globalSensitivity,
                        valueRange = 1f..10f,
                        steps = 8,
                        enabled = true,
                        label = String.format(java.util.Locale.US, "%.1f", perDirectionSensitivity[type] ?: globalSensitivity),
                        formatLabel = { String.format(java.util.Locale.US, "%.1f", it) },
                        startLabel = stringResource(R.string.shake_gestures_sensitivity_easy),
                        endLabel = stringResource(R.string.shake_gestures_sensitivity_hard),
                        onValueChange = { onSensitivityChange(type, it) },
                    )
                }
            }
        }
    }
}

@Composable
fun ShakeGestureActionRow(
    type: ShakeGestureType,
    action: GestureAction,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = {
            ShakeGestureColoredIcon(
                icon = shakeGestureIcon(type),
                background = shakeGestureIconTint(type),
            )
        },
        title = shakeGestureLabel(type),
        subtitle = gestureActionLabel(action),
        enabled = enabled,
        onClick = onClick,
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = gestureActionIcon(action),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = gestureActionLabel(action),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
fun ShakeGestureColoredIcon(
    icon: ImageVector,
    background: Color,
    contentColor: Color = Color.White,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun shakeGestureLabel(type: ShakeGestureType): String = when (type) {
    ShakeGestureType.LEFT_FLIP -> stringResource(R.string.shake_gesture_left_flip)
    ShakeGestureType.RIGHT_FLIP -> stringResource(R.string.shake_gesture_right_flip)
    ShakeGestureType.FORWARD_FLIP -> stringResource(R.string.shake_gesture_forward_flip)
    ShakeGestureType.BACKWARD_FLIP -> stringResource(R.string.shake_gesture_backward_flip)
    ShakeGestureType.LEFT_FLICK -> stringResource(R.string.shake_gesture_left_flick)
    ShakeGestureType.RIGHT_FLICK -> stringResource(R.string.shake_gesture_right_flick)
}

fun shakeGestureIcon(type: ShakeGestureType): ImageVector = when (type) {
    ShakeGestureType.LEFT_FLIP -> Icons.AutoMirrored.Filled.RotateLeft
    ShakeGestureType.RIGHT_FLIP -> Icons.AutoMirrored.Filled.RotateRight
    ShakeGestureType.FORWARD_FLIP -> Icons.Default.ScreenRotation
    ShakeGestureType.BACKWARD_FLIP -> Icons.Default.ScreenRotation
    ShakeGestureType.LEFT_FLICK -> Icons.Default.SwipeLeft
    ShakeGestureType.RIGHT_FLICK -> Icons.Default.SwipeRight
}

fun shakeGestureIconTint(type: ShakeGestureType): Color = when (type) {
    ShakeGestureType.LEFT_FLIP,
    ShakeGestureType.RIGHT_FLIP,
    -> Color(0xFF42A5F5)
    ShakeGestureType.FORWARD_FLIP,
    ShakeGestureType.BACKWARD_FLIP,
    -> Color(0xFFFF9800)
    ShakeGestureType.LEFT_FLICK,
    ShakeGestureType.RIGHT_FLICK,
    -> Color(0xFFAB47BC)
}
