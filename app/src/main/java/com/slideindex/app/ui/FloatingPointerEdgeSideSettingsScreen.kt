package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerEdgeActionSlot
import com.slideindex.app.settings.FloatingPointerEdgeActionsCodec
import com.slideindex.app.settings.FloatingPointerEdgeSide
import com.slideindex.app.ui.settings.components.LazySettingsItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerEdgeSideSettingsScreen(
    side: FloatingPointerEdgeSide,
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenActionPick: (Int) -> Unit,
    onOpenShellCommand: (Int, String) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (Int) -> Unit,
) {
    val bar = settings.floatingPointerEdgeActionsConfig.bar(side)
    val slots = bar.layoutSlots()

    SettingsScreenScaffold(
        title = edgeSideTitle(side),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.floating_pointer_edge_side_enabled_desc))

        SettingsCard {
            SettingSwitchRow(
                title = edgeSideTitle(side),
                subtitle = edgeSideSummary(slots.size, bar.enabled),
                icon = { label -> Icon(edgeSideIcon(side), contentDescription = label) },
                checked = bar.enabled,
                enabled = true,
                onCheckedChange = onEnabledChange,
            )
        }

        MiuixSmallTitle(
            stringResource(R.string.floating_pointer_edge_section_zones_count, slots.size),
            modifier = Modifier.fillMaxWidth().padding(top = MiuixSmallTitleSectionTop),
        )
        LazySettingsItem(key = "edge-zones-${side.name}-${slots.size}-${slots.map { it.action }}") {
            for (index in slots.indices) {
                val slot = slots[index]
                EdgeZoneSettingsCard(
                    side = side,
                    index = index,
                    slot = slot,
                    canRemove = slots.size > 1,
                    onPickAction = { onOpenActionPick(index) },
                    onOpenShellCommand = { command -> onOpenShellCommand(index, command) },
                    onRemove = { onRemoveSlot(index) },
                )
            }
        }
        if (slots.size < FloatingPointerEdgeActionsCodec.MAX_SLOTS_PER_EDGE) {
            SettingsCard(keyPrefix = "edge-add-zone-${side.name}") {
                SettingNavigationRow(
                    icon = { label -> Icon(Icons.Default.Add, contentDescription = label) },
                    title = stringResource(R.string.floating_pointer_edge_add_zone),
                    subtitle = stringResource(R.string.floating_pointer_edge_add_zone_desc),
                    onClick = onAddSlot,
                )
            }
        }
    }
}

@Composable
private fun EdgeZoneSettingsCard(
    side: FloatingPointerEdgeSide,
    index: Int,
    slot: FloatingPointerEdgeActionSlot,
    canRemove: Boolean,
    onPickAction: () -> Unit,
    onOpenShellCommand: (String) -> Unit,
    onRemove: () -> Unit,
) {
    SettingsCard(keyPrefix = "edge-zone-${side.name}-$index") {
        SettingNavigationRow(
            icon = { label -> Icon(gestureActionIcon(slot.action), contentDescription = label) },
            title = stringResource(R.string.floating_pointer_edge_zone_title, index + 1),
            subtitle = gestureActionLabel(slot.action),
            onClick = onPickAction,
            trailingContent = if (canRemove) {
                {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.floating_pointer_edge_remove_zone),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                null
            },
        )
        if (slot.action is GestureAction.ExecuteShellCommand) {
            val shellAction = slot.action as GestureAction.ExecuteShellCommand
            SettingNavigationRow(
                icon = { label -> Icon(gestureActionIcon(shellAction), contentDescription = label) },
                title = gestureExecuteShellCommandPreview(shellAction.command),
                subtitle = stringResource(R.string.gesture_shell_command_config_title),
                onClick = { onOpenShellCommand(shellAction.command) },
            )
        }
    }
}
