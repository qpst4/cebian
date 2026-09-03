package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.InwardCompoundBranch
import com.slideindex.app.gesture.SwipeDirectionFamily
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SwipeHoverDurationLimits
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.defaultTriggerModeFor
import com.slideindex.app.settings.displayTriggerMode
import com.slideindex.app.settings.gestureConfigSide
import com.slideindex.app.settings.oppositeGesturesSyncedForHandle
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card

private enum class SideGestureDistanceTab {
    Short,
    Long,
    Compound,
}

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
    onAlignOppositeGesturesChange: (enabled: Boolean, mirrorSourceSide: PanelSide?) -> Unit = { _, _ -> },
    onSwipeHoverDurationChange: (Int) -> Unit = {},
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

    val slotSide = settings.gestureConfigSide(side, handleId)
    var showMirrorDirectionDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SideGestureDistanceTab.Short) }
    val showAlignGesturesSwitch = side.isHorizontalEdge &&
        settings.triggerHandle(PanelSide.LEFT, handleId) != null &&
        settings.triggerHandle(PanelSide.RIGHT, handleId) != null

    val behaviorSectionTitle = stringResource(R.string.side_gestures_behavior_section)
    val swipeDirectionsSectionTitle = stringResource(R.string.side_gestures_swipe_directions_section)
    val pressTapSectionTitle = stringResource(R.string.side_gestures_press_tap)
    val straightSectionTitle = stringResource(R.string.side_gestures_direction_straight_section)
    val hoverSectionTitle = stringResource(R.string.side_gestures_direction_pause_section)
    val compoundHint = stringResource(R.string.side_gestures_inward_branch_hint)

    val pressTapItems = sideGestureSlotCardItems(
        settings = settings,
        slotSide = slotSide,
        handleId = handleId,
        side = side,
        triggers = GestureTriggerType.pressTapEntries(),
        titleStyle = SideGestureSlotTitleStyle.TriggerLabel,
        onOpenSlotConfig = onOpenSlotConfig,
    )

    SettingsLazyScreenScaffold(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    ) {
        settingsLazySmallTitle(
            key = "section-behavior",
            title = behaviorSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "side-gesture-behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("trigger-design") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Brush, contentDescription = label) },
                            title = stringResource(R.string.trigger_design_entry),
                            subtitle = triggerDesignSummary(selectedHandle.design),
                            onClick = onOpenDesignSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("trigger-appearance") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Outlined.Animation, contentDescription = label) },
                            title = stringResource(R.string.trigger_appearance_entry),
                            subtitle = triggerAppearanceSummary(settings, side, handleId),
                            onClick = onOpenAppearanceSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("default-trigger-mode") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = label)
                            },
                            title = stringResource(R.string.default_trigger_mode),
                            subtitle = triggerModeLabel(settings.defaultTriggerModeFor(slotSide), includeDefault = false),
                            onClick = onOpenDefaultModePick,
                        )
                    },
                )
                if (showAlignGesturesSwitch) {
                    add(
                        settingsCardScopeItem("align-opposite-gestures") {
                            SettingSwitchRow(
                                title = stringResource(R.string.align_opposite_gestures),
                                subtitle = stringResource(R.string.align_opposite_gestures_desc),
                                checked = selectedHandle.alignOppositeGestures,
                                enabled = serviceEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        showMirrorDirectionDialog = true
                                    } else {
                                        onAlignOppositeGesturesChange(false, null)
                                    }
                                },
                            )
                        },
                    )
                }
                add(
                    settingsCardScopeItem("swipe-hover-duration") {
                        SettingsSliderRow(
                            title = stringResource(R.string.side_gestures_hover_duration),
                            value = settings.swipeHoverDurationMs.toFloat(),
                            valueRange = SwipeHoverDurationLimits.MIN_MS.toFloat()..SwipeHoverDurationLimits.MAX_MS.toFloat(),
                            steps = (SwipeHoverDurationLimits.MAX_MS - SwipeHoverDurationLimits.MIN_MS) / 10,
                            enabled = serviceEnabled,
                            label = stringResource(
                                R.string.side_gestures_hover_duration_value,
                                settings.swipeHoverDurationMs,
                            ),
                            onValueChange = { onSwipeHoverDurationChange(it.roundToInt()) },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "section-swipe-directions",
            title = swipeDirectionsSectionTitle,
            sectionTop = true,
        )

        item(key = "swipe-distance-tab-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MiuixTabRowWithContour(
                        tabs = SideGestureDistanceTab.entries.map { tab ->
                            stringResource(
                                when (tab) {
                                    SideGestureDistanceTab.Short -> R.string.side_gestures_tab_short_distance
                                    SideGestureDistanceTab.Long -> R.string.side_gestures_tab_long_distance
                                    SideGestureDistanceTab.Compound -> R.string.side_gestures_tab_compound
                                },
                            )
                        },
                        selectedTabIndex = selectedTab.ordinal,
                        onTabSelected = { selectedTab = SideGestureDistanceTab.entries[it] },
                        contourHost = MiuixTabRowContourHost.SurfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                    when (selectedTab) {
                        SideGestureDistanceTab.Short -> {
                            MiuixSmallTitle(
                                text = straightSectionTitle,
                                insideMargin = PaddingValues(bottom = 4.dp),
                            )
                            RenderSideGestureSlotItems(
                                sideGestureSlotCardItems(
                                    settings = settings,
                                    slotSide = slotSide,
                                    handleId = handleId,
                                    side = side,
                                    triggers = SwipeDirectionFamily.orderedEntries().map { it.shortTrigger },
                                    titleStyle = SideGestureSlotTitleStyle.SlotLabel,
                                    onOpenSlotConfig = onOpenSlotConfig,
                                ),
                            )
                            MiuixSmallTitle(
                                text = hoverSectionTitle,
                                insideMargin = PaddingValues(top = 8.dp, bottom = 4.dp),
                            )
                            RenderSideGestureSlotItems(
                                sideGestureSlotCardItems(
                                    settings = settings,
                                    slotSide = slotSide,
                                    handleId = handleId,
                                    side = side,
                                    triggers = SwipeDirectionFamily.orderedEntries().map { it.hoverTrigger },
                                    titleStyle = SideGestureSlotTitleStyle.TriggerLabel,
                                    onOpenSlotConfig = onOpenSlotConfig,
                                ),
                            )
                        }

                        SideGestureDistanceTab.Long -> {
                            RenderSideGestureSlotItems(
                                sideGestureSlotCardItems(
                                    settings = settings,
                                    slotSide = slotSide,
                                    handleId = handleId,
                                    side = side,
                                    triggers = SwipeDirectionFamily.orderedEntries().map { it.longTrigger },
                                    titleStyle = SideGestureSlotTitleStyle.SlotLabel,
                                    onOpenSlotConfig = onOpenSlotConfig,
                                ),
                            )
                        }

                        SideGestureDistanceTab.Compound -> {
                            MiuixHintText(
                                text = compoundHint,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            RenderSideGestureSlotItems(
                                sideGestureCompoundSlotItems(
                                    settings = settings,
                                    slotSide = slotSide,
                                    handleId = handleId,
                                    side = side,
                                    onOpenSlotConfig = onOpenSlotConfig,
                                ),
                            )
                        }
                    }
                }
            }
        }

        settingsLazySmallTitle(
            key = "section-press-tap",
            title = pressTapSectionTitle,
            sectionTop = true,
        )
        groupedCardItems("side-gesture-press-tap", pressTapItems)

        item(key = "side-gesture-bottom-spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showMirrorDirectionDialog) {
        AlignOppositeGesturesMirrorDialog(
            currentSide = side,
            onDismiss = { showMirrorDirectionDialog = false },
            onCopyCurrentToOpposite = {
                showMirrorDirectionDialog = false
                onAlignOppositeGesturesChange(true, side)
            },
            onCopyOppositeToCurrent = {
                showMirrorDirectionDialog = false
                onAlignOppositeGesturesChange(true, side.opposite())
            },
        )
    }
}

private fun sideGestureCompoundTriggers(): List<GestureTriggerType> = buildList {
    InwardCompoundBranch.orderedEntries().forEach { branch ->
        add(branch.shortTrigger)
        branch.pairedLongTrigger?.let { add(it) }
    }
}

private enum class SideGestureSlotTitleStyle {
    SlotLabel,
    TriggerLabel,
}

private fun sideGestureCompoundSlotItems(
    settings: AppSettings,
    slotSide: PanelSide,
    handleId: String,
    side: PanelSide,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
): List<CardItem> = sideGestureSlotCardItems(
    settings = settings,
    slotSide = slotSide,
    handleId = handleId,
    side = side,
    triggers = sideGestureCompoundTriggers(),
    titleStyle = SideGestureSlotTitleStyle.SlotLabel,
    onOpenSlotConfig = onOpenSlotConfig,
)

private fun sideGestureSlotCardItems(
    settings: AppSettings,
    slotSide: PanelSide,
    handleId: String,
    side: PanelSide,
    triggers: List<GestureTriggerType>,
    titleStyle: SideGestureSlotTitleStyle,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
): List<CardItem> = buildList {
    triggers.forEach { trigger ->
        add(
            settingsCardScopeItem("slot-${trigger.name}") {
                val label = when (titleStyle) {
                    SideGestureSlotTitleStyle.SlotLabel -> triggerSlotLabel(side, trigger)
                    SideGestureSlotTitleStyle.TriggerLabel -> triggerLabel(side, trigger)
                }
                GestureSlotRow(
                    side = side,
                    trigger = trigger,
                    label = label,
                    action = settings.actionFor(slotSide, trigger, handleId),
                    modeLabel = if (trigger.isHoverSwipe) {
                        null
                    } else {
                        triggerModeLabel(settings.displayTriggerMode(slotSide, trigger, handleId))
                    },
                    onClick = { onOpenSlotConfig(trigger) },
                )
            },
        )
    }
}

@Composable
private fun ColumnScope.RenderSideGestureSlotItems(items: List<CardItem>) {
    items.forEach { cardItem ->
        key(cardItem.key) {
            cardItem.content(this)
        }
    }
}

@Composable
private fun AlignOppositeGesturesMirrorDialog(
    currentSide: PanelSide,
    onDismiss: () -> Unit,
    onCopyCurrentToOpposite: () -> Unit,
    onCopyOppositeToCurrent: () -> Unit,
) {
    val currentSideLabel = horizontalGestureSideLabel(currentSide)
    val oppositeSideLabel = horizontalGestureSideLabel(currentSide.opposite())

    top.yukonga.miuix.kmp.window.WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.align_opposite_gestures_mirror_title),
        summary = stringResource(R.string.align_opposite_gestures_mirror_message),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(R.string.align_opposite_gestures_use_this_side, currentSideLabel),
                onClick = onCopyCurrentToOpposite,
                modifier = Modifier.fillMaxWidth(),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
            )
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(R.string.align_opposite_gestures_use_opposite_side, oppositeSideLabel),
                onClick = onCopyOppositeToCurrent,
                modifier = Modifier.fillMaxWidth(),
            )
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun horizontalGestureSideLabel(side: PanelSide): String = when (side) {
    PanelSide.LEFT -> stringResource(R.string.trigger_side_left_item)
    PanelSide.RIGHT -> stringResource(R.string.trigger_side_right_item)
    else -> side.name
}

@Composable
private fun SettingsCardScope.GestureSlotRow(
    side: PanelSide,
    trigger: GestureTriggerType,
    label: String,
    action: GestureAction,
    modeLabel: String?,
    onClick: () -> Unit,
) {
    val subtitle = if (modeLabel == null) {
        gestureActionSettingSubtitle(action)
    } else {
        listOf(gestureActionSettingSubtitle(action), modeLabel).joinToString(" · ")
    }
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
        subtitle = subtitle,
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
