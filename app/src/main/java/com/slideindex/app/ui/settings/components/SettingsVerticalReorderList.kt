@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slideindex.app.launcher.QuickLauncherGridLogic.moveIndex
import com.slideindex.app.ui.pickerListSegmentedGap
import kotlin.math.roundToInt

@Composable
fun <T> SettingsVerticalReorderList(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    itemContent: @Composable (
        item: T,
        index: Int,
        segmentIndex: Int,
        segmentCount: Int,
        modifier: Modifier,
    ) -> Unit,
) {
    if (items.isEmpty()) return

    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragSnapshot by remember { mutableStateOf<List<T>?>(null) }
    var draggedItemKey by remember { mutableStateOf<Any?>(null) }
    var dragRowHeightPx by remember { mutableFloatStateOf(0f) }
    var measuredRowHeightPx by remember { mutableFloatStateOf(0f) }
    var pendingItems by remember { mutableStateOf<List<T>?>(null) }
    val defaultRowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val baseItems = pendingItems ?: items
    val itemsOrderKey = remember(baseItems) { baseItems.map(key).joinToString(separator = "\u0000") }

    LaunchedEffect(items, itemsOrderKey) {
        val pending = pendingItems ?: return@LaunchedEffect
        if (items.map(key) == pending.map(key)) {
            pendingItems = null
        }
    }

    val displayItems = dragSnapshot ?: baseItems
    val isDragging = dragSnapshot != null
    val segmentCount = displayItems.size
    val draggedItem = draggedItemKey?.let { draggedKey ->
        displayItems.firstOrNull { key(it) == draggedKey }
    }

    fun indexOfItem(list: List<T>, itemKey: Any): Int =
        list.indexOfFirst { key(it) == itemKey }

    fun commitDragIfNeeded() {
        val snapshot = dragSnapshot
        val fromIndex = dragStartIndex
        val toIndex = dragTargetIndex
        if (snapshot != null && fromIndex >= 0 && fromIndex != toIndex) {
            val newOrder = snapshot.moveIndex(fromIndex, toIndex)
            pendingItems = newOrder
            onReorder(newOrder)
        }
    }

    fun clearDragState() {
        dragStartIndex = -1
        dragTargetIndex = -1
        dragOffsetY = 0f
        dragSnapshot = null
        draggedItemKey = null
        dragRowHeightPx = 0f
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        displayItems.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val rowHeight = measuredRowHeightPx.takeIf { it > 0f } ?: defaultRowHeightPx
                val itemIndex = indexOfItem(baseItems, itemKey)
                val isSourceSlot = isDragging && itemKey == draggedItemKey
                val displacementY = if (isDragging) {
                    displacementForIndex(
                        index = index,
                        fromIndex = dragStartIndex,
                        toIndex = dragTargetIndex,
                        rowHeightPx = dragRowHeightPx.takeIf { it > 0f } ?: rowHeight,
                    )
                } else {
                    0f
                }

                Box(
                    modifier = Modifier
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                measuredRowHeightPx = size.height.toFloat()
                            }
                        }
                        .offset { IntOffset(0, displacementY.roundToInt()) }
                        .zIndex(if (isSourceSlot) 1f else 0f)
                        .pointerInput(itemKey, itemIndex, itemsOrderKey) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val sourceItems = pendingItems ?: items
                                    val startIndex = indexOfItem(sourceItems, itemKey)
                                    if (startIndex < 0) return@detectDragGesturesAfterLongPress
                                    val activeRowHeight = measuredRowHeightPx
                                        .takeIf { it > 0f }
                                        ?: defaultRowHeightPx
                                    draggedItemKey = itemKey
                                    dragStartIndex = startIndex
                                    dragTargetIndex = startIndex
                                    dragOffsetY = 0f
                                    dragRowHeightPx = activeRowHeight
                                    dragSnapshot = sourceItems
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDrag = { change, dragAmount ->
                                    if (dragSnapshot == null) return@detectDragGesturesAfterLongPress
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val activeRowHeight = dragRowHeightPx
                                        .takeIf { it > 0f }
                                        ?: defaultRowHeightPx
                                    val newTarget = (dragStartIndex + (dragOffsetY / activeRowHeight).roundToInt())
                                        .coerceIn(0, displayItems.lastIndex)
                                    if (newTarget != dragTargetIndex) {
                                        dragTargetIndex = newTarget
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                },
                                onDragEnd = {
                                    commitDragIfNeeded()
                                    clearDragState()
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                                onDragCancel = {
                                    commitDragIfNeeded()
                                    clearDragState()
                                },
                            )
                        },
                ) {
                    Box(modifier = Modifier.alpha(if (isSourceSlot) 0f else 1f)) {
                        itemContent(item, index, index, segmentCount, Modifier)
                    }
                    if (isSourceSlot && draggedItem != null) {
                        val draggedIndex = indexOfItem(displayItems, draggedItemKey!!)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                                .zIndex(1f),
                        ) {
                            itemContent(
                                draggedItem,
                                draggedIndex.coerceAtLeast(0),
                                draggedIndex.coerceAtLeast(0),
                                segmentCount,
                                Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun displacementForIndex(
    index: Int,
    fromIndex: Int,
    toIndex: Int,
    rowHeightPx: Float,
): Float {
    if (fromIndex < 0) return 0f
    return when {
        index == fromIndex -> 0f
        fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> -rowHeightPx
        fromIndex > toIndex && index in toIndex until fromIndex -> rowHeightPx
        else -> 0f
    }
}
