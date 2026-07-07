package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.overlay.FloatingPointerRadialMenuPreview
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerRadialMenuCodec
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import kotlin.math.roundToInt

private enum class RadialMenuTab { Settings, Functions, Design }

private enum class RadialColorTarget {
    Outer,
    Inner,
    Divider,
    Icon,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerRadialMenuSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLongPressMsChange: (Int) -> Unit,
    onSlotActionChange: (Int, GestureAction) -> Unit,
    onOuterDiameterChange: (Float) -> Unit,
    onInnerDiameterChange: (Float) -> Unit,
    onOuterColorChange: (Int) -> Unit,
    onInnerColorChange: (Int) -> Unit,
    onDividerThicknessChange: (Float) -> Unit,
    onDividerColorChange: (Int) -> Unit,
    onIconSizeFractionChange: (Float) -> Unit,
    onIconColorChange: (Int) -> Unit,
    onResetDesignDefaults: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(RadialMenuTab.Settings) }
    var pickingSlot by remember { mutableIntStateOf(-1) }
    var swipeConfigSlot by remember { mutableIntStateOf(-1) }
    var swipeConfigDraft by remember { mutableStateOf(PointerSwipeConfig.DEFAULT) }
    var colorTarget by remember { mutableStateOf<RadialColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }

    if (swipeConfigSlot >= 0) {
        PointerSwipeConfigDialog(
            initialConfig = swipeConfigDraft,
            onDismissRequest = { swipeConfigSlot = -1 },
            onConfirm = { config ->
                onSlotActionChange(swipeConfigSlot, GestureAction.SimulatePointerSwipe(config))
                swipeConfigSlot = -1
            },
        )
    }

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                when (colorTarget) {
                    RadialColorTarget.Outer -> onOuterColorChange(color)
                    RadialColorTarget.Inner -> onInnerColorChange(color)
                    RadialColorTarget.Divider -> onDividerColorChange(color)
                    RadialColorTarget.Icon -> onIconColorChange(color)
                    null -> Unit
                }
                colorTarget = null
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScreenScaffold(
            title = stringResource(R.string.floating_pointer_radial_settings_title),
            onBack = onBack,
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                RadialMenuTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                when (tab) {
                                    RadialMenuTab.Settings -> stringResource(R.string.floating_pointer_radial_tab_settings)
                                    RadialMenuTab.Functions -> stringResource(R.string.floating_pointer_radial_tab_functions)
                                    RadialMenuTab.Design -> stringResource(R.string.floating_pointer_radial_tab_design)
                                },
                            )
                        },
                    )
                }
            }

            when (selectedTab) {
                RadialMenuTab.Settings -> {
                    SettingsHintText(stringResource(R.string.floating_pointer_radial_settings_desc))
                    SettingsCard {
                        SettingSwitchRow(
                            title = stringResource(R.string.floating_pointer_radial_enabled),
                            subtitle = stringResource(R.string.floating_pointer_radial_enabled_desc),
                            checked = settings.floatingPointerRadialMenuEnabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange,
                        )
                        if (settings.floatingPointerRadialMenuEnabled) {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_radial_long_press_ms),
                                value = settings.floatingPointerRadialLongPressMs.toFloat(),
                                valueRange = 200f..2000f,
                                steps = 17,
                                enabled = true,
                                label = stringResource(
                                    R.string.floating_pointer_radial_long_press_ms_value,
                                    settings.floatingPointerRadialLongPressMs,
                                ),
                                onValueChange = { onLongPressMsChange(it.roundToInt()) },
                            )
                        }
                    }
                    SettingsHintText(stringResource(R.string.floating_pointer_radial_usage_hint))
                }

                RadialMenuTab.Functions -> {
                    SettingsSectionTitle(stringResource(R.string.floating_pointer_radial_functions_section))
                    SettingsCard {
                        repeat(FloatingPointerRadialMenuCodec.SLOT_COUNT) { index ->
                            val action = settings.floatingPointerRadialSlotActions.getOrElse(index) { GestureAction.None }
                            SettingNavigationRow(
                                icon = {
                                    Icon(
                                        imageVector = gestureActionIcon(action),
                                        contentDescription = null,
                                    )
                                },
                                title = radialSlotDirectionLabel(index),
                                subtitle = radialSlotActionSubtitle(action),
                                onClick = { pickingSlot = index },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            when (val current = action) {
                                                is GestureAction.SimulatePointerSwipe -> {
                                                    swipeConfigDraft = current.config
                                                    swipeConfigSlot = index
                                                }
                                                else -> pickingSlot = index
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }

                RadialMenuTab.Design -> {
                    SettingsCard {
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_radial_outer_size),
                            value = settings.floatingPointerRadialOuterDiameterPx,
                            valueRange = 240f..720f,
                            steps = 23,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_size_px_value,
                                settings.floatingPointerRadialOuterDiameterPx.roundToInt(),
                            ),
                            onValueChange = onOuterDiameterChange,
                        )
                        AnimationStyleColorRow(
                            title = stringResource(R.string.floating_pointer_radial_outer_color),
                            color = settings.floatingPointerRadialOuterColorArgb,
                            enabled = true,
                            onClick = {
                                pickerInitialColor = settings.floatingPointerRadialOuterColorArgb
                                colorTarget = RadialColorTarget.Outer
                            },
                        )
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_radial_inner_size),
                            value = settings.floatingPointerRadialInnerDiameterPx,
                            valueRange = 80f..480f,
                            steps = 19,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_size_px_value,
                                settings.floatingPointerRadialInnerDiameterPx.roundToInt(),
                            ),
                            onValueChange = onInnerDiameterChange,
                        )
                        AnimationStyleColorRow(
                            title = stringResource(R.string.floating_pointer_radial_inner_color),
                            color = settings.floatingPointerRadialInnerColorArgb,
                            enabled = true,
                            onClick = {
                                pickerInitialColor = settings.floatingPointerRadialInnerColorArgb
                                colorTarget = RadialColorTarget.Inner
                            },
                        )
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_radial_divider_thickness),
                            value = settings.floatingPointerRadialDividerThicknessPx,
                            valueRange = 1f..12f,
                            steps = 10,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_size_px_value,
                                settings.floatingPointerRadialDividerThicknessPx.roundToInt(),
                            ),
                            onValueChange = onDividerThicknessChange,
                        )
                        AnimationStyleColorRow(
                            title = stringResource(R.string.floating_pointer_radial_divider_color),
                            color = settings.floatingPointerRadialDividerColorArgb,
                            enabled = true,
                            onClick = {
                                pickerInitialColor = settings.floatingPointerRadialDividerColorArgb
                                colorTarget = RadialColorTarget.Divider
                            },
                        )
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_radial_icon_size),
                            value = settings.floatingPointerRadialIconSizeFraction,
                            valueRange = 0.2f..0.9f,
                            steps = 13,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                (settings.floatingPointerRadialIconSizeFraction * 100).roundToInt(),
                            ),
                            onValueChange = onIconSizeFractionChange,
                        )
                        AnimationStyleColorRow(
                            title = stringResource(R.string.floating_pointer_radial_icon_color),
                            color = settings.floatingPointerRadialIconColorArgb,
                            enabled = true,
                            onClick = {
                                pickerInitialColor = settings.floatingPointerRadialIconColorArgb
                                colorTarget = RadialColorTarget.Icon
                            },
                        )
                    }
                    SettingLinkRow(
                        title = stringResource(R.string.floating_pointer_radial_reset_design),
                        onClick = onResetDesignDefaults,
                    )
                }
            }

            SettingsSectionTitle(stringResource(R.string.floating_pointer_preview_section))
            Surface(
                modifier = Modifier.padding(bottom = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                FloatingPointerRadialMenuPreview(
                    settings = settings,
                    slots = settings.floatingPointerRadialSlotActions,
                    highlightedSlot = 2,
                )
            }
        }

        AnimatedFullScreenOverlay(visible = pickingSlot >= 0) {
            val slot = pickingSlot
            if (slot >= 0) {
                GestureActionPickerScreen(
                    trigger = GestureTriggerType.SHORT_SWIPE_IN,
                    current = settings.floatingPointerRadialSlotActions.getOrElse(slot) { GestureAction.None },
                    onDismiss = { pickingSlot = -1 },
                    onSelect = { action ->
                        if (action is GestureAction.FloatingPointer) {
                            pickingSlot = -1
                            return@GestureActionPickerScreen
                        }
                        if (action is GestureAction.SimulatePointerSwipe) {
                            swipeConfigDraft = action.config
                            swipeConfigSlot = slot
                        } else {
                            onSlotActionChange(slot, action)
                        }
                        pickingSlot = -1
                    },
                )
            }
        }
    }
}

@Composable
private fun radialSlotDirectionLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.floating_pointer_radial_slot_top)
    1 -> stringResource(R.string.floating_pointer_radial_slot_top_right)
    2 -> stringResource(R.string.floating_pointer_radial_slot_right)
    3 -> stringResource(R.string.floating_pointer_radial_slot_bottom_right)
    4 -> stringResource(R.string.floating_pointer_radial_slot_bottom)
    5 -> stringResource(R.string.floating_pointer_radial_slot_bottom_left)
    6 -> stringResource(R.string.floating_pointer_radial_slot_left)
    7 -> stringResource(R.string.floating_pointer_radial_slot_top_left)
    else -> stringResource(R.string.floating_pointer_radial_slot_top)
}

@Composable
private fun radialSlotActionSubtitle(action: GestureAction): String {
    val base = gestureActionLabel(action)
    return if (action is GestureAction.SimulatePointerSwipe) {
        stringResource(R.string.pointer_swipe_action_summary, base)
    } else {
        base
    }
}
