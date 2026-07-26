package com.slideindex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerCollectionEntry
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.allTriggerHandles
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.ui.settings.components.SwitchNavigationTrailingContent
import kotlinx.coroutines.delay

private const val TRIGGER_PAIR_ENTER_MS = 260
private const val TRIGGER_PAIR_EXIT_MS = 200
private const val TRIGGER_ACTION_ENTER_MS = 220
private const val TRIGGER_ACTION_EXIT_MS = 180

private data class PendingSideRemove(val side: PanelSide, val handleId: String)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TriggerCollectionScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onOpenLeftTrigger: (handleId: String) -> Unit,
    onOpenRightTrigger: (handleId: String) -> Unit,
    onOpenBottomTrigger: (handleId: String) -> Unit,
    onOpenTopTrigger: (handleId: String) -> Unit,
    onAddTriggerPair: () -> Unit,
    onAddBottomTrigger: () -> Unit,
    onAddTopTrigger: () -> Unit,
    onRemoveTriggerHandle: (PanelSide, String) -> Unit,
    onTriggerHandleEnabledChange: (PanelSide, String, Boolean) -> Unit,
) {
    var sideExpanded by rememberSaveable { mutableStateOf(true) }
    var pendingRemove by remember { mutableStateOf<PendingSideRemove?>(null) }
    val entries = remember(settings.leftTriggerHandles, settings.rightTriggerHandles) {
        settings.triggerCollectionEntries()
    }
    val pairColors = listOf(
        Color(0xFF7E57C2),
        Color(0xFF26A69A),
        Color(0xFFFF7043),
    )

    SettingsScreenScaffold(
        title = stringResource(R.string.trigger_collection_title),
        subtitle = stringResource(R.string.trigger_collection_desc),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.trigger_collection_long_press_remove_hint))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TriggerEntryList(
                entries = entries,
                pairColors = pairColors,
                settings = settings,
                serviceEnabled = serviceEnabled,
                sideExpanded = sideExpanded,
                onToggleExpanded = { sideExpanded = !sideExpanded },
                onOpenLeftTrigger = onOpenLeftTrigger,
                onOpenRightTrigger = onOpenRightTrigger,
                onRequestRemoveSide = { side, handleId ->
                    pendingRemove = PendingSideRemove(side, handleId)
                },
                onTriggerHandleEnabledChange = onTriggerHandleEnabledChange,
            )
            AnimatedVisibility(
                visible = sideExpanded,
                enter = expandVertically(
                    animationSpec = tween(TRIGGER_ACTION_ENTER_MS),
                    expandFrom = Alignment.Top,
                ),
                exit = shrinkVertically(
                    animationSpec = tween(TRIGGER_ACTION_EXIT_MS),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                TextButton(
                    onClick = onAddTriggerPair,
                    enabled = serviceEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    Text(stringResource(R.string.trigger_handles_add))
                }
            }
            SettingsSectionTitle(stringResource(R.string.trigger_collection_bottom))
            settings.allTriggerHandles(PanelSide.BOTTOM).forEach { handle ->
                SettingsCard {
                    SettingSwitchNavigationRow(
                        title = triggerCollectionHandleTitle(PanelSide.BOTTOM, handle.id),
                        subtitle = triggerHandleActionSummary(settings, PanelSide.BOTTOM, handle.id),
                        icon = { label ->
                            Icon(Icons.Default.SwipeRight, contentDescription = label)
                        },
                        checked = handle.enabled,
                        enabled = serviceEnabled,
                        onCheckedChange = {
                            onTriggerHandleEnabledChange(PanelSide.BOTTOM, handle.id, it)
                        },
                        onNavigate = { onOpenBottomTrigger(handle.id) },
                        onLongClick = if (serviceEnabled) {
                            {
                                pendingRemove = PendingSideRemove(
                                    side = PanelSide.BOTTOM,
                                    handleId = handle.id,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            TextButton(
                onClick = onAddBottomTrigger,
                enabled = serviceEnabled && settings.allTriggerHandles(PanelSide.BOTTOM).size < 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(stringResource(R.string.trigger_collection_add_bottom))
            }
            SettingsSectionTitle(stringResource(R.string.trigger_collection_top))
            settings.allTriggerHandles(PanelSide.TOP).forEach { handle ->
                SettingsCard {
                    SettingSwitchNavigationRow(
                        title = triggerCollectionHandleTitle(PanelSide.TOP, handle.id),
                        subtitle = triggerHandleActionSummary(settings, PanelSide.TOP, handle.id),
                        icon = { label ->
                            Icon(Icons.Default.SwipeRight, contentDescription = label)
                        },
                        checked = handle.enabled,
                        enabled = serviceEnabled,
                        onCheckedChange = {
                            onTriggerHandleEnabledChange(PanelSide.TOP, handle.id, it)
                        },
                        onNavigate = { onOpenTopTrigger(handle.id) },
                        onLongClick = if (serviceEnabled && handle.id != TriggerHandle.DEFAULT_ID) {
                            {
                                pendingRemove = PendingSideRemove(
                                    side = PanelSide.TOP,
                                    handleId = handle.id,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            TextButton(
                onClick = onAddTopTrigger,
                enabled = serviceEnabled && settings.allTriggerHandles(PanelSide.TOP).size < 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(stringResource(R.string.trigger_collection_add_top))
            }
        }
    }

    pendingRemove?.let { pending ->
        val sideLabel = when (pending.side) {
            PanelSide.LEFT -> stringResource(R.string.trigger_side_left_item)
            PanelSide.RIGHT -> stringResource(R.string.trigger_side_right_item)
            PanelSide.BOTTOM -> stringResource(R.string.trigger_collection_bottom)
            PanelSide.TOP -> stringResource(R.string.trigger_collection_top)
        }
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.trigger_remove_side_confirm_title, sideLabel)) },
            text = { Text(stringResource(R.string.trigger_remove_side_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveTriggerHandle(pending.side, pending.handleId)
                        pendingRemove = null
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TriggerEntryList(
    entries: List<TriggerCollectionEntry>,
    pairColors: List<Color>,
    settings: AppSettings,
    serviceEnabled: Boolean,
    sideExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenLeftTrigger: (handleId: String) -> Unit,
    onOpenRightTrigger: (handleId: String) -> Unit,
    onRequestRemoveSide: (PanelSide, String) -> Unit,
    onTriggerHandleEnabledChange: (PanelSide, String, Boolean) -> Unit,
) {
    val targetIds = entries.map { it.handleId }
    var renderingIds by remember { mutableStateOf(targetIds) }
    var exitingIds by remember { mutableStateOf(setOf<String>()) }
    val entryCache = remember { mutableStateMapOf<String, TriggerCollectionEntry>() }
    entries.forEach { entryCache[it.handleId] = it }

    LaunchedEffect(targetIds) {
        val renderingSet = renderingIds.toSet()
        val targetSet = targetIds.toSet()
        val removed = renderingSet - targetSet
        val added = targetSet - renderingSet

        when {
            removed.isNotEmpty() -> {
                exitingIds = removed
                delay(TRIGGER_PAIR_EXIT_MS.toLong())
                exitingIds = emptySet()
                renderingIds = targetIds
            }
            added.isNotEmpty() -> {
                renderingIds = targetIds
            }
            renderingIds != targetIds -> {
                renderingIds = targetIds
            }
        }
    }

    val visibleRowCount = renderingIds.sumOf { handleId ->
        val entry = entryCache[handleId] ?: return@sumOf 0
        listOfNotNull(entry.left, entry.right).size
    }
    val segmentCount = if (sideExpanded) 1 + visibleRowCount else 1
    var segmentIndex = 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(TRIGGER_PAIR_ENTER_MS)),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        SegmentedListItem(
            onClick = onToggleExpanded,
            shapes = pickerSegmentedShapes(0, segmentCount),
            colors = pickerSegmentedColors(),
            trailingContent = {
                Icon(
                    imageVector = if (sideExpanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = stringResource(
                        if (sideExpanded) R.string.cd_collapse_section else R.string.cd_expand_section,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            content = {
                Text(
                    text = stringResource(R.string.trigger_collection_side),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
        if (sideExpanded) {
            renderingIds.forEachIndexed { displayIndex, handleId ->
                val entry = entryCache[handleId] ?: return@forEachIndexed
                val entryIndex = targetIds.indexOf(handleId).takeIf { it >= 0 } ?: displayIndex
                val dotColor = pairColors[entryIndex % pairColors.size]
                val pairLabel = if (targetIds.size > 1) {
                    stringResource(R.string.trigger_pair_index, entryIndex + 1)
                } else {
                    null
                }
                AnimatedVisibility(
                    visible = handleId !in exitingIds,
                    enter = expandVertically(
                        animationSpec = tween(TRIGGER_PAIR_ENTER_MS),
                        expandFrom = Alignment.Top,
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(TRIGGER_PAIR_EXIT_MS),
                        shrinkTowards = Alignment.Top,
                    ),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
                    ) {
                        entry.left?.let { left ->
                            val currentSegment = segmentIndex++
                            TriggerSideRow(
                                side = PanelSide.LEFT,
                                segmentIndex = currentSegment,
                                segmentCount = segmentCount,
                                dotColor = dotColor,
                                title = stringResource(R.string.trigger_side_left_item),
                                pairLabel = pairLabel,
                                summary = triggerHandleActionSummary(settings, PanelSide.LEFT, handleId),
                                handleEnabled = left.enabled,
                                enabled = serviceEnabled,
                                onClick = { onOpenLeftTrigger(handleId) },
                                onLongClick = if (serviceEnabled) {
                                    { onRequestRemoveSide(PanelSide.LEFT, handleId) }
                                } else {
                                    null
                                },
                                onHandleEnabledChange = {
                                    onTriggerHandleEnabledChange(PanelSide.LEFT, handleId, it)
                                },
                            )
                        }
                        entry.right?.let { right ->
                            val currentSegment = segmentIndex++
                            TriggerSideRow(
                                side = PanelSide.RIGHT,
                                segmentIndex = currentSegment,
                                segmentCount = segmentCount,
                                dotColor = dotColor,
                                title = stringResource(R.string.trigger_side_right_item),
                                pairLabel = pairLabel,
                                summary = triggerHandleActionSummary(settings, PanelSide.RIGHT, handleId),
                                handleEnabled = right.enabled,
                                enabled = serviceEnabled,
                                onClick = { onOpenRightTrigger(handleId) },
                                onLongClick = if (serviceEnabled) {
                                    { onRequestRemoveSide(PanelSide.RIGHT, handleId) }
                                } else {
                                    null
                                },
                                onHandleEnabledChange = {
                                    onTriggerHandleEnabledChange(PanelSide.RIGHT, handleId, it)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TriggerSideRow(
    side: PanelSide,
    segmentIndex: Int,
    segmentCount: Int,
    dotColor: Color,
    title: String,
    pairLabel: String?,
    summary: String,
    handleEnabled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onHandleEnabledChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
        colors = pickerSegmentedColors(),
        leadingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Icon(
                    imageVector = Icons.Default.SwipeRight,
                    contentDescription = stringResource(R.string.cd_trigger_preview),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = if (side == PanelSide.RIGHT) {
                        Modifier.graphicsLayer { scaleX = -1f }
                    } else {
                        Modifier
                    },
                )
            }
        },
        trailingContent = {
            SwitchNavigationTrailingContent(
                checked = handleEnabled,
                enabled = enabled,
                onCheckedChange = onHandleEnabledChange,
            )
        },
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (pairLabel != null) {
                    Text(
                        text = pairLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        },
    )
}

@Composable
private fun triggerCollectionHandleTitle(side: PanelSide, handleId: String): String {
    if (side == PanelSide.BOTTOM && handleId == TriggerHandle.DEFAULT_ID) {
        return stringResource(R.string.trigger_bottom_default_title)
    }
    if (side == PanelSide.TOP && handleId == TriggerHandle.DEFAULT_ID) {
        return stringResource(R.string.trigger_top_default_title)
    }
    return handleId
}

@Composable
private fun triggerHandleActionSummary(
    settings: AppSettings,
    side: PanelSide,
    handleId: String,
): String {
    val labels = buildList {
        GestureTriggerType.shortDistanceEntries().forEach { trigger ->
            val action = settings.actionFor(side, trigger, handleId)
            if (action.isEffective()) add(gestureActionLabel(action))
        }
        GestureTriggerType.pressTapEntries().forEach { trigger ->
            val action = settings.actionFor(side, trigger, handleId)
            if (action.isEffective()) add(gestureActionLabel(action))
        }
        GestureTriggerType.longDistanceEntries().forEach { trigger ->
            val action = settings.actionFor(side, trigger, handleId)
            if (action.isEffective()) add(gestureActionLabel(action))
        }
    }.distinct()
    return if (labels.isEmpty()) {
        stringResource(R.string.trigger_summary_none)
    } else {
        labels.take(5).joinToString("、")
    }
}
