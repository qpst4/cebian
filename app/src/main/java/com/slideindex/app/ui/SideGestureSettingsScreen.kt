package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.actionFor
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.edgeTriggerWidthDp
import com.slideindex.app.settings.triggerBottomFraction
import com.slideindex.app.settings.triggerTopFraction
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideGestureSettingsScreen(
    side: PanelSide,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onSlotActionChange: (GestureTriggerType, GestureAction) -> Unit,
    onOpenQuickLauncherEditor: () -> Unit,
    onEdgeWidthChange: (Float) -> Unit,
    onTriggerVerticalRangeChange: (Float, Float) -> Unit,
    onAlignHandlesChange: (Boolean) -> Unit,
    onInterceptBackChange: (Boolean) -> Unit,
    onLimitInterceptLengthChange: (Boolean) -> Unit,
    onLayoutPreviewStart: () -> Unit,
    onLayoutPreviewStop: () -> Unit,
) {
    val title = when (side) {
        PanelSide.LEFT -> stringResource(R.string.side_gestures_left_title)
        PanelSide.RIGHT -> stringResource(R.string.side_gestures_right_title)
    }
    var pickingTrigger by remember { mutableStateOf<GestureTriggerType?>(null) }

    DisposableEffect(Unit) {
        onDispose { onLayoutPreviewStop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.side_gestures_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsSectionTitle(stringResource(R.string.side_gestures_short_distance))
            SettingsCard {
                GestureTriggerType.shortDistanceEntries().forEach { trigger ->
                    GestureSlotRow(
                        label = triggerLabel(side, trigger),
                        actionLabel = actionLabel(settings.actionFor(side, trigger)),
                        onClick = { pickingTrigger = trigger },
                    )
                }
            }
            SettingsSectionTitle(stringResource(R.string.side_gestures_press_tap))
            SettingsCard {
                GestureTriggerType.pressTapEntries().forEach { trigger ->
                    GestureSlotRow(
                        label = triggerLabel(side, trigger),
                        actionLabel = actionLabel(settings.actionFor(side, trigger)),
                        onClick = { pickingTrigger = trigger },
                    )
                }
            }
            SettingsSectionTitle(stringResource(R.string.side_gestures_long_distance))
            SettingsCard {
                GestureTriggerType.longDistanceEntries().forEach { trigger ->
                    GestureSlotRow(
                        label = triggerLabel(side, trigger),
                        actionLabel = actionLabel(settings.actionFor(side, trigger)),
                        onClick = { pickingTrigger = trigger },
                    )
                }
            }
            SettingsSectionTitle(stringResource(R.string.side_gestures_tools))
            SettingsCard {
                SettingNavigationRow(
                    icon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
                    title = stringResource(R.string.quick_launcher_editor_title),
                    subtitle = stringResource(R.string.quick_launcher_editor_desc),
                    onClick = onOpenQuickLauncherEditor,
                )
            }

            SettingsHintText(stringResource(R.string.side_gestures_preview_hint))
            SettingsSectionTitle(stringResource(R.string.side_gestures_handle_section))
            SettingsCard {
                SettingsSliderRow(
                    title = stringResource(R.string.handle_width),
                    value = settings.edgeTriggerWidthDp(side),
                    valueRange = 12f..36f,
                    enabled = serviceEnabled,
                    label = "${settings.edgeTriggerWidthDp(side).roundToInt()} dp",
                    startLabel = stringResource(R.string.handle_width_small),
                    endLabel = stringResource(R.string.handle_width_large),
                    triggersLayoutPreview = true,
                    onLayoutPreviewStart = onLayoutPreviewStart,
                    onLayoutPreviewStop = onLayoutPreviewStop,
                    onValueChange = onEdgeWidthChange,
                )
                SettingsRangeSliderRow(
                    title = stringResource(R.string.handle_length),
                    values = settings.triggerTopFraction(side)..
                        settings.triggerBottomFraction(side),
                    valueRange = 0.05f..0.95f,
                    startLabel = stringResource(R.string.handle_length_small),
                    endLabel = stringResource(R.string.handle_length_large),
                    enabled = serviceEnabled,
                    triggersLayoutPreview = true,
                    onLayoutPreviewStart = onLayoutPreviewStart,
                    onLayoutPreviewStop = onLayoutPreviewStop,
                    onValueChange = { range ->
                        onTriggerVerticalRangeChange(range.start, range.endInclusive)
                    },
                )
                SettingSwitchRow(
                    title = stringResource(R.string.align_handles),
                    subtitle = stringResource(R.string.align_handles_desc),
                    checked = settings.alignHandlesEnabled,
                    enabled = serviceEnabled,
                    onCheckedChange = onAlignHandlesChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.intercept_system_back),
                    subtitle = stringResource(R.string.intercept_system_back_desc),
                    checked = settings.interceptSystemBackGesture,
                    enabled = serviceEnabled,
                    onCheckedChange = onInterceptBackChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.limit_intercept_length),
                    subtitle = stringResource(R.string.limit_intercept_length_desc),
                    checked = settings.limitMaxInterceptLength,
                    enabled = serviceEnabled && settings.interceptSystemBackGesture,
                    onCheckedChange = onLimitInterceptLengthChange,
                )
            }
        }
    }

    pickingTrigger?.let { trigger ->
        ActionPickerDialog(
            side = side,
            trigger = trigger,
            current = settings.actionFor(side, trigger),
            onDismiss = { pickingTrigger = null },
            onSelect = { action ->
                onSlotActionChange(trigger, action)
                pickingTrigger = null
            },
        )
    }
}

@Composable
private fun GestureSlotRow(label: String, actionLabel: String, onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
        title = label,
        subtitle = actionLabel,
        onClick = onClick,
    )
}

@Composable
private fun ActionPickerDialog(
    side: PanelSide,
    trigger: GestureTriggerType,
    current: GestureAction,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
) {
    val options = buildList {
        add(GestureAction.None)
        if (trigger.supportsIndex) add(GestureAction.OpenIndex)
        add(GestureAction.QuickLauncher)
        add(GestureAction.TaskSwitcher)
        add(GestureAction.Back)
        add(GestureAction.Home)
        add(GestureAction.Recents)
        add(GestureAction.CloseCurrentApp)
        add(GestureAction.FreeWindowCurrentApp)
        if (trigger == GestureTriggerType.SHORT_SINGLE_TAP) add(GestureAction.ClickPassthrough)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(triggerLabel(side, trigger)) },
        text = {
            Column {
                options.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = action.type == current.type, onClick = { onSelect(action) })
                        Text(actionLabel(action))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun triggerLabel(side: PanelSide, trigger: GestureTriggerType): String = stringResource(
    when (trigger) {
        GestureTriggerType.SHORT_SWIPE_IN, GestureTriggerType.LONG_SWIPE_IN -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_in_left
            PanelSide.RIGHT -> R.string.gesture_swipe_in_right
        }
        GestureTriggerType.SHORT_SWIPE_UP_RIGHT, GestureTriggerType.LONG_SWIPE_UP_RIGHT -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_up_right_on_left
            PanelSide.RIGHT -> R.string.gesture_swipe_up_left_on_right
        }
        GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT, GestureTriggerType.LONG_SWIPE_DOWN_RIGHT -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_down_right_on_left
            PanelSide.RIGHT -> R.string.gesture_swipe_down_left_on_right
        }
        GestureTriggerType.SHORT_SWIPE_UP -> R.string.gesture_short_swipe_up
        GestureTriggerType.SHORT_SWIPE_DOWN -> R.string.gesture_short_swipe_down
        GestureTriggerType.SHORT_LONG_PRESS -> R.string.gesture_short_long_press
        GestureTriggerType.SHORT_SINGLE_TAP -> R.string.gesture_short_single_tap
        GestureTriggerType.LONG_SWIPE_UP -> R.string.gesture_long_swipe_up
        GestureTriggerType.LONG_SWIPE_DOWN -> R.string.gesture_long_swipe_down
        GestureTriggerType.LONG_LONG_PRESS -> R.string.gesture_long_long_press
        GestureTriggerType.LONG_SINGLE_TAP -> R.string.gesture_long_single_tap
    },
)

@Composable
private fun actionLabel(action: GestureAction): String = when (action.type) {
    GestureActionType.NONE -> stringResource(R.string.gesture_action_none)
    GestureActionType.OPEN_INDEX -> stringResource(R.string.gesture_action_open_index)
    GestureActionType.LAUNCH_APP -> stringResource(R.string.gesture_action_launch_app)
    GestureActionType.QUICK_LAUNCHER -> stringResource(R.string.gesture_action_quick_launcher)
    GestureActionType.TASK_SWITCHER -> stringResource(R.string.gesture_action_task_switcher)
    GestureActionType.BACK -> stringResource(R.string.gesture_action_back)
    GestureActionType.HOME -> stringResource(R.string.gesture_action_home)
    GestureActionType.RECENTS -> stringResource(R.string.gesture_action_recents)
    GestureActionType.CLOSE_CURRENT_APP -> stringResource(R.string.gesture_action_close_current_app)
    GestureActionType.FREE_WINDOW_CURRENT_APP -> stringResource(R.string.gesture_action_free_window_current_app)
    GestureActionType.CLICK_PASSTHROUGH -> stringResource(R.string.gesture_action_click_passthrough)
}

@Composable
fun SideGesturesEntryCard(onOpenLeft: () -> Unit, onOpenRight: () -> Unit) {
    SettingNavigationRow(
        icon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
        title = stringResource(R.string.side_gestures_entry_left),
        subtitle = stringResource(R.string.side_gestures_entry_desc),
        onClick = onOpenLeft,
    )
    SettingNavigationRow(
        icon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
        title = stringResource(R.string.side_gestures_entry_right),
        subtitle = stringResource(R.string.side_gestures_entry_desc),
        onClick = onOpenRight,
    )
}
