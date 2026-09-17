package com.slideindex.app.ui.searchengine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.SearchEngineConfig
import kotlin.math.roundToInt

/**
 * 现代风格的“聚合搜索”管理工作台（参考图设计规范）
 * - 平铺多页卡片，Header 显示“第 X 页 16/16 ↕️”
 * - 每一页提供整页调序把手
 * - 独立“已隐藏”卡片池
 * - 实时“网格布局”调节（单页行数、单页列数、显示标签）
 * - 支持图标长按自由拖拽排序 / 拖拽移入移出隐藏
 * - 支持点击图标快捷弹窗调序与管理
 */
@Composable
fun AggregatedSearchEngineManager(
    engines: List<SearchEngineConfig>,
    gridColumns: Int,
    gridRows: Int,
    showLabels: Boolean,
    onUpdateEngines: (List<SearchEngineConfig>) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onGridRowsChange: (Int) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onAddEngine: () -> Unit,
    onEditEngine: (String) -> Unit,
    onDeleteEngine: (String) -> Unit,
    onPresetCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val columns = gridColumns.coerceIn(3, 8)
    val rows = gridRows.coerceIn(1, 4)
    val pageSize = columns * rows

    val visibleEngines = remember(engines) { engines.filter { it.showInPickPanel } }
    val hiddenEngines = remember(engines) { engines.filter { !it.showInPickPanel } }

    val pages = remember(visibleEngines, pageSize) {
        if (visibleEngines.isEmpty()) emptyList()
        else visibleEngines.chunked(pageSize)
    }

    // 拖拽全局状态
    var draggedEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var dragPositionInWindow by remember { mutableStateOf(Offset.Zero) }
    var dragTouchOffset by remember { mutableStateOf(Offset.Zero) }

    // 目标区域矩形注册表：key 为 targetId（"page_P_slot_S" 或 "hidden_pool"）
    val dropTargets = remember { mutableStateMapOf<String, Rect>() }
    var hoveredTargetId by remember { mutableStateOf<String?>(null) }

    // 点击某项后弹出的快捷管理对话框
    var actionTargetEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }

    // 整页调序弹窗
    var reorderPageDialogIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // 占位
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 多页平铺卡片（所有启用的搜索引擎）
            if (pages.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无启用的搜索引擎，可从下方已隐藏池添加或点击右下角 + 新建",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        pages.forEachIndexed { pageIndex, pageEngines ->
                            SearchEnginePageBlock(
                                pageIndex = pageIndex,
                                totalPages = pages.size,
                                pageEngines = pageEngines,
                                pageSize = pageSize,
                                columns = columns,
                                rows = rows,
                                showLabels = showLabels,
                                draggedEngineId = draggedEngine?.id,
                                hoveredTargetId = hoveredTargetId,
                                onRegisterTarget = { id, rect -> dropTargets[id] = rect },
                                onUnregisterTarget = { id -> dropTargets.remove(id) },
                                onDragStart = { engine, pos, touchOff ->
                                    draggedEngine = engine
                                    dragPositionInWindow = pos
                                    dragTouchOffset = touchOff
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragMove = { pos ->
                                    dragPositionInWindow = pos
                                    // 查找命中的目标
                                    val hit = dropTargets.entries.firstOrNull { it.value.contains(pos) }
                                    hoveredTargetId = hit?.key
                                },
                                onDragEnd = {
                                    val currentDragged = draggedEngine
                                    val target = hoveredTargetId
                                    if (currentDragged != null && target != null) {
                                        handleDrop(
                                            dragged = currentDragged,
                                            targetId = target,
                                            engines = engines,
                                            pageSize = pageSize,
                                            onUpdateEngines = onUpdateEngines,
                                        )
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    draggedEngine = null
                                    hoveredTargetId = null
                                },
                                onPageHandleClick = {
                                    reorderPageDialogIndex = pageIndex
                                },
                                onItemClick = { engine ->
                                    actionTargetEngine = engine
                                },
                            )
                        }
                    }
                }
            }

            // 2. 已隐藏卡片池
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        dropTargets["hidden_pool"] = coords.boundsInWindow()
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "已隐藏",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${hiddenEngines.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }

                    if (hiddenEngines.isEmpty()) {
                        Text(
                            text = "可将上方引擎拖拽到此处隐藏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        // 隐藏引擎网格（按 columns 排列）
                        val hiddenRows = hiddenEngines.chunked(columns)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            hiddenRows.forEachIndexed { rIdx, rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    rowItems.forEachIndexed { cIdx, engine ->
                                        val slotId = "hidden_slot_${rIdx * columns + cIdx}"
                                        val isHovered = hoveredTargetId == slotId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .onGloballyPositioned { coords ->
                                                    dropTargets[slotId] = coords.boundsInWindow()
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            SearchEngineGridItem(
                                                engine = engine,
                                                showLabel = showLabels,
                                                iconSize = calculateIconSize(columns),
                                                isDragging = draggedEngine?.id == engine.id,
                                                isHovered = isHovered,
                                                onDragStart = { pos, off ->
                                                    draggedEngine = engine
                                                    dragPositionInWindow = pos
                                                    dragTouchOffset = off
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragMove = { pos ->
                                                    dragPositionInWindow = pos
                                                    val hit = dropTargets.entries.firstOrNull { it.value.contains(pos) }
                                                    hoveredTargetId = hit?.key
                                                },
                                                onDragEnd = {
                                                    val currentDragged = draggedEngine
                                                    val target = hoveredTargetId
                                                    if (currentDragged != null && target != null) {
                                                        handleDrop(
                                                            dragged = currentDragged,
                                                            targetId = target,
                                                            engines = engines,
                                                            pageSize = pageSize,
                                                            onUpdateEngines = onUpdateEngines,
                                                        )
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                    draggedEngine = null
                                                    hoveredTargetId = null
                                                },
                                                onClick = { actionTargetEngine = engine },
                                            )
                                        }
                                    }
                                    repeat(columns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. 网格布局设置卡片
            Text(
                text = "网格布局",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // 单页行数
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "单页行数",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "$rows 行",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "独立搜索页每屏展示的引擎行数（1~4 行）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                        )
                        Slider(
                            value = rows.toFloat(),
                            onValueChange = { onGridRowsChange(it.roundToInt()) },
                            valueRange = 1f..4f,
                            steps = 2,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 单页列数
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "单页列数",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "$columns 列",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "独立搜索页每行排列的引擎列数（3~8 列）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                        )
                        Slider(
                            value = columns.toFloat(),
                            onValueChange = { onGridColumnsChange(it.roundToInt()) },
                            valueRange = 3f..8f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 显示标签
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "显示标签",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "在图标下方展示搜索引擎名称",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        Switch(
                            checked = showLabels,
                            onCheckedChange = onShowLabelsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }

            // 底部安全间距
            Spacer(modifier = Modifier.height(64.dp))
        }

        // 浮动拖拽预览（跟随手指）
        draggedEngine?.let { engine ->
            val iconSize = calculateIconSize(columns)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (dragPositionInWindow.x - dragTouchOffset.x).roundToInt(),
                            y = (dragPositionInWindow.y - dragTouchOffset.y).roundToInt(),
                        )
                    }
                    .size(iconSize + 16.dp)
                    .shadow(16.dp, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                SearchEngineIcon(
                    engine = engine,
                    modifier = Modifier.size(iconSize),
                )
            }
        }

        // 单项快捷操作弹窗
        actionTargetEngine?.let { target ->
            EngineActionDialog(
                engine = target,
                onDismiss = { actionTargetEngine = null },
                onToggleVisibility = {
                    val updated = engines.map {
                        if (it.id == target.id) it.copy(showInPickPanel = !it.showInPickPanel) else it
                    }
                    onUpdateEngines(updated)
                    actionTargetEngine = null
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onEdit = {
                    actionTargetEngine = null
                    onEditEngine(target.id)
                },
                onDelete = {
                    actionTargetEngine = null
                    onDeleteEngine(target.id)
                },
            )
        }

        // 整页调序弹窗
        reorderPageDialogIndex?.let { pageIdx ->
            PageReorderDialog(
                pageIndex = pageIdx,
                totalPages = pages.size,
                onDismiss = { reorderPageDialogIndex = null },
                onMovePage = { direction ->
                    reorderPageDialogIndex = null
                    val targetPage = pageIdx + direction
                    if (targetPage in 0 until pages.size) {
                        movePage(
                            fromPage = pageIdx,
                            toPage = targetPage,
                            pageSize = pageSize,
                            engines = engines,
                            onUpdateEngines = onUpdateEngines,
                        )
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
            )
        }
    }
}

/** 每一页的平铺卡片块 */
@Composable
private fun SearchEnginePageBlock(
    pageIndex: Int,
    totalPages: Int,
    pageEngines: List<SearchEngineConfig>,
    pageSize: Int,
    columns: Int,
    rows: Int,
    showLabels: Boolean,
    draggedEngineId: String?,
    hoveredTargetId: String?,
    onRegisterTarget: (String, Rect) -> Unit,
    onUnregisterTarget: (String) -> Unit,
    onDragStart: (SearchEngineConfig, Offset, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPageHandleClick: () -> Unit,
    onItemClick: (SearchEngineConfig) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 页标头：第 1 页 16/16 ↕️
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "第 ${pageIndex + 1} 页",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${pageEngines.size}/$pageSize",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            // ↕️ 整页调序按钮
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onPageHandleClick() }
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "调序整页",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // 该页图标网格：按 columns 分割
        val iconSize = calculateIconSize(columns)
        val rowChunks = pageEngines.chunked(columns)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowChunks.forEachIndexed { rIdx, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowItems.forEachIndexed { cIdx, engine ->
                        val slotIndex = rIdx * columns + cIdx
                        val slotId = "page_${pageIndex}_slot_$slotIndex"
                        val isHovered = hoveredTargetId == slotId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { coords ->
                                    onRegisterTarget(slotId, coords.boundsInWindow())
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            SearchEngineGridItem(
                                engine = engine,
                                showLabel = showLabels,
                                iconSize = iconSize,
                                isDragging = draggedEngineId == engine.id,
                                isHovered = isHovered,
                                onDragStart = { pos, off -> onDragStart(engine, pos, off) },
                                onDragMove = onDragMove,
                                onDragEnd = onDragEnd,
                                onClick = { onItemClick(engine) },
                            )
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 单个引擎图标与文字单元 */
@Composable
private fun SearchEngineGridItem(
    engine: SearchEngineConfig,
    showLabel: Boolean,
    iconSize: Dp,
    isDragging: Boolean,
    isHovered: Boolean,
    onDragStart: (Offset, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    var itemWindowOffset by remember { mutableStateOf(Offset.Zero) }

    val alpha = if (isDragging) 0.25f else 1f
    val scale = if (isHovered) 1.15f else 1f

    Column(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                itemWindowOffset = coords.boundsInWindow().topLeft
            }
            .pointerInput(engine.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { touchOffset ->
                        onDragStart(itemWindowOffset + touchOffset, touchOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragMove(itemWindowOffset + change.position)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .then(
                    if (isHovered) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            SearchEngineIcon(
                engine = engine,
                modifier = Modifier.size(iconSize),
            )
        }

        if (showLabel) {
            Text(
                text = engine.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

/** 拖拽放置处理 */
private fun handleDrop(
    dragged: SearchEngineConfig,
    targetId: String,
    engines: List<SearchEngineConfig>,
    pageSize: Int,
    onUpdateEngines: (List<SearchEngineConfig>) -> Unit,
) {
    if (targetId == "hidden_pool") {
        // 拖入已隐藏池
        val updated = engines.map {
            if (it.id == dragged.id) it.copy(showInPickPanel = false) else it
        }
        onUpdateEngines(updated)
        return
    }

    if (targetId.startsWith("page_")) {
        // 格式：page_P_slot_S
        val parts = targetId.split("_")
        val pIdx = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val sIdx = parts.getOrNull(3)?.toIntOrNull() ?: 0
        val targetGlobalIndex = (pIdx * pageSize + sIdx).coerceAtLeast(0)

        val visible = engines.filter { it.showInPickPanel }.toMutableList()
        val hidden = engines.filter { !it.showInPickPanel }.toMutableList()

        if (!dragged.showInPickPanel) {
            // 从隐藏池拖入显示页
            hidden.removeAll { it.id == dragged.id }
            val insertPos = targetGlobalIndex.coerceIn(0, visible.size)
            visible.add(insertPos, dragged.copy(showInPickPanel = true))
        } else {
            // 在显示页之间换位置
            visible.removeAll { it.id == dragged.id }
            val insertPos = targetGlobalIndex.coerceIn(0, visible.size)
            visible.add(insertPos, dragged)
        }

        val merged = (visible + hidden).mapIndexed { idx, item ->
            item.copy(sortOrder = idx)
        }
        onUpdateEngines(merged)
    }
}

/** 整页调序 */
private fun movePage(
    fromPage: Int,
    toPage: Int,
    pageSize: Int,
    engines: List<SearchEngineConfig>,
    onUpdateEngines: (List<SearchEngineConfig>) -> Unit,
) {
    val visible = engines.filter { it.showInPickPanel }
    val hidden = engines.filter { !it.showInPickPanel }

    val pages = visible.chunked(pageSize).toMutableList()
    if (fromPage in pages.indices && toPage in pages.indices) {
        val moved = pages.removeAt(fromPage)
        pages.add(toPage, moved)

        val flattened = pages.flatten()
        val merged = (flattened + hidden).mapIndexed { idx, item ->
            item.copy(sortOrder = idx)
        }
        onUpdateEngines(merged)
    }
}

/** 图标尺寸自适应 */
private fun calculateIconSize(columns: Int): Dp = when {
    columns >= 8 -> 32.dp
    columns >= 7 -> 34.dp
    columns >= 6 -> 36.dp
    else -> 40.dp
}

/** 单个引擎快捷管理弹窗 */
@Composable
private fun EngineActionDialog(
    engine: SearchEngineConfig,
    onDismiss: () -> Unit,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SearchEngineIcon(engine = engine, modifier = Modifier.size(40.dp))
                    Column {
                        Text(
                            text = engine.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (engine.showInPickPanel) "当前状态：已在面板显示" else "当前状态：已隐藏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 切换隐藏/显示
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleVisibility() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = if (engine.showInPickPanel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (engine.showInPickPanel) "隐藏此搜索引擎" else "恢复显示在面板中",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // 编辑
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEdit() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "编辑搜索引擎信息",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // 删除
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDelete() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "删除此搜索引擎",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** 整页调序弹窗 */
@Composable
private fun PageReorderDialog(
    pageIndex: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onMovePage: (direction: Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "调序第 ${pageIndex + 1} 页",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pageIndex > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onMovePage(-1) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("将整页上移（变为第 $pageIndex 页）")
                        }
                    }

                    if (pageIndex < totalPages - 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onMovePage(1) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("将整页下移（变为第 ${pageIndex + 2} 页）")
                        }
                    }
                }
            }
        }
    }
}
