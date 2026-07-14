package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.defaultTriggerModeFor
import com.slideindex.app.settings.slotTriggerMode
import com.slideindex.app.gesture.preferredTriggerMode
import com.slideindex.app.gesture.supportsAction
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SideGestureSettingsScreen(
    side: PanelSide,
    handleId: String,
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDesignSettings: () -> Unit,
    onSlotConfigChange: (String, GestureTriggerType, GestureAction, GestureTriggerMode) -> Unit,
    onDefaultTriggerModeChange: (GestureTriggerMode) -> Unit,
    onPreviewStart: () -> Unit = {},
    onPreviewStop: () -> Unit = {},
) {
    val pairIndex = settings.triggerCollectionEntries().indexOfFirst { it.handleId == handleId }.let {
        if (it >= 0) it + 1 else 1
    }
    val pairCount = settings.triggerCollectionEntries().size
    val selectedHandle = settings.triggerHandle(side, handleId)
        ?: settings.primaryTriggerHandle(side)
    val baseTitle = when (side) {
        PanelSide.LEFT -> stringResource(R.string.side_gestures_left_title)
        PanelSide.RIGHT -> stringResource(R.string.side_gestures_right_title)
    }
    val title = if (pairCount > 1) "$baseTitle · $pairIndex" else baseTitle
    var pickingTrigger by remember { mutableStateOf<GestureTriggerType?>(null) }
    var pickingDefaultMode by remember { mutableStateOf(false) }

    TriggerHandlePreviewLifecycle(
        enabled = serviceEnabled,
        side = side,
        handleId = handleId,
        onPreviewStart = { _, _ -> onPreviewStart() },
        onPreviewStop = onPreviewStop,
    )

    SettingsScreenScaffold(
        title = title,
        subtitle = stringResource(R.string.side_gestures_desc),
        onBack = onBack,
    ) {
            SettingsSectionTitle(stringResource(R.string.side_gestures_behavior_section))
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Brush, contentDescription = label) },
                    title = stringResource(R.string.trigger_design_entry),
                    subtitle = triggerDesignSummary(selectedHandle.design),
                    onClick = onOpenDesignSettings,
                )
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Animation, contentDescription = label) },
                    title = stringResource(R.string.trigger_appearance_entry),
                    subtitle = triggerAppearanceSummary(settings, side, handleId),
                    onClick = onOpenAppearanceSettings,
                )
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.SwipeRight, contentDescription = label) },
                    title = stringResource(R.string.default_trigger_mode),
                    subtitle = triggerModeLabel(settings.defaultTriggerModeFor(side), includeDefault = false),
                    onClick = { pickingDefaultMode = true },
                )
            }

            SettingsSectionTitle(stringResource(R.string.side_gestures_short_distance))
            SettingsCard {
                GestureTriggerType.shortDistanceEntries().forEach { trigger ->
                    GestureSlotRow(
                        label = triggerLabel(side, trigger),
                        action = settings.actionFor(side, trigger, handleId),
                        modeLabel = triggerModeLabel(settings.slotTriggerMode(side, trigger, handleId)),
                        onClick = { pickingTrigger = trigger },
                    )
                }
            }
            SettingsSectionTitle(stringResource(R.string.side_gestures_press_tap))
            SettingsCard {
                GestureTriggerType.pressTapEntries().forEach { trigger ->
                    GestureSlotRow(
                        label = triggerLabel(side, trigger),
                        action = settings.actionFor(side, trigger, handleId),
                        modeLabel = triggerModeLabel(settings.slotTriggerMode(side, trigger, handleId)),
                        onClick = { pickingTrigger = trigger },
                    )
                }
            }
            SettingsSectionTitle(stringResource(R.string.side_gestures_long_distance))
            SettingsCard {
                GestureTriggerType.longDistanceEntries().forEach { trigger ->
                    GestureSlotRow(
                        label = triggerLabel(side, trigger),
                        action = settings.actionFor(side, trigger, handleId),
                        modeLabel = triggerModeLabel(settings.slotTriggerMode(side, trigger, handleId)),
                        onClick = { pickingTrigger = trigger },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
    }

    AnimatedFullScreenOverlay(visible = pickingTrigger != null) {
        pickingTrigger?.let { trigger ->
            SlotConfigOverlay(
                side = side,
                settings = settings,
                trigger = trigger,
                currentAction = settings.actionFor(side, trigger, handleId),
                currentMode = settings.slotTriggerMode(side, trigger, handleId),
                onDismiss = { pickingTrigger = null },
                onConfirm = { action, mode ->
                    onSlotConfigChange(handleId, trigger, action, mode)
                    pickingTrigger = null
                },
            )
        }
    }

    AnimatedFullScreenOverlay(visible = pickingDefaultMode) {
        TriggerModePickerScreen(
            title = stringResource(R.string.default_trigger_mode),
            current = settings.defaultTriggerModeFor(side),
            action = GestureAction.None,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            includeDefaultOption = false,
            onBack = { pickingDefaultMode = false },
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
    action: GestureAction,
    modeLabel: String,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = {
            Icon(
                imageVector = gestureActionIcon(action),
                contentDescription = label,
            )
        },
        title = label,
        subtitle = "${gestureActionLabel(action)} · $modeLabel",
        onClick = onClick,
    )
}

@Composable
private fun SlotConfigOverlay(
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

    AnimatedFullScreenOverlay(visible = pickingAction) {
        GestureActionPickerScreen(
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

    AnimatedFullScreenOverlay(visible = pickingMode) {
        TriggerModePickerScreen(
            title = stringResource(R.string.slot_pick_trigger_mode),
            current = selectedMode,
            action = selectedAction,
            trigger = trigger,
            sideDefaultMode = sideDefaultMode,
            includeDefaultOption = true,
            onBack = { pickingMode = false },
            onSelect = { mode ->
                selectedMode = mode
                pickingMode = false
            },
        )
    }

    if (!pickingAction && !pickingMode) {
        SettingsFormScreen(
            title = triggerLabel(side, trigger),
            onBack = onDismiss,
            onConfirm = { onConfirm(selectedAction, selectedMode) },
        ) {
            SettingsSectionTitle(stringResource(R.string.slot_action_type))
            SettingsCard {
                SettingNavigationRow(
                    icon = { label ->
                        Icon(
                            imageVector = gestureActionIcon(selectedAction),
                            contentDescription = label,
                        )
                    },
                    title = gestureActionLabel(selectedAction),
                    subtitle = stringResource(R.string.slot_pick_action),
                    onClick = { pickingAction = true },
                )
            }
            SettingsSectionTitle(stringResource(R.string.slot_trigger_mode))
            SettingsCard {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.SwipeRight, contentDescription = label) },
                    title = slotTriggerModeTitle(selectedMode, sideDefaultMode),
                    subtitle = slotTriggerModeSubtitle(selectedMode, sideDefaultMode),
                    onClick = { pickingMode = true },
                )
            }
        }
    }
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
private fun TriggerModePickerScreen(
    title: String,
    current: GestureTriggerMode,
    action: GestureAction,
    trigger: GestureTriggerType,
    sideDefaultMode: GestureTriggerMode = GestureTriggerMode.ON_RELEASE,
    includeDefaultOption: Boolean,
    onBack: () -> Unit,
    onSelect: (GestureTriggerMode) -> Unit,
) {
    val modes = if (includeDefaultOption) {
        GestureTriggerMode.configurableEntries
    } else {
        GestureTriggerMode.configurableEntries.filter { it != GestureTriggerMode.DEFAULT }
    }
    SettingsRadioPickerScreen(
        title = title,
        onBack = onBack,
    ) {
        modes.forEach { mode ->
            val enabled = mode == GestureTriggerMode.DEFAULT || mode.supportsAction(action, trigger)
            val modeTitle = when (mode) {
                GestureTriggerMode.DEFAULT -> stringResource(R.string.trigger_mode_default)
                else -> triggerModeLabel(mode, includeDefault = false)
            }
            val subtitle = when (mode) {
                GestureTriggerMode.DEFAULT -> stringResource(
                    R.string.trigger_mode_default_slot_desc,
                    triggerModeLabel(sideDefaultMode, includeDefault = false),
                )
                else -> triggerModeDescription(mode)
            }
            SettingRadioRow(
                title = modeTitle,
                subtitle = subtitle,
                selected = current == mode,
                enabled = enabled,
                onClick = { if (enabled) onSelect(mode) },
            )
        }
    }
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
        labels.add(gestureActionLabel(action))
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
fun SideGesturesEntryCard(onOpenLeft: () -> Unit, onOpenRight: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.SwipeRight, contentDescription = label) },
        title = stringResource(R.string.side_gestures_entry_left),
        subtitle = stringResource(R.string.side_gestures_entry_desc),
        onClick = onOpenLeft,
    )
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.SwipeRight, contentDescription = label) },
        title = stringResource(R.string.side_gestures_entry_right),
        subtitle = stringResource(R.string.side_gestures_entry_desc),
        onClick = onOpenRight,
    )
}
