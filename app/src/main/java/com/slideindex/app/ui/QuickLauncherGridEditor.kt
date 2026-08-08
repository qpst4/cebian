package com.slideindex.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherGridLogic.moveIndex
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.quicklauncher.QuickLauncherDeleteButtonLayer
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorToolbar
import com.slideindex.app.ui.quicklauncher.QuickLauncherGridCell
import com.slideindex.app.ui.quicklauncher.QuickLauncherPageGrid
import com.slideindex.app.ui.quicklauncher.QuickLauncherPageSwitcher
import com.slideindex.app.util.QuickLauncherIconResolver
import kotlin.math.roundToInt

private const val PAGE_EDGE_RESISTANCE = 0.35f
private const val PAGE_COMMIT_FRACTION = 0.22f
private const val PAGE_EDGE_AUTO_PAGE_CELL_FRACTION = 0.12f
private const val PAGE_AUTO_TURN_COOLDOWN_MS = 400L

@Composable
fun QuickLauncherGridEditor(
    settings: AppSettings,
    items: List<QuickLauncherItem>,
    appsByPackage: Map<String, AppInfo>,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
    onAdd: () -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit = {},
    showPageSwitcher: Boolean = true,
    gridColumnsOverride: Int? = null,
    gridRowsOverride: Int? = null,
) {
    var editMode by remember { mutableStateOf(false) }
    var dragFromGlobal by remember { mutableIntStateOf(-1) }
    var dragSlotGlobal by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartInGrid by remember { mutableStateOf(Offset.Zero) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageSwipeOffsetPx by remember { mutableFloatStateOf(0f) }
    var lastAutoPageTurnMs by remember { mutableLongStateOf(0L) }
    var dragEdgePageZone by remember { mutableIntStateOf(0) }
    var dragEdgeAutoPageSeeded by remember { mutableStateOf(false) }
    val columns = (gridColumnsOverride ?: settings.quickLauncherColumnsPerPage).coerceIn(2, 5)
    val rows = (gridRowsOverride ?: settings.quickLauncherRowsPerPage).coerceIn(2, 6)
    val pageSize = QuickLauncherGridLogic.pageSize(columns, rows)
    val pageCount = QuickLauncherGridLogic.pageCount(items.size, pageSize)
    val density = LocalDensity.current
    val gridGapPx = with(density) { 8.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val actionIconTintArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val iconBitmapCache = remember(items, appsByPackage, actionIconTintArgb) {
        items.mapIndexed { index, item ->
            index to QuickLauncherIconResolver.iconBitmap(
                item = item,
                appsByPackage = appsByPackage,
                context = context,
                actionIconTintArgb = actionIconTintArgb,
            )
        }.toMap()
    }

    LaunchedEffect(pageCount, columns, rows) {
        currentPage = currentPage.coerceIn(0, pageCount - 1)
    }

    LaunchedEffect(editMode, dragFromGlobal) {
        onInteractionActiveChange(editMode || dragFromGlobal >= 0)
    }

    val gridMinHeight = (rows * 96).dp

    SettingsCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showPageSwitcher) {
                QuickLauncherPageSwitcher(
                    currentPage = currentPage,
                    pageCount = pageCount,
                    onPrevious = {
                        if (currentPage > 0) {
                            currentPage -= 1
                            pageSwipeOffsetPx = 0f
                        }
                    },
                    onNext = {
                        if (currentPage < pageCount - 1) {
                            currentPage += 1
                            pageSwipeOffsetPx = 0f
                        }
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = gridMinHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .clipToBounds(),
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val pageWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
                        val pageStartIndex = currentPage * pageSize
                        val cellWidthPx = ((pageWidthPx - gridGapPx * (columns - 1)) / columns)
                            .coerceAtLeast(1f)
                        val cellHeightPx = cellWidthPx / 0.82f
                        val stepX = cellWidthPx + gridGapPx
                        val stepY = cellHeightPx + gridGapPx
                        val currentPageState = rememberUpdatedState(currentPage)
                        val itemsState = rememberUpdatedState(items)
                        val pageSizeState = rememberUpdatedState(pageSize)

                        fun finishPageSwipe() {
                            val threshold = pageWidthPx * PAGE_COMMIT_FRACTION
                            val offset = pageSwipeOffsetPx
                            val delta = when {
                                offset <= -threshold && currentPage < pageCount - 1 -> 1
                                offset >= threshold && currentPage > 0 -> -1
                                else -> 0
                            }
                            if (delta != 0) {
                                currentPage += delta
                            }
                            pageSwipeOffsetPx = 0f
                        }

                        fun dragEdgeZone(pointerX: Float): Int {
                            val edgeInset = cellWidthPx * PAGE_EDGE_AUTO_PAGE_CELL_FRACTION
                            val lastColStart = (columns - 1).coerceAtLeast(0) * stepX
                            val rightEdgeStart = lastColStart + cellWidthPx - edgeInset
                            return when {
                                pointerX <= edgeInset -> -1
                                pointerX >= rightEdgeStart -> 1
                                else -> 0
                            }
                        }

                        fun resetDragEdgeAutoPage() {
                            dragEdgeAutoPageSeeded = false
                            dragEdgePageZone = 0
                        }

                        fun tryAutoPageTurn(pointerX: Float) {
                            if (!showPageSwitcher || pageCount <= 1 || dragFromGlobal < 0) return
                            val zone = dragEdgeZone(pointerX)
                            if (!dragEdgeAutoPageSeeded) {
                                dragEdgeAutoPageSeeded = true
                                dragEdgePageZone = zone
                                return
                            }
                            val prevZone = dragEdgePageZone
                            dragEdgePageZone = zone
                            if (zone == 0 || zone == prevZone) return
                            val now = System.currentTimeMillis()
                            if (now - lastAutoPageTurnMs < PAGE_AUTO_TURN_COOLDOWN_MS) return
                            val page = currentPageState.value
                            val delta = when (zone) {
                                -1 -> if (page > 0) -1 else 0
                                1 -> if (page < pageCount - 1) 1 else 0
                                else -> 0
                            }
                            if (delta == 0) return
                            currentPage = page + delta
                            lastAutoPageTurnMs = now
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(currentPage, pageCount, editMode, dragFromGlobal, pageWidthPx, showPageSwitcher) {
                                    if (!showPageSwitcher || editMode || dragFromGlobal >= 0 || pageCount <= 1) {
                                        return@pointerInput
                                    }
                                    detectHorizontalDragGestures(
                                        onDragEnd = { finishPageSwipe() },
                                        onDragCancel = { pageSwipeOffsetPx = 0f },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val nextOffset = pageSwipeOffsetPx + dragAmount
                                            pageSwipeOffsetPx = when {
                                                currentPage == 0 && nextOffset > 0f ->
                                                    pageSwipeOffsetPx + dragAmount * PAGE_EDGE_RESISTANCE
                                                currentPage >= pageCount - 1 && nextOffset < 0f ->
                                                    pageSwipeOffsetPx + dragAmount * PAGE_EDGE_RESISTANCE
                                                else -> nextOffset
                                            }.coerceIn(-pageWidthPx, pageWidthPx)
                                        },
                                    )
                                },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = pageSwipeOffsetPx },
                            ) {
                                QuickLauncherPageGrid(
                                    pageStart = pageStartIndex,
                                    columns = columns,
                                    rows = rows,
                                    pageSize = pageSize,
                                    items = items,
                                    appsByPackage = appsByPackage,
                                    iconBitmapCache = iconBitmapCache,
                                    actionIconTintArgb = actionIconTintArgb,
                                    editMode = editMode,
                                    dragFromGlobal = dragFromGlobal,
                                    dragSlotGlobal = dragSlotGlobal,
                                )
                            }

                            if (pageSwipeOffsetPx < 0f && currentPage < pageCount - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = pageWidthPx + pageSwipeOffsetPx
                                        },
                                ) {
                                    QuickLauncherPageGrid(
                                        pageStart = (currentPage + 1) * pageSize,
                                        columns = columns,
                                        rows = rows,
                                        pageSize = pageSize,
                                        items = items,
                                        appsByPackage = appsByPackage,
                                    iconBitmapCache = iconBitmapCache,
                                    actionIconTintArgb = actionIconTintArgb,
                                    editMode = false,
                                        dragFromGlobal = -1,
                                        dragSlotGlobal = -1,
                                    )
                                }
                            }

                            if (pageSwipeOffsetPx > 0f && currentPage > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = pageSwipeOffsetPx - pageWidthPx
                                        },
                                ) {
                                    QuickLauncherPageGrid(
                                        pageStart = (currentPage - 1) * pageSize,
                                        columns = columns,
                                        rows = rows,
                                        pageSize = pageSize,
                                        items = items,
                                        appsByPackage = appsByPackage,
                                    iconBitmapCache = iconBitmapCache,
                                    actionIconTintArgb = actionIconTintArgb,
                                    editMode = false,
                                        dragFromGlobal = -1,
                                        dragSlotGlobal = -1,
                                    )
                                }
                            }

                            if (editMode) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(1f)
                                        .pointerInput(
                                            editMode,
                                            columns,
                                            rows,
                                            pageSize,
                                            pageCount,
                                            cellWidthPx,
                                            cellHeightPx,
                                            gridGapPx,
                                            pageWidthPx,
                                        ) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { start ->
                                                    dragStartInGrid = start
                                                    val page = currentPageState.value
                                                    val pageStart = page * pageSizeState.value
                                                    val localIndex = QuickLauncherGridLogic.localSlotAt(
                                                        x = start.x,
                                                        y = start.y,
                                                        columns = columns,
                                                        rows = rows,
                                                        pageSize = pageSize,
                                                        cellWidthPx = cellWidthPx,
                                                        cellHeightPx = cellHeightPx,
                                                        gapPx = gridGapPx,
                                                    )
                                                    val globalIndex = pageStart + localIndex
                                                    if (globalIndex in itemsState.value.indices) {
                                                        dragFromGlobal = globalIndex
                                                        dragSlotGlobal = globalIndex
                                                        dragOffsetX = 0f
                                                        dragOffsetY = 0f
                                                        lastAutoPageTurnMs = 0L
                                                        resetDragEdgeAutoPage()
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.GestureThresholdActivate,
                                                        )
                                                    }
                                                },
                                                onDrag = { change, dragAmount ->
                                                    if (dragFromGlobal < 0) {
                                                        return@detectDragGesturesAfterLongPress
                                                    }
                                                    change.consume()
                                                    dragOffsetX += dragAmount.x
                                                    dragOffsetY += dragAmount.y
                                                    val pointerX = dragStartInGrid.x + dragOffsetX
                                                    val pointerY = dragStartInGrid.y + dragOffsetY
                                                    tryAutoPageTurn(pointerX)
                                                    val activePageStart =
                                                        currentPageState.value * pageSizeState.value
                                                    val localSlot = QuickLauncherGridLogic.localSlotAt(
                                                        x = pointerX,
                                                        y = pointerY,
                                                        columns = columns,
                                                        rows = rows,
                                                        pageSize = pageSize,
                                                        cellWidthPx = cellWidthPx,
                                                        cellHeightPx = cellHeightPx,
                                                        gapPx = gridGapPx,
                                                    )
                                                    dragSlotGlobal = QuickLauncherGridLogic.dragSlotGlobal(
                                                        pageStart = activePageStart,
                                                        localSlot = localSlot,
                                                        pageSize = pageSize,
                                                    )
                                                },
                                                onDragEnd = {
                                                    if (dragFromGlobal >= 0 && dragSlotGlobal >= 0) {
                                                        val currentItems = itemsState.value
                                                        val insertIndex =
                                                            QuickLauncherGridLogic.dragInsertIndex(
                                                                dragSlotGlobal = dragSlotGlobal,
                                                                itemCount = currentItems.size,
                                                            )
                                                        if (dragFromGlobal != insertIndex) {
                                                            onItemsChange(
                                                                currentItems.moveIndex(
                                                                    dragFromGlobal,
                                                                    insertIndex,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                    dragFromGlobal = -1
                                                    dragSlotGlobal = -1
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    resetDragEdgeAutoPage()
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.GestureEnd,
                                                    )
                                                },
                                                onDragCancel = {
                                                    dragFromGlobal = -1
                                                    dragSlotGlobal = -1
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    resetDragEdgeAutoPage()
                                                },
                                            )
                                        },
                                )
                                QuickLauncherDeleteButtonLayer(
                                    columns = columns,
                                    pageSize = pageSize,
                                    pageStartIndex = pageStartIndex,
                                    items = items,
                                    dragFromGlobal = dragFromGlobal,
                                    dragSlotGlobal = dragSlotGlobal,
                                    stepX = stepX,
                                    stepY = stepY,
                                    cellWidthPx = cellWidthPx,
                                    density = density,
                                    zIndex = if (dragFromGlobal >= 0) 0.5f else 3f,
                                    onRemoveAt = { globalIndex ->
                                        onItemsChange(
                                            itemsState.value.filterIndexed { i, _ -> i != globalIndex },
                                        )
                                    },
                                )
                            }

                            if (editMode && dragFromGlobal >= 0) {
                                val draggedItem = items.getOrNull(dragFromGlobal)
                                if (draggedItem != null) {
                                    val pointerX = dragStartInGrid.x + dragOffsetX
                                    val pointerY = dragStartInGrid.y + dragOffsetY
                                    val onCurrentPage = dragFromGlobal in pageStartIndex until (pageStartIndex + pageSize)
                                    val floaterX = if (onCurrentPage) {
                                        val localFrom = dragFromGlobal - pageStartIndex
                                        val col = localFrom % columns
                                        col * stepX + dragOffsetX
                                    } else {
                                        pointerX - cellWidthPx / 2f
                                    }
                                    val floaterY = if (onCurrentPage) {
                                        val localFrom = dragFromGlobal - pageStartIndex
                                        val row = localFrom / columns
                                        row * stepY + dragOffsetY
                                    } else {
                                        pointerY - cellHeightPx / 2f
                                    }
                                    QuickLauncherGridCell(
                                        modifier = Modifier
                                            .zIndex(2f)
                                            .offset {
                                                IntOffset(
                                                    floaterX.roundToInt(),
                                                    floaterY.roundToInt(),
                                                )
                                            }
                                            .width(with(density) { cellWidthPx.toDp() }),
                                        item = draggedItem,
                                        appsByPackage = appsByPackage,
                                        iconBitmap = iconBitmapCache[dragFromGlobal],
                                        actionIconTintArgb = actionIconTintArgb,
                                        showEditBadge = false,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                QuickLauncherEditorToolbar(
                    editMode = editMode,
                    onAdd = onAdd,
                    onToggleEdit = { editMode = !editMode },
                )
            }
        }
    }
}
