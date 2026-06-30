package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.gesture.actionFor
import com.slideindex.app.gesture.defaultTriggerModeFor
import com.slideindex.app.gesture.preferredTriggerMode
import com.slideindex.app.gesture.slotTriggerMode
import com.slideindex.app.gesture.supportsAction
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper
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
    onSlotConfigChange: (GestureTriggerType, GestureAction, GestureTriggerMode) -> Unit,
    onDefaultTriggerModeChange: (GestureTriggerMode) -> Unit,
    onShortSwipeDistanceChange: (Float) -> Unit,
    onLongSwipeDistanceChange: (Float) -> Unit,
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
    var pickingDefaultMode by remember { mutableStateOf(false) }

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
            SettingsSectionTitle(stringResource(R.string.side_gestures_behavior_section))
            SettingsCard {
                SettingNavigationRow(
                    icon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
                    title = stringResource(R.string.default_trigger_mode),
                    subtitle = triggerModeLabel(settings.defaultTriggerModeFor(side), includeDefault = false),
                    onClick = { pickingDefaultMode = true },
                )
                SettingsSliderRow(
                    title = stringResource(R.string.short_swipe_distance),
                    value = settings.shortSwipeDistanceDp,
                    valueRange = SwipePathRecognizer.SHORT_DISTANCE_MIN_DP..
                        SwipePathRecognizer.SHORT_DISTANCE_MAX_DP,
                    enabled = serviceEnabled,
                    label = "",
                    formatLabel = { "${it.roundToInt()} dp" },
                    commitOnFinish = true,
                    startLabel = stringResource(R.string.swipe_distance_small),
                    endLabel = stringResource(R.string.swipe_distance_large),
                    onValueChange = onShortSwipeDistanceChange,
                )
                SettingsSliderRow(
                    title = stringResource(R.string.long_swipe_distance),
                    value = settings.longSwipeDistanceDp,
                    valueRange = SwipePathRecognizer.LONG_DISTANCE_MIN_DP..
                        SwipePathRecognizer.LONG_DISTANCE_MAX_DP,
                    enabled = serviceEnabled,
                    label = "",
                    formatLabel = { "${it.roundToInt()} dp" },
                    commitOnFinish = true,
                    startLabel = stringResource(R.string.swipe_distance_small),
                    endLabel = stringResource(R.string.swipe_distance_large),
                    onValueChange = onLongSwipeDistanceChange,
                )
            }
            SettingsSectionTitle(stringResource(R.string.side_gestures_short_distance))
            SettingsCard {
                GestureTriggerType.shortDistanceEntries().forEach { trigger ->
                    GestureSlotRow(
                        label = triggerLabel(side, trigger),
                        actionLabel = actionLabel(settings.actionFor(side, trigger)),
                        modeLabel = triggerModeLabel(settings.slotTriggerMode(side, trigger)),
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
                        modeLabel = triggerModeLabel(settings.slotTriggerMode(side, trigger)),
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
                        modeLabel = triggerModeLabel(settings.slotTriggerMode(side, trigger)),
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
        SlotConfigDialog(
            side = side,
            settings = settings,
            trigger = trigger,
            currentAction = settings.actionFor(side, trigger),
            currentMode = settings.slotTriggerMode(side, trigger),
            onDismiss = { pickingTrigger = null },
            onConfirm = { action, mode ->
                onSlotConfigChange(trigger, action, mode)
                pickingTrigger = null
            },
        )
    }

    if (pickingDefaultMode) {
        TriggerModePickerDialog(
            title = stringResource(R.string.default_trigger_mode),
            current = settings.defaultTriggerModeFor(side),
            action = GestureAction.None,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            includeDefaultOption = false,
            onDismiss = { pickingDefaultMode = false },
            onSelect = { mode ->
                onDefaultTriggerModeChange(mode)
                pickingDefaultMode = false
            },
        )
    }
}

@Composable
private fun GestureSlotRow(
    label: String,
    actionLabel: String,
    modeLabel: String,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
        title = label,
        subtitle = "$actionLabel · $modeLabel",
        onClick = onClick,
    )
}

@Composable
private fun SlotConfigDialog(
    side: PanelSide,
    settings: AppSettings,
    trigger: GestureTriggerType,
    currentAction: GestureAction,
    currentMode: GestureTriggerMode,
    onDismiss: () -> Unit,
    onConfirm: (GestureAction, GestureTriggerMode) -> Unit,
) {
    var selectedAction by remember { mutableStateOf(currentAction) }
    var selectedMode by remember { mutableStateOf(currentMode) }
    var pickingAction by remember { mutableStateOf(false) }
    var pickingMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sideDefaultMode = settings.defaultTriggerModeFor(side)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(triggerLabel(side, trigger)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.slot_action_type),
                    style = MaterialTheme.typography.labelLarge,
                )
                SettingNavigationRow(
                    icon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
                    title = actionLabel(selectedAction),
                    subtitle = stringResource(R.string.slot_pick_action),
                    onClick = { pickingAction = true },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = stringResource(R.string.slot_trigger_mode),
                    style = MaterialTheme.typography.labelLarge,
                )
                SettingNavigationRow(
                    icon = { Icon(Icons.Default.SwipeRight, contentDescription = null) },
                    title = slotTriggerModeTitle(selectedMode, sideDefaultMode),
                    subtitle = slotTriggerModeSubtitle(selectedMode, sideDefaultMode),
                    onClick = { pickingMode = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedAction, selectedMode) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (pickingAction) {
        ActionPickerDialog(
            trigger = trigger,
            current = selectedAction,
            onDismiss = { pickingAction = false },
            onSelect = { action ->
                requestPermissionForAdjustAction(context, action)
                selectedAction = action
                if (!selectedMode.supportsAction(action, trigger)) {
                    selectedMode = action.preferredTriggerMode(trigger)
                        ?: GestureTriggerMode.ON_RELEASE
                }
                pickingAction = false
            },
        )
    }

    if (pickingMode) {
        TriggerModePickerDialog(
            title = stringResource(R.string.slot_pick_trigger_mode),
            current = selectedMode,
            action = selectedAction,
            trigger = trigger,
            sideDefaultMode = sideDefaultMode,
            includeDefaultOption = true,
            onDismiss = { pickingMode = false },
            onSelect = { mode ->
                selectedMode = mode
                pickingMode = false
            },
        )
    }
}

@Composable
private fun ActionPickerDialog(
    trigger: GestureTriggerType,
    current: GestureAction,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
) {
    val context = LocalContext.current
    val actionOptions = buildList {
        add(GestureAction.None)
        if (trigger.supportsIndex) add(GestureAction.OpenIndex)
        add(GestureAction.QuickLauncher)
        add(GestureAction.TaskSwitcher)
        add(GestureAction.Back)
        add(GestureAction.Home)
        add(GestureAction.Recents)
        add(GestureAction.CloseCurrentApp)
        add(GestureAction.FreeWindowCurrentApp)
        add(GestureAction.Flashlight)
        if (trigger.supportsIndex) {
            add(GestureAction.AdjustVolume)
            add(GestureAction.AdjustBrightness)
        }
        add(GestureAction.LaunchAssistant)
        if (trigger == GestureTriggerType.SHORT_SINGLE_TAP) add(GestureAction.ClickPassthrough)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.slot_pick_action)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                actionOptions.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(
                            selected = action.type == current.type,
                            onClick = { onSelect(action) },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(actionLabel(action))
                            actionDescription(action)?.let { description ->
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            actionPermissionHint(action, context)?.let { hint ->
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
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
private fun slotTriggerModeTitle(mode: GestureTriggerMode, sideDefaultMode: GestureTriggerMode): String =
    when (mode) {
        GestureTriggerMode.DEFAULT -> stringResource(R.string.trigger_mode_default)
        else -> triggerModeLabel(mode, includeDefault = false)
    }

@Composable
private fun slotTriggerModeSubtitle(
    mode: GestureTriggerMode,
    sideDefaultMode: GestureTriggerMode,
): String = when (mode) {
    GestureTriggerMode.DEFAULT -> stringResource(
        R.string.trigger_mode_default_slot_desc,
        triggerModeLabel(sideDefaultMode, includeDefault = false),
    )
    else -> triggerModeDescription(mode)
}

@Composable
private fun TriggerModePickerDialog(
    title: String,
    current: GestureTriggerMode,
    action: GestureAction,
    trigger: GestureTriggerType,
    sideDefaultMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    includeDefaultOption: Boolean,
    onDismiss: () -> Unit,
    onSelect: (GestureTriggerMode) -> Unit,
) {
    val modes = if (includeDefaultOption) {
        GestureTriggerMode.configurableEntries
    } else {
        GestureTriggerMode.configurableEntries.filter { it != GestureTriggerMode.DEFAULT }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                modes.forEach { mode ->
                    val enabled = mode == GestureTriggerMode.DEFAULT || mode.supportsAction(action, trigger)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (enabled) Modifier.clickable { onSelect(mode) } else Modifier)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = current == mode,
                            onClick = { if (enabled) onSelect(mode) },
                            enabled = enabled,
                        )
                        Column {
                            Text(
                                text = when (mode) {
                                    GestureTriggerMode.DEFAULT ->
                                        stringResource(R.string.trigger_mode_default)
                                    else -> triggerModeLabel(mode, includeDefault = false)
                                },
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                            Text(
                                text = when (mode) {
                                    GestureTriggerMode.DEFAULT -> stringResource(
                                        R.string.trigger_mode_default_slot_desc,
                                        triggerModeLabel(sideDefaultMode, includeDefault = false),
                                    )
                                    else -> triggerModeDescription(mode)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
private fun triggerModeLabel(mode: GestureTriggerMode, includeDefault: Boolean = true): String =
    when (mode) {
        GestureTriggerMode.DEFAULT ->
            if (includeDefault) stringResource(R.string.trigger_mode_default) else stringResource(R.string.trigger_mode_on_release)
        GestureTriggerMode.ON_RELEASE -> stringResource(R.string.trigger_mode_on_release)
        GestureTriggerMode.CONTINUOUS -> stringResource(R.string.trigger_mode_continuous)
        GestureTriggerMode.IMMEDIATE -> stringResource(R.string.trigger_mode_immediate)
    }

@Composable
private fun triggerModeDescription(mode: GestureTriggerMode): String = when (mode) {
    GestureTriggerMode.DEFAULT -> stringResource(R.string.default_trigger_mode_desc)
    GestureTriggerMode.ON_RELEASE -> stringResource(R.string.trigger_mode_on_release_desc)
    GestureTriggerMode.CONTINUOUS -> stringResource(
        R.string.trigger_mode_continuous_desc,
        continuousTrackingActionsSummary(),
    )
    GestureTriggerMode.IMMEDIATE -> stringResource(R.string.trigger_mode_immediate_desc)
}

@Composable
private fun continuousTrackingActionsSummary(): String {
    val labels = mutableListOf<String>()
    for (action in GestureAction.continuousTrackingActions) {
        labels.add(actionLabel(action))
    }
    return labels.joinToString("、")
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
    GestureActionType.FLASHLIGHT -> stringResource(R.string.gesture_action_flashlight)
    GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume)
    GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness)
    GestureActionType.LAUNCH_ASSISTANT -> stringResource(R.string.gesture_action_launch_assistant)
}

@Composable
private fun actionDescription(action: GestureAction): String? = when (action.type) {
    GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume_desc)
    GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness_desc)
    else -> null
}

@Composable
private fun actionPermissionHint(action: GestureAction, context: Context): String? =
    when (action.type) {
        GestureActionType.ADJUST_VOLUME -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            stringResource(R.string.gesture_action_adjust_volume_permission)
        }
        GestureActionType.ADJUST_BRIGHTNESS -> {
            if (PermissionHelper.canWriteSettings(context)) return null
            stringResource(R.string.gesture_action_adjust_brightness_permission)
        }
        else -> null
    }

private fun requestPermissionForAdjustAction(context: Context, action: GestureAction) {
    when (action) {
        GestureAction.AdjustVolume -> PermissionHelper.requestNotificationPolicyAccess(context)
        GestureAction.AdjustBrightness -> PermissionHelper.requestWriteSettingsAccess(context)
        else -> Unit
    }
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
