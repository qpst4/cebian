package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.isShellActivityShortcut
import com.slideindex.app.launcher.showsShellActivityShortcutBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.overlay.ShellCommandBadgeOverlay
import com.slideindex.app.overlay.ShortcutBadgeOverlay
import com.slideindex.app.overlay.corner.CornerSlotIconBitmap
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FingertipRingCodec
import com.slideindex.app.ui.Md3PickerLaunchShortcutLeading
import com.slideindex.app.ui.Md3PickerPackageLeading
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FingertipRingSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSlotCountChange: (Int) -> Unit,
    onOrbitRadiusChange: (Float) -> Unit,
    onIconSizeChange: (Float) -> Unit,
    onOpenSlotActionPick: (Int) -> Unit,
    onOpenShellCommand: (Int, String) -> Unit,
    onOpenSwipeConfig: (Int) -> Unit,
) {
    val slotCount = FingertipRingCodec.effectiveSlotCount(settings.fingertipRingSlotCount)
    val activeSlots = FingertipRingCodec.activeSlots(settings.fingertipRing)
    val orbitRadiusPx = FingertipRingCodec.effectiveOrbitRadiusPx(settings.fingertipRingOrbitRadiusPx)
    val iconSizePx = FingertipRingCodec.effectiveIconSizePx(settings.fingertipRingIconSizePx)
    val layoutSectionTitle = stringResource(R.string.fingertip_ring_layout_section)
    val slotsSectionTitle = stringResource(R.string.fingertip_ring_slots_section)
    val orbitRadiusTitle = stringResource(R.string.fingertip_ring_orbit_radius)
    val iconSizeTitle = stringResource(R.string.fingertip_ring_icon_size)
    val orbitRadiusLabel = stringResource(R.string.fingertip_ring_size_px_value, orbitRadiusPx.roundToInt())
    val iconSizeLabel = stringResource(R.string.fingertip_ring_size_px_value, iconSizePx.roundToInt())
    val slotCountTitle = stringResource(R.string.fingertip_ring_slot_count)
    val slotCountLabel = stringResource(R.string.fingertip_ring_slot_count_value, slotCount)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.fingertip_ring_settings_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "fingertip_ring_layout_title",
            title = layoutSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "fingertip-ring-layout",
            items = listOf(
                settingsCardScopeItem("orbit-radius") {
                    SettingsSliderRow(
                        title = orbitRadiusTitle,
                        value = orbitRadiusPx,
                        valueRange = FingertipRingCodec.MIN_ORBIT_RADIUS_PX..FingertipRingCodec.MAX_ORBIT_RADIUS_PX,
                        steps = ((FingertipRingCodec.MAX_ORBIT_RADIUS_PX - FingertipRingCodec.MIN_ORBIT_RADIUS_PX) / 8f).roundToInt() - 1,
                        enabled = true,
                        label = orbitRadiusLabel,
                        onValueChange = onOrbitRadiusChange,
                    )
                },
                settingsCardScopeItem("icon-size") {
                    SettingsSliderRow(
                        title = iconSizeTitle,
                        value = iconSizePx,
                        valueRange = FingertipRingCodec.MIN_ICON_SIZE_PX..FingertipRingCodec.MAX_ICON_SIZE_PX,
                        steps = ((FingertipRingCodec.MAX_ICON_SIZE_PX - FingertipRingCodec.MIN_ICON_SIZE_PX) / 4f).roundToInt() - 1,
                        enabled = true,
                        label = iconSizeLabel,
                        onValueChange = onIconSizeChange,
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "fingertip_ring_slots_title",
            title = slotsSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "fingertip-ring-slots",
            items = buildList {
                add(
                    settingsCardScopeItem("slot-count") {
                        SettingsSliderRow(
                            title = slotCountTitle,
                            value = slotCount.toFloat(),
                            valueRange = FingertipRingCodec.MIN_SLOT_COUNT.toFloat()..FingertipRingCodec.MAX_SLOT_COUNT.toFloat(),
                            steps = FingertipRingCodec.MAX_SLOT_COUNT - FingertipRingCodec.MIN_SLOT_COUNT - 1,
                            enabled = true,
                            label = slotCountLabel,
                            onValueChange = { onSlotCountChange(it.roundToInt()) },
                        )
                    },
                )
                repeat(slotCount) { index ->
                    val action = activeSlots.getOrElse(index) { GestureAction.None }
                    add(
                        settingsCardScopeItem("slot-$index") {
                            SettingNavigationRow(
                                icon = { label ->
                                    FingertipRingSlotActionIcon(
                                        action = action,
                                        settings = settings,
                                        contentDescription = label,
                                    )
                                },
                                title = stringResource(R.string.fingertip_ring_slot_title, index + 1),
                                subtitle = gestureActionLabel(action),
                                onClick = { onOpenSlotActionPick(index) },
                                trailingContent = when (action) {
                                    is GestureAction.SimulatePointerSwipe,
                                    is GestureAction.ExecuteShellCommand,
                                    -> {
                                        {
                                            IconButton(
                                                onClick = {
                                                    when (val current = action) {
                                                        is GestureAction.SimulatePointerSwipe ->
                                                            onOpenSwipeConfig(index)
                                                        is GestureAction.ExecuteShellCommand ->
                                                            onOpenShellCommand(index, current.command)
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Settings,
                                                    contentDescription = stringResource(R.string.cd_radial_menu_settings),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    else -> null
                                },
                            )
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun FingertipRingSlotActionIcon(
    action: GestureAction,
    settings: AppSettings,
    contentDescription: String?,
) {
    when (action) {
        is GestureAction.LaunchApp -> {
            Md3PickerPackageLeading(
                packageName = action.packageName,
                contentDescription = contentDescription,
            )
        }
        is GestureAction.LaunchShortcut -> {
            Md3PickerLaunchShortcutLeading(
                action = action,
                activityShortcuts = settings.activityShortcuts,
            )
        }
        is GestureAction.ExecuteShellCommand -> {
            FingertipRingShellSlotIcon(
                action = action,
                settings = settings,
                contentDescription = contentDescription,
            )
        }
        else -> {
            Icon(
                imageVector = gestureActionIcon(action),
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun FingertipRingShellSlotIcon(
    action: GestureAction.ExecuteShellCommand,
    settings: AppSettings,
    contentDescription: String?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSize = 28.dp
    val iconPx = with(density) { iconSize.roundToPx() }.coerceAtLeast(12)
    val bitmap by produceState<android.graphics.Bitmap?>(null, action, settings.shellCommands) {
        value = CornerSlotIconBitmap.get(
            context = context,
            action = action,
            sizePx = iconPx,
            tintArgb = android.graphics.Color.WHITE,
            activityShortcuts = settings.activityShortcuts,
            shellCommands = settings.shellCommands,
        )
    }
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        } else {
            Icon(
                imageVector = gestureActionIcon(action),
                contentDescription = contentDescription,
            )
        }
        if (action.showsShellCommandBadge(settings.shellCommands)) {
            ShellCommandBadgeOverlay(iconSize = iconSize)
        }
    }
}
