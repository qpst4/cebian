package com.slideindex.app.ui



import androidx.compose.foundation.layout.size

import com.slideindex.app.ui.settings.components.MiuixNavigationRow

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.slideindex.app.R

import com.slideindex.app.gesture.GestureAction

import com.slideindex.app.gesture.GestureTriggerMode

import com.slideindex.app.gesture.GestureTriggerType

import com.slideindex.app.overlay.PanelSide

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.settings.actionFor

import com.slideindex.app.settings.defaultTriggerModeFor

import com.slideindex.app.settings.displayTriggerMode

import com.slideindex.app.settings.gestureConfigSide

import com.slideindex.app.gesture.supportsAction

import com.slideindex.app.ui.miuix.groupedCardItems

import com.slideindex.app.ui.settings.components.settingsCardScopeItem

import com.slideindex.app.ui.settings.components.settingsLazySmallTitle



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun SideGestureSlotConfigScreen(

    side: PanelSide,

    handleId: String,

    trigger: GestureTriggerType,

    settings: AppSettings,

    onBack: () -> Unit,

    onOpenActionPick: () -> Unit,

    onOpenModePick: () -> Unit,

    onOpenShellCommand: (String) -> Unit,

    onOpenQuickLauncherPanel: (String) -> Unit = {},

) {

    val configSide = settings.gestureConfigSide(side, handleId)

    val selectedAction = settings.actionFor(configSide, trigger, handleId)

    val selectedMode = settings.displayTriggerMode(configSide, trigger, handleId)

    val sideDefaultMode = settings.defaultTriggerModeFor(configSide)



    val actionSectionTitle = stringResource(R.string.slot_action_type)

    val quickLauncherSectionTitle = stringResource(R.string.quick_launcher_panel_pick_title)

    val shellCommandSectionTitle = stringResource(R.string.gesture_shell_command_config_title)

    val triggerModeSectionTitle = stringResource(R.string.slot_trigger_mode)



    SettingsScreenScaffold(

        title = triggerLabel(side, trigger),

        onBack = onBack,

    ) {

        settingsLazySmallTitle(

            key = "slot-action-section",

            title = actionSectionTitle,

            sectionTop = true,

        )

        groupedCardItems(

            keyPrefix = "side-gesture-action",

            items = buildList {

                add(

                    settingsCardScopeItem("slot-action") {

                        SettingNavigationRow(

                            icon = { label ->

                                Icon(

                                    imageVector = gestureActionImageVector(selectedAction, outlined = true),

                                    contentDescription = label,

                                    modifier = Modifier.size(24.dp),

                                    tint = MaterialTheme.colorScheme.onSurface,

                                )

                            },

                            title = gestureActionLabel(selectedAction, settings),

                            subtitle = stringResource(R.string.slot_pick_action),

                            onClick = onOpenActionPick,

                        )

                    },

                )

            },

        )



        if (selectedAction is GestureAction.QuickLauncher) {

            settingsLazySmallTitle(

                key = "slot-quick-launcher-section",

                title = quickLauncherSectionTitle,

                sectionTop = true,

            )

            groupedCardItems(

                keyPrefix = "side-gesture-quick-launcher",

                items = buildList {

                    add(

                        settingsCardScopeItem("slot-quick-launcher") {

                            SettingNavigationRow(

                                icon = { label ->

                                    Icon(

                                        imageVector = gestureActionImageVector(selectedAction, outlined = true),

                                        contentDescription = label,

                                        modifier = Modifier.size(24.dp),

                                        tint = MaterialTheme.colorScheme.onSurface,

                                    )

                                },

                                title = quickLauncherPanelLabel(settings, selectedAction.panelId),

                                subtitle = stringResource(R.string.quick_launcher_panel_pick_title),

                                onClick = { onOpenQuickLauncherPanel(selectedAction.panelId) },

                            )

                        },

                    )

                },

            )

        }



        if (selectedAction is GestureAction.ExecuteShellCommand) {

            settingsLazySmallTitle(

                key = "slot-shell-command-section",

                title = shellCommandSectionTitle,

                sectionTop = true,

            )

            groupedCardItems(

                keyPrefix = "side-gesture-shell-command",

                items = buildList {

                    add(

                        settingsCardScopeItem("slot-shell-command") {

                            SettingNavigationRow(

                                icon = { label ->

                                    Icon(

                                        imageVector = gestureActionImageVector(selectedAction, outlined = true),

                                        contentDescription = label,

                                        modifier = Modifier.size(24.dp),

                                        tint = MaterialTheme.colorScheme.onSurface,

                                    )

                                },

                                title = gestureExecuteShellCommandPreview(selectedAction.command),

                                subtitle = stringResource(R.string.gesture_shell_command_config_title),

                                onClick = { onOpenShellCommand(selectedAction.command) },

                            )

                        },

                    )

                },

            )

        }



        settingsLazySmallTitle(

            key = "slot-trigger-mode-section",

            title = triggerModeSectionTitle,

            sectionTop = true,

        )

        groupedCardItems(

            keyPrefix = "side-gesture-trigger-mode",

            items = buildList {

                add(

                    settingsCardScopeItem("slot-trigger-mode") {

                        MiuixNavigationRow(

                            title = slotTriggerModeTitle(selectedMode, sideDefaultMode),

                            summary = slotTriggerModeSubtitle(selectedMode, sideDefaultMode),

                            onClick = onOpenModePick,

                        )

                    },

                )

            },

        )

    }

}



@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun SideGestureTriggerModePickerScreen(

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

    val modeItems = buildList {

        modes.forEach { mode ->

            add(
                settingsCardScopeItem("mode-${mode.name}") {
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
                },
            )

        }

    }

    SettingsRadioPickerScreen(

        title = title,

        onBack = onBack,

        items = modeItems,

    )

}



@Composable

fun slotTriggerModeTitle(mode: GestureTriggerMode, sideDefaultMode: GestureTriggerMode): String =

    when (mode) {

        GestureTriggerMode.DEFAULT -> stringResource(R.string.trigger_mode_default)

        else -> triggerModeLabel(mode, includeDefault = false)

    }



@Composable

fun slotTriggerModeSubtitle(

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

fun triggerModeLabel(mode: GestureTriggerMode, includeDefault: Boolean = true): String =

    when (mode) {

        GestureTriggerMode.DEFAULT ->

            if (includeDefault) stringResource(R.string.trigger_mode_default) else stringResource(R.string.trigger_mode_on_release)

        GestureTriggerMode.ON_RELEASE -> stringResource(R.string.trigger_mode_on_release)

        GestureTriggerMode.CONTINUOUS -> stringResource(R.string.trigger_mode_continuous)

        GestureTriggerMode.IMMEDIATE -> stringResource(R.string.trigger_mode_immediate)

    }



@Composable

fun triggerModeDescription(mode: GestureTriggerMode): String = when (mode) {

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

fun triggerLabel(side: PanelSide, trigger: GestureTriggerType): String = stringResource(

    when (trigger) {

        GestureTriggerType.SHORT_SWIPE_IN, GestureTriggerType.LONG_SWIPE_IN -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_in_left

            PanelSide.RIGHT -> R.string.gesture_swipe_in_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_in_bottom

            PanelSide.TOP -> R.string.gesture_swipe_in_top

        }

        GestureTriggerType.SHORT_SWIPE_UP_RIGHT, GestureTriggerType.LONG_SWIPE_UP_RIGHT -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_up_right_on_left

            PanelSide.RIGHT -> R.string.gesture_swipe_up_left_on_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_up_left_on_bottom

            PanelSide.TOP -> R.string.gesture_swipe_up_left_on_top

        }

        GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT, GestureTriggerType.LONG_SWIPE_DOWN_RIGHT -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_down_right_on_left

            PanelSide.RIGHT -> R.string.gesture_swipe_down_left_on_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_down_right_on_bottom

            PanelSide.TOP -> R.string.gesture_swipe_down_right_on_top

        }

        GestureTriggerType.SHORT_SWIPE_IN_UP, GestureTriggerType.LONG_SWIPE_IN_UP -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_in_up_left

            PanelSide.RIGHT -> R.string.gesture_swipe_in_up_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_in_up_bottom

            PanelSide.TOP -> R.string.gesture_swipe_in_up_top

        }

        GestureTriggerType.SHORT_SWIPE_IN_DOWN, GestureTriggerType.LONG_SWIPE_IN_DOWN -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_in_down_left

            PanelSide.RIGHT -> R.string.gesture_swipe_in_down_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_in_down_bottom

            PanelSide.TOP -> R.string.gesture_swipe_in_down_top

        }

        GestureTriggerType.SHORT_SWIPE_IN_AND_BACK -> when (side) {

            PanelSide.LEFT -> R.string.gesture_swipe_in_and_back_left

            PanelSide.RIGHT -> R.string.gesture_swipe_in_and_back_right

            PanelSide.BOTTOM -> R.string.gesture_swipe_in_and_back_bottom

            PanelSide.TOP -> R.string.gesture_swipe_in_and_back_top

        }

        GestureTriggerType.SHORT_SWIPE_UP -> when (side) {

            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_short_swipe_left

            else -> R.string.gesture_short_swipe_up

        }

        GestureTriggerType.SHORT_SWIPE_DOWN -> when (side) {

            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_short_swipe_right

            else -> R.string.gesture_short_swipe_down

        }

        GestureTriggerType.SHORT_LONG_PRESS -> R.string.gesture_short_long_press

        GestureTriggerType.SHORT_SINGLE_TAP -> R.string.gesture_short_single_tap

        GestureTriggerType.LONG_SWIPE_UP -> when (side) {

            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_long_swipe_left

            else -> R.string.gesture_long_swipe_up

        }

        GestureTriggerType.LONG_SWIPE_DOWN -> when (side) {

            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_long_swipe_right

            else -> R.string.gesture_long_swipe_down

        }

        GestureTriggerType.LONG_LONG_PRESS -> R.string.gesture_long_long_press

        GestureTriggerType.LONG_SINGLE_TAP -> R.string.gesture_long_single_tap

        GestureTriggerType.SHORT_SWIPE_IN_HOVER -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_in_hover_left
            PanelSide.RIGHT -> R.string.gesture_swipe_in_hover_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_in_hover_bottom
            PanelSide.TOP -> R.string.gesture_swipe_in_hover_top
        }

        GestureTriggerType.SHORT_SWIPE_UP_RIGHT_HOVER -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_up_right_hover_on_left
            PanelSide.RIGHT -> R.string.gesture_swipe_up_left_hover_on_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_up_left_hover_on_bottom
            PanelSide.TOP -> R.string.gesture_swipe_up_left_hover_on_top
        }

        GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT_HOVER -> when (side) {
            PanelSide.LEFT -> R.string.gesture_swipe_down_right_hover_on_left
            PanelSide.RIGHT -> R.string.gesture_swipe_down_left_hover_on_right
            PanelSide.BOTTOM -> R.string.gesture_swipe_down_right_hover_on_bottom
            PanelSide.TOP -> R.string.gesture_swipe_down_right_hover_on_top
        }

        GestureTriggerType.SHORT_SWIPE_UP_HOVER -> when (side) {
            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_short_swipe_left_hover
            else -> R.string.gesture_short_swipe_up_hover
        }

        GestureTriggerType.SHORT_SWIPE_DOWN_HOVER -> when (side) {
            PanelSide.BOTTOM, PanelSide.TOP -> R.string.gesture_short_swipe_right_hover
            else -> R.string.gesture_short_swipe_down_hover
        }

    },

)


