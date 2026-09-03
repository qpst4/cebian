package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.InwardCompoundBranch
import com.slideindex.app.gesture.SwipeDirectionFamily
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.displayTriggerMode

@Composable
fun swipeDirectionFamilyTitle(side: PanelSide, family: SwipeDirectionFamily): String =
    triggerLabel(side, family.shortTrigger)

@Composable
fun swipeDirectionFamilyEntrySubtitle(
    settings: AppSettings,
    configSide: PanelSide,
    handleId: String,
    family: SwipeDirectionFamily,
): String {
    val shortAction = settings.actionFor(configSide, family.shortTrigger, handleId)
    val longAction = settings.actionFor(configSide, family.longTrigger, handleId)
    val hoverAction = settings.actionFor(configSide, family.hoverTrigger, handleId)
    val primaryTrigger = when {
        longAction.isEffective() -> family.longTrigger
        shortAction.isEffective() -> family.shortTrigger
        hoverAction.isEffective() -> family.hoverTrigger
        else -> null
    }
    if (primaryTrigger == null) {
        return stringResource(R.string.trigger_summary_none)
    }
    val action = settings.actionFor(configSide, primaryTrigger, handleId)
    val line = buildString {
        append(gestureActionSettingSubtitle(action))
        if (!primaryTrigger.isHoverSwipe) {
            append(" · ")
            append(triggerModeLabel(settings.displayTriggerMode(configSide, primaryTrigger, handleId)))
        }
    }
    if (family.hasAfterPauseBranches && inwardBranchesConfigured(settings, configSide, handleId)) {
        return "$line · ${stringResource(R.string.side_gestures_inward_entry_suffix)}"
    }
    return line
}

@Composable
fun gestureSlotConfigSubtitle(
    settings: AppSettings,
    configSide: PanelSide,
    handleId: String,
    trigger: GestureTriggerType,
): String {
    val action = settings.actionFor(configSide, trigger, handleId)
    return "${gestureActionSettingSubtitle(action)} · " +
        triggerModeLabel(settings.displayTriggerMode(configSide, trigger, handleId))
}

private fun inwardBranchesConfigured(
    settings: AppSettings,
    configSide: PanelSide,
    handleId: String,
): Boolean =
    InwardCompoundBranch.orderedEntries().any { branch ->
        settings.actionFor(configSide, branch.shortTrigger, handleId).isEffective() ||
            branch.pairedLongTrigger?.let { longTrigger ->
                settings.actionFor(configSide, longTrigger, handleId).isEffective()
            } == true
    }
