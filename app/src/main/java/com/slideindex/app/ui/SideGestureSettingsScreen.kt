package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.settings.oppositeGesturesSyncedForHandle
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.defaultTriggerModeFor
import com.slideindex.app.settings.displayTriggerMode
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingsCardScope

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
    onOpenDefaultModePick: () -> Unit,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
    onAlignOppositeGesturesChange: (Boolean) -> Unit = {},
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
        PanelSide.BOTTOM -> stringResource(R.string.side_gestures_bottom_title)
        PanelSide.TOP -> stringResource(R.string.side_gestures_top_title)
    }
    val title = if (pairCount > 1) "$baseTitle · $pairIndex" else baseTitle
    val gesturesSynced = settings.oppositeGesturesSyncedForHandle(handleId)
    val subtitle = if (gesturesSynced && side.isHorizontalEdge) {
        stringResource(R.string.side_gestures_shared_with_opposite)
    } else {
        stringResource(R.string.side_gestures_desc)
    }

    TriggerHandlePreviewLifecycle(
        enabled = serviceEnabled,
        side = side,
        handleId = handleId,
        onPreviewStart = { _, _ -> onPreviewStart() },
        onPreviewStop = onPreviewStop,
    )

    SettingsScreenScaffold(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
    ) {
        val slotSide = if (gesturesSynced && side.isHorizontalEdge) PanelSide.LEFT else side
        val showAlignGesturesSwitch = side.isHorizontalEdge &&
            settings.triggerHandle(PanelSide.LEFT, handleId) != null &&
            settings.triggerHandle(PanelSide.RIGHT, handleId) != null
        MiuixSmallTitle(stringResource(R.string.side_gestures_behavior_section), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Brush, contentDescription = label) },
                title = stringResource(R.string.trigger_design_entry),
                subtitle = triggerDesignSummary(selectedHandle.design),
                onClick = onOpenDesignSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.Outlined.Animation, contentDescription = label) },
                title = stringResource(R.string.trigger_appearance_entry),
                subtitle = triggerAppearanceSummary(settings, side, handleId),
                onClick = onOpenAppearanceSettings,
            )
            SettingNavigationRow(
                icon = { label -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = label) },
                title = stringResource(R.string.default_trigger_mode),
                subtitle = triggerModeLabel(settings.defaultTriggerModeFor(slotSide), includeDefault = false),
                onClick = onOpenDefaultModePick,
            )
            if (showAlignGesturesSwitch) {
                SettingSwitchRow(
                    title = stringResource(R.string.align_opposite_gestures),
                    subtitle = stringResource(R.string.align_opposite_gestures_desc),
                    checked = selectedHandle.alignOppositeGestures,
                    enabled = serviceEnabled,
                    onCheckedChange = onAlignOppositeGesturesChange,
                )
            }
        }

        MiuixSmallTitle(stringResource(R.string.side_gestures_short_distance), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            GestureTriggerType.shortDistanceEntries().forEach { trigger ->
                GestureSlotRow(
                    side = side,
                    trigger = trigger,
                    label = triggerLabel(side, trigger),
                    action = settings.actionFor(slotSide, trigger, handleId),
                    modeLabel = triggerModeLabel(settings.displayTriggerMode(slotSide, trigger, handleId)),
                    onClick = { onOpenSlotConfig(trigger) },
                )
            }
        }
        MiuixSmallTitle(stringResource(R.string.side_gestures_press_tap), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            GestureTriggerType.pressTapEntries().forEach { trigger ->
                GestureSlotRow(
                    side = side,
                    trigger = trigger,
                    label = triggerLabel(side, trigger),
                    action = settings.actionFor(slotSide, trigger, handleId),
                    modeLabel = triggerModeLabel(settings.displayTriggerMode(slotSide, trigger, handleId)),
                    onClick = { onOpenSlotConfig(trigger) },
                )
            }
        }
        MiuixSmallTitle(stringResource(R.string.side_gestures_long_distance), modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop))
        SettingsCard {
            GestureTriggerType.longDistanceEntries().forEach { trigger ->
                GestureSlotRow(
                    side = side,
                    trigger = trigger,
                    label = triggerLabel(side, trigger),
                    action = settings.actionFor(slotSide, trigger, handleId),
                    modeLabel = triggerModeLabel(settings.displayTriggerMode(slotSide, trigger, handleId)),
                    onClick = { onOpenSlotConfig(trigger) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun GestureSlotRow(
    side: PanelSide,
    trigger: GestureTriggerType,
    label: String,
    action: GestureAction,
    modeLabel: String,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { contentDescription ->
            GestureTriggerIcon(
                side = side,
                trigger = trigger,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        },
        title = label,
        subtitle = "${gestureActionSettingSubtitle(action)} · $modeLabel",
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.SideGesturesEntryCard(onOpenLeft: () -> Unit, onOpenRight: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = label) },
        title = stringResource(R.string.side_gestures_entry_left),
        subtitle = stringResource(R.string.side_gestures_entry_desc),
        onClick = onOpenLeft,
    )
    SettingNavigationRow(
        icon = { label -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = label) },
        title = stringResource(R.string.side_gestures_entry_right),
        subtitle = stringResource(R.string.side_gestures_entry_desc),
        onClick = onOpenRight,
    )
}
