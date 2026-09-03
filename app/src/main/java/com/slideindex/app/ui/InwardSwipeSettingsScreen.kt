package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.InwardCompoundBranch
import com.slideindex.app.gesture.SwipeDirectionFamily
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.gestureConfigSide
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InwardSwipeSettingsScreen(
    side: PanelSide,
    handleId: String,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
) {
    val configSide = settings.gestureConfigSide(side, handleId)
    val pathHint = stringResource(R.string.side_gestures_inward_swipe_path_hint)
    val straightSectionTitle = stringResource(R.string.side_gestures_direction_straight_section)
    val holdSectionTitle = stringResource(R.string.side_gestures_inward_hold_section)
    val holdHint = stringResource(R.string.side_gestures_inward_hold_hint)
    val afterHoldSectionTitle = stringResource(R.string.side_gestures_direction_after_pause_section)
    val shortSlotTitle = stringResource(R.string.side_gestures_direction_short_slot)
    val longSlotTitle = stringResource(R.string.side_gestures_direction_long_slot)
    val holdSlotTitle = stringResource(R.string.side_gestures_inward_hold_slot)
    val afterHoldHint = stringResource(R.string.side_gestures_inward_branch_hint)
    val upBranchTitle = stringResource(R.string.side_gestures_inward_branch_turn_up)
    val downBranchTitle = stringResource(R.string.side_gestures_inward_branch_turn_down)
    val returnBranchTitle = stringResource(R.string.side_gestures_inward_branch_return)

    SettingsScreenScaffold(
        title = swipeDirectionFamilyTitle(side, SwipeDirectionFamily.IN),
        subtitle = stringResource(R.string.side_gestures_inward_swipe_subtitle),
        onBack = onBack,
    ) {
        settingsLazyHint(
            key = "inward-swipe-path-hint",
            text = pathHint,
        )
        item(key = "inward-swipe-path-preview") {
            InwardSwipePathPreview(
                side = side,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        settingsLazySmallTitle(
            key = "inward-straight-section",
            title = straightSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "inward-straight",
            items = listOf(
                settingsCardScopeItem("inward-straight-short") {
                    GestureDirectionSlotRow(
                        side = side,
                        trigger = GestureTriggerType.SHORT_SWIPE_IN,
                        title = shortSlotTitle,
                        subtitle = gestureSlotConfigSubtitle(
                            settings,
                            configSide,
                            handleId,
                            GestureTriggerType.SHORT_SWIPE_IN,
                        ),
                        onClick = { onOpenSlotConfig(GestureTriggerType.SHORT_SWIPE_IN) },
                    )
                },
                settingsCardScopeItem("inward-straight-long") {
                    GestureDirectionSlotRow(
                        side = side,
                        trigger = GestureTriggerType.LONG_SWIPE_IN,
                        title = longSlotTitle,
                        subtitle = gestureSlotConfigSubtitle(
                            settings,
                            configSide,
                            handleId,
                            GestureTriggerType.LONG_SWIPE_IN,
                        ),
                        onClick = { onOpenSlotConfig(GestureTriggerType.LONG_SWIPE_IN) },
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "inward-hold-section",
            title = holdSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(
            key = "inward-hold-hint",
            text = holdHint,
        )
        groupedCardItems(
            keyPrefix = "inward-hold",
            items = listOf(
                settingsCardScopeItem("inward-hold-release") {
                    GestureDirectionSlotRow(
                        side = side,
                        trigger = GestureTriggerType.SHORT_SWIPE_IN_HOVER,
                        title = holdSlotTitle,
                        subtitle = gestureSlotConfigSubtitle(
                            settings,
                            configSide,
                            handleId,
                            GestureTriggerType.SHORT_SWIPE_IN_HOVER,
                        ),
                        onClick = { onOpenSlotConfig(GestureTriggerType.SHORT_SWIPE_IN_HOVER) },
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "inward-after-hold-section",
            title = afterHoldSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(
            key = "inward-after-hold-hint",
            text = afterHoldHint,
        )
        groupedCardItems(
            keyPrefix = "inward-after-hold",
            items = inwardAfterHoldSlotItems(
                side = side,
                configSide = configSide,
                handleId = handleId,
                settings = settings,
                upBranchTitle = upBranchTitle,
                downBranchTitle = downBranchTitle,
                returnBranchTitle = returnBranchTitle,
                shortSlotTitle = shortSlotTitle,
                longSlotTitle = longSlotTitle,
                onOpenSlotConfig = onOpenSlotConfig,
            ),
        )
    }
}

private fun inwardAfterHoldSlotItems(
    side: PanelSide,
    configSide: PanelSide,
    handleId: String,
    settings: AppSettings,
    upBranchTitle: String,
    downBranchTitle: String,
    returnBranchTitle: String,
    shortSlotTitle: String,
    longSlotTitle: String,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
) = buildList {
    addAll(
        inwardBranchDistanceSlots(
            side = side,
            configSide = configSide,
            handleId = handleId,
            settings = settings,
            branch = InwardCompoundBranch.UP,
            branchTitle = upBranchTitle,
            shortSlotTitle = shortSlotTitle,
            longSlotTitle = longSlotTitle,
            onOpenSlotConfig = onOpenSlotConfig,
        ),
    )
    addAll(
        inwardBranchDistanceSlots(
            side = side,
            configSide = configSide,
            handleId = handleId,
            settings = settings,
            branch = InwardCompoundBranch.DOWN,
            branchTitle = downBranchTitle,
            shortSlotTitle = shortSlotTitle,
            longSlotTitle = longSlotTitle,
            onOpenSlotConfig = onOpenSlotConfig,
        ),
    )
    add(
        settingsCardScopeItem("inward-after-hold-return") {
            GestureDirectionSlotRow(
                side = side,
                trigger = InwardCompoundBranch.RETURN.shortTrigger,
                title = returnBranchTitle,
                subtitle = gestureSlotConfigSubtitle(
                    settings,
                    configSide,
                    handleId,
                    InwardCompoundBranch.RETURN.shortTrigger,
                ),
                onClick = { onOpenSlotConfig(InwardCompoundBranch.RETURN.shortTrigger) },
            )
        },
    )
}

private fun inwardBranchDistanceSlots(
    side: PanelSide,
    configSide: PanelSide,
    handleId: String,
    settings: AppSettings,
    branch: InwardCompoundBranch,
    branchTitle: String,
    shortSlotTitle: String,
    longSlotTitle: String,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
): List<CardItem> {
    val longTrigger = branch.pairedLongTrigger ?: return emptyList()
    return listOf(
        settingsCardScopeItem("inward-after-hold-${branch.name}-short") {
            GestureDirectionSlotRow(
                side = side,
                trigger = branch.shortTrigger,
                title = inwardBranchSlotTitle(branchTitle, shortSlotTitle),
                subtitle = gestureSlotConfigSubtitle(
                    settings,
                    configSide,
                    handleId,
                    branch.shortTrigger,
                ),
                onClick = { onOpenSlotConfig(branch.shortTrigger) },
            )
        },
        settingsCardScopeItem("inward-after-hold-${branch.name}-long") {
            GestureDirectionSlotRow(
                side = side,
                trigger = longTrigger,
                title = inwardBranchSlotTitle(branchTitle, longSlotTitle),
                subtitle = gestureSlotConfigSubtitle(
                    settings,
                    configSide,
                    handleId,
                    longTrigger,
                ),
                onClick = { onOpenSlotConfig(longTrigger) },
            )
        },
    )
}

@Composable
fun inwardBranchSlotTitle(branchTitle: String, distanceTitle: String): String =
    stringResource(R.string.side_gestures_inward_branch_slot_title, branchTitle, distanceTitle)
