package com.slideindex.app.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CornerGestureSlotsSettingsScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onUnifiedSlotsChange: (Boolean) -> Unit,
    onOpenInnerZoneActionPick: () -> Unit,
    onOpenLeftSlotActionPick: (Int) -> Unit,
    onOpenRightSlotActionPick: (Int) -> Unit,
) {
    val corner = settings.cornerGestureSettings
    val slotsEnabled = serviceEnabled && corner.enabled
    val slotsEnabledLeft = slotsEnabled && corner.leftEnabled
    val slotsEnabledRight = slotsEnabled && corner.rightEnabled

    val slotsSectionTitle = stringResource(R.string.corner_gesture_slots_section)
    val launchPolicyHint = stringResource(R.string.corner_gesture_launch_policy_hint)
    val leftSlotsSectionTitle = stringResource(R.string.corner_gesture_left_slots_section)
    val rightSlotsSectionTitle = stringResource(R.string.corner_gesture_right_slots_section)

    val layerTitles = listOf(
        cornerLayerTitle(0),
        cornerLayerTitle(1),
        cornerLayerTitle(2),
    )
    val unifiedLayer0 = cornerLayerCardItems(0, corner.leftSlots, slotsEnabled, onOpenLeftSlotActionPick)
    val unifiedLayer1 = cornerLayerCardItems(1, corner.leftSlots, slotsEnabled, onOpenLeftSlotActionPick)
    val unifiedLayer2 = cornerLayerCardItems(2, corner.leftSlots, slotsEnabled, onOpenLeftSlotActionPick)
    val leftLayer0 = cornerLayerCardItems(0, corner.leftSlots, slotsEnabledLeft, onOpenLeftSlotActionPick)
    val leftLayer1 = cornerLayerCardItems(1, corner.leftSlots, slotsEnabledLeft, onOpenLeftSlotActionPick)
    val leftLayer2 = cornerLayerCardItems(2, corner.leftSlots, slotsEnabledLeft, onOpenLeftSlotActionPick)
    val rightLayer0 = cornerLayerCardItems(0, corner.rightSlots, slotsEnabledRight, onOpenRightSlotActionPick)
    val rightLayer1 = cornerLayerCardItems(1, corner.rightSlots, slotsEnabledRight, onOpenRightSlotActionPick)
    val rightLayer2 = cornerLayerCardItems(2, corner.rightSlots, slotsEnabledRight, onOpenRightSlotActionPick)

    SettingsScreenScaffold(
        title = stringResource(R.string.corner_gesture_slots_section),
        subtitle = stringResource(R.string.corner_gesture_slots_entry_desc),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "corner-slots-section",
            title = slotsSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(
            key = "corner-launch-policy-hint",
            text = launchPolicyHint,
        )
        groupedCardItems(
            keyPrefix = "corner-unified-slots",
            items = buildList {
                add(
                    settingsCardScopeItem("unified-slots") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_unified_slots),
                            subtitle = stringResource(R.string.corner_gesture_unified_slots_desc),
                            checked = corner.unifiedSlots,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onUnifiedSlotsChange,
                        )
                    },
                )
            },
        )
        if (corner.unifiedSlots) {
            emitCornerLayerSlots(
                keyPrefix = "corner-unified",
                layerTitles = layerTitles,
                layerItems = listOf(unifiedLayer0, unifiedLayer1, unifiedLayer2),
            )
        } else {
            settingsLazySmallTitle(
                key = "corner-left-slots-section",
                title = leftSlotsSectionTitle,
                sectionTop = true,
            )
            emitCornerLayerSlots(
                keyPrefix = "corner-left",
                layerTitles = layerTitles,
                layerItems = listOf(leftLayer0, leftLayer1, leftLayer2),
            )
            settingsLazySmallTitle(
                key = "corner-right-slots-section",
                title = rightSlotsSectionTitle,
                sectionTop = true,
            )
            emitCornerLayerSlots(
                keyPrefix = "corner-right",
                layerTitles = layerTitles,
                layerItems = listOf(rightLayer0, rightLayer1, rightLayer2),
            )
        }
        groupedCardItems(
            keyPrefix = "corner-inner-zone",
            items = buildList {
                add(
                    settingsCardScopeItem("inner-zone-action") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    imageVector = gestureActionIcon(corner.innerZoneAction, outlined = true),
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.corner_gesture_inner_zone_action),
                            subtitle = gestureActionLabel(corner.innerZoneAction),
                            enabled = serviceEnabled && corner.enabled,
                            onClick = onOpenInnerZoneActionPick,
                        )
                    },
                )
            },
        )
    }
}

@Composable
private fun cornerLayerTitle(layer: Int): String = when (layer) {
    0 -> stringResource(R.string.corner_gesture_layer_inner)
    1 -> stringResource(R.string.corner_gesture_layer_middle)
    else -> stringResource(R.string.corner_gesture_layer_outer)
}

@Composable
private fun cornerLayerCardItems(
    layer: Int,
    slots: List<GestureAction>,
    enabled: Boolean,
    onOpenSlotActionPick: (Int) -> Unit,
): List<CardItem> {
    val start = CornerRadialMenuCodec.layerStartIndex(layer)
    val count = CornerRadialMenuCodec.slotCountInLayer(layer)
    return buildList {
        repeat(count) { offset ->
            val index = start + offset
            val action = slots.getOrElse(index) { GestureAction.None }
            add(
                settingsCardScopeItem("corner-slot-$index") {
                    SettingNavigationRow(
                        icon = { label ->
                            Icon(
                                imageVector = gestureActionIcon(action, outlined = true),
                                contentDescription = label,
                            )
                        },
                        title = stringResource(R.string.corner_gesture_slot_title, index + 1),
                        subtitle = if (action is GestureAction.None) {
                            stringResource(R.string.corner_gesture_slot_empty)
                        } else {
                            gestureActionLabel(action)
                        },
                        enabled = enabled,
                        onClick = { onOpenSlotActionPick(index) },
                    )
                },
            )
        }
    }
}

private fun LazyListScope.emitCornerLayerSlots(
    keyPrefix: String,
    layerTitles: List<String>,
    layerItems: List<List<CardItem>>,
) {
    layerTitles.forEachIndexed { index, title ->
        settingsLazySmallTitle(
            key = "$keyPrefix-layer-$index-title",
            title = title,
            sectionTop = true,
        )
        groupedCardItems("$keyPrefix-layer-$index", layerItems[index])
    }
}
