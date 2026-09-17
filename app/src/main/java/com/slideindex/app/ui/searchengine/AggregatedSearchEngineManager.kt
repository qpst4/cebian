package com.slideindex.app.ui.searchengine

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.SearchEngineConfig
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 聚合搜索管理工作台（系统桌面级交互与严格 MIUIX 规范）：
 * 1. 【真实网格弹簧挤位动画】：拖动图标时，受影响的周围图标以物理差值反向 snap 并通过 spring 弹簧平滑滑向新槽位，绝不重叠卡住；
 * 2. 【行列变化平滑重排】：调整行列数松手时，所有图标平滑滑入新行列槽位；
 * 3. 【彻底消除整页拖拽抖动】：拖拽中保持列表稳定，以累加位移驱动相邻卡片避让，松手统一落盘；
 * 4. 【滑块松手生效】：拖动滑块仅更新本地数字标签，松手后才提交，操作流畅轻快；
 * 5. 【严格 MIUIX 边距】：外圈严格保留 12dp 边距（padding(horizontal = 12.dp)），绝不贴边。
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
    onImportBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val columns = gridColumns.coerceIn(3, 8)
    val rows = gridRows.coerceIn(1, 4)
    val pageSize = columns * rows

    // 本地响应式数据源
    val workingEngines = remember { mutableStateListOf<SearchEngineConfig>() }
    LaunchedEffect(engines) {
        workingEngines.clear()
        workingEngines.addAll(engines)
    }

    val visibleEngines = workingEngines.filter { it.showInPickPanel }
    val hiddenEngines = workingEngines.filter { !it.showInPickPanel }

    val pages = remember(visibleEngines, pageSize) {
        if (visibleEngines.isEmpty()) emptyList()
        else visibleEngines.chunked(pageSize)
    }

    // 根容器全局定位
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // --- 图标拖拽状态 ---
    var draggedEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var dragFingerLocalPos by remember { mutableStateOf(Offset.Zero) }

    // --- 整页拖拽状态（拖拽中保持列表数据稳定，彻底根除死循环抖动）---
    var dragStartPageIndex by remember { mutableIntStateOf(-1) }
    var pageTotalDragY by remember { mutableFloatStateOf(0f) }
    var pageHoverTargetIndex by remember { mutableIntStateOf(-1) }
    var singleCardHeightPx by remember { mutableFloatStateOf(0f) }

    // 槽位物理 Bounds 记录表
    val itemSlotBounds = remember { mutableStateMapOf<String, Rect>() }
    var hiddenCardBounds by remember { mutableStateOf(Rect.Zero) }

    // 快捷管理弹窗
    var actionTargetEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }

    val iconSizeDp = calculateIconSize(columns)
    val iconSizePx = with(density) { iconSizeDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootCoordinates = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 多页平铺卡片（每一页为独立 MiuixCard，外圈 12dp 标准边距）
            if (pages.isEmpty()) {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = "暂无启用的搜索引擎，可从下方已隐藏池添加或点击右下角 + 新建",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                pages.forEachIndexed { pageIndex, pageEngines ->
                    val isBeingDragged = dragStartPageIndex == pageIndex

                    // 计算非拖拽页的弹簧避让位移动画
                    val cardHeight = if (singleCardHeightPx > 50f) singleCardHeightPx + with(density) { 12.dp.toPx() } else with(density) { 200.dp.toPx() }
                    val targetPageOffset = when {
                        isBeingDragged -> pageTotalDragY
                        dragStartPageIndex >= 0 -> {
                            val from = dragStartPageIndex
                            val to = pageHoverTargetIndex
                            if (from < to && pageIndex in (from + 1)..to) {
                                -cardHeight
                            } else if (from > to && pageIndex in to until from) {
                                cardHeight
                            } else {
                                0f
                            }
                        }
                        else -> 0f
                    }

                    val animatedPageOffset by animateIntOffsetAsState(
                        targetValue = if (isBeingDragged) {
                            IntOffset(0, pageTotalDragY.roundToInt())
                        } else {
                            IntOffset(0, targetPageOffset.roundToInt())
                        },
                        animationSpec = if (isBeingDragged) {
                            spring(dampingRatio = 1f, stiffness = 10000f)
                        } else {
                            spring(dampingRatio = 0.82f, stiffness = 380f)
                        },
                        label = "page_reorder_offset_$pageIndex",
                    )

                    MiuixCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isBeingDragged) 10f else 1f)
                            .offset { animatedPageOffset }
                            .scale(if (isBeingDragged) 1.02f else 1f)
                            .shadow(if (isBeingDragged) 12.dp else 0.dp, shape = RoundedCornerShape(18.dp))
                            .onGloballyPositioned { coords ->
                                rootCoordinates?.let { root ->
                                    val bounds = root.localBoundingBoxOf(coords)
                                    if (bounds.height > 50f && dragStartPageIndex < 0) {
                                        singleCardHeightPx = bounds.height
                                    }
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 标头行：长按标头拖拽整页，带迟滞死区与避让动效，零抖动
                            PageHeaderRow(
                                pageIndex = pageIndex,
                                engineCount = pageEngines.size,
                                pageSize = pageSize,
                                onDragStart = {
                                    dragStartPageIndex = pageIndex
                                    pageHoverTargetIndex = pageIndex
                                    pageTotalDragY = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { deltaY ->
                                    pageTotalDragY += deltaY
                                    val h = if (singleCardHeightPx > 50f) singleCardHeightPx else with(density) { 180.dp.toPx() }
                                    val rawFloatIndex = dragStartPageIndex + (pageTotalDragY / h)
                                    val newTarget = rawFloatIndex.roundToInt().coerceIn(0, pages.size - 1)
                                    if (newTarget != pageHoverTargetIndex) {
                                        pageHoverTargetIndex = newTarget
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    val from = dragStartPageIndex
                                    val to = pageHoverTargetIndex
                                    if (from >= 0 && to >= 0 && from != to) {
                                        movePageInWorkingList(workingEngines, from, to, pageSize)
                                        onUpdateEngines(workingEngines.mapIndexed { idx, itm -> itm.copy(sortOrder = idx) })
                                    }
                                    dragStartPageIndex = -1
                                    pageHoverTargetIndex = -1
                                    pageTotalDragY = 0f
                                },
                            )

                            // 该页网格列表（桌面级弹簧挤位让位动画）
                            DesktopReorderableGrid(
                                engines = pageEngines,
                                columns = columns,
                                showLabels = showLabels,
                                iconSize = iconSizeDp,
                                draggedEngineId = draggedEngine?.id,
                                rootCoordinates = rootCoordinates,
                                itemSlotBounds = itemSlotBounds,
                                onDragStart = { engine, touchOffset ->
                                    draggedEngine = engine
                                    val bounds = itemSlotBounds[engine.id] ?: Rect.Zero
                                    dragFingerLocalPos = bounds.topLeft + touchOffset
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { dragAmount ->
                                    dragFingerLocalPos += dragAmount
                                    val currentDragged = draggedEngine ?: return@DesktopReorderableGrid
                                    val fingerPos = dragFingerLocalPos

                                    // 实时碰撞判定，命中目标即触发列表微调，其余图标自动弹簧滑移
                                    val hitEntry = itemSlotBounds.entries.firstOrNull { entry ->
                                        entry.key != currentDragged.id && entry.value.contains(fingerPos)
                                    }
                                    if (hitEntry != null) {
                                        val targetId = hitEntry.key
                                        val fromIdx = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                        val toIdx = workingEngines.indexOfFirst { it.id == targetId }
                                        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                                            val itm = workingEngines.removeAt(fromIdx).copy(showInPickPanel = true)
                                            workingEngines.add(toIdx, itm)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    } else if (hiddenCardBounds.contains(fingerPos)) {
                                        val fromIdx = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                        if (fromIdx >= 0 && workingEngines[fromIdx].showInPickPanel) {
                                            val itm = workingEngines.removeAt(fromIdx).copy(showInPickPanel = false)
                                            workingEngines.add(itm)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggedEngine = null
                                    onUpdateEngines(workingEngines.mapIndexed { idx, it -> it.copy(sortOrder = idx) })
                                },
                                onEngineClick = { actionTargetEngine = it },
                            )
                        }
                    }
                }
            }

            // 2. 已隐藏卡片池
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        rootCoordinates?.let { root ->
                            hiddenCardBounds = root.localBoundingBoxOf(coords)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiuixText(
                            text = "已隐藏",
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        MiuixText(
                            text = "${hiddenEngines.size}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    if (hiddenEngines.isEmpty()) {
                        MiuixText(
                            text = "可将上方引擎拖拽到此处隐藏",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    } else {
                        DesktopReorderableGrid(
                            engines = hiddenEngines,
                            columns = columns,
                            showLabels = showLabels,
                            iconSize = iconSizeDp,
                            draggedEngineId = draggedEngine?.id,
                            rootCoordinates = rootCoordinates,
                            itemSlotBounds = itemSlotBounds,
                            onDragStart = { engine, touchOffset ->
                                draggedEngine = engine
                                val bounds = itemSlotBounds[engine.id] ?: Rect.Zero
                                dragFingerLocalPos = bounds.topLeft + touchOffset
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { dragAmount ->
                                dragFingerLocalPos += dragAmount
                                val currentDragged = draggedEngine ?: return@DesktopReorderableGrid
                                val fingerPos = dragFingerLocalPos

                                val hitEntry = itemSlotBounds.entries.firstOrNull { entry ->
                                    entry.key != currentDragged.id && entry.value.contains(fingerPos)
                                }
                                if (hitEntry != null) {
                                    val targetId = hitEntry.key
                                    val fromIdx = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                    val toIdx = workingEngines.indexOfFirst { it.id == targetId }
                                    if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                                        val isTargetVisible = workingEngines[toIdx].showInPickPanel
                                        val itm = workingEngines.removeAt(fromIdx).copy(showInPickPanel = isTargetVisible)
                                        workingEngines.add(toIdx, itm)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedEngine = null
                                onUpdateEngines(workingEngines.mapIndexed { idx, it -> it.copy(sortOrder = idx) })
                            },
                            onEngineClick = { actionTargetEngine = it },
                        )
                    }
                }
            }

            // 3. 网格布局设置（松手才提交变化，滑动中绝不卡顿重排）
            SmallTitle(text = "网格布局")

            var draftRows by remember(gridRows) { mutableFloatStateOf(rows.toFloat()) }
            var draftColumns by remember(gridColumns) { mutableFloatStateOf(columns.toFloat()) }

            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                SliderPreference(
                    title = "单页行数",
                    summary = "独立搜索页每屏展示的引擎行数（1~4 行）",
                    value = draftRows,
                    valueRange = 1f..4f,
                    steps = 2,
                    valueText = "${draftRows.roundToInt()} 行",
                    onValueChange = { draftRows = it },
                    onValueChangeFinished = {
                        val finalRows = draftRows.roundToInt()
                        if (finalRows != gridRows) {
                            onGridRowsChange(finalRows)
                        }
                    },
                )

                SliderPreference(
                    title = "单页列数",
                    summary = "独立搜索页每行排列的引擎列数（3~8 列）",
                    value = draftColumns,
                    valueRange = 3f..8f,
                    steps = 4,
                    valueText = "${draftColumns.roundToInt()} 列",
                    onValueChange = { draftColumns = it },
                    onValueChangeFinished = {
                        val finalCols = draftColumns.roundToInt()
                        if (finalCols != gridColumns) {
                            onGridColumnsChange(finalCols)
                        }
                    },
                )

                SwitchPreference(
                    title = "显示标签",
                    summary = "在图标下方展示搜索引擎名称",
                    checked = showLabels,
                    onCheckedChange = onShowLabelsChange,
                )
            }

            // 4. 管理与导入（恢复预设搜索引擎库与导入备份，纯正 MIUIX Card）
            SmallTitle(text = "快捷管理")

            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.search_engine_settings_preset_catalog),
                    summary = stringResource(R.string.search_engine_settings_preset_catalog_subtitle),
                    onClick = onPresetCatalog,
                )
                ArrowPreference(
                    title = stringResource(R.string.search_engine_settings_import),
                    summary = stringResource(R.string.search_engine_settings_import_subtitle),
                    onClick = onImportBackup,
                )
            }

            // 底部安全留白
            Spacer(modifier = Modifier.height(72.dp))
        }

        // 5. 手指正下方跟随浮层（中心绝对对准触点）
        draggedEngine?.let { engine ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (dragFingerLocalPos.x - iconSizePx / 2f).roundToInt(),
                            y = (dragFingerLocalPos.y - iconSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(iconSizeDp)
                    .zIndex(999f)
                    .scale(1.15f)
                    .shadow(12.dp, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SearchEngineIcon(
                    engine = engine,
                    modifier = Modifier.size(iconSizeDp),
                )
            }
        }

        // 6. 单项快捷操作弹窗
        actionTargetEngine?.let { target ->
            MiuixEngineActionDialog(
                engine = target,
                onDismiss = { actionTargetEngine = null },
                onToggleVisibility = {
                    val updated = workingEngines.map {
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
    }
}

/**
 * 桌面级自适应重排网格（实现类似桌面/Springboard 的平滑果冻弹簧重排动画）
 */
@Composable
private fun DesktopReorderableGrid(
    engines: List<SearchEngineConfig>,
    columns: Int,
    showLabels: Boolean,
    iconSize: Dp,
    draggedEngineId: String?,
    rootCoordinates: LayoutCoordinates?,
    itemSlotBounds: MutableMap<String, Rect>,
    onDragStart: (SearchEngineConfig, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onEngineClick: (SearchEngineConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val safeCols = columns.coerceAtLeast(1)
    val horizontalSpacingDp = 6.dp
    val verticalSpacingDp = 10.dp
    val horizontalSpacingPx = with(density) { horizontalSpacingDp.toPx() }
    val verticalSpacingPx = with(density) { verticalSpacingDp.toPx() }

    var cellWidthPx by remember { mutableFloatStateOf(0f) }
    var cellHeightPx by remember { mutableFloatStateOf(0f) }

    EngineCustomLayout(
        columns = safeCols,
        horizontalSpacing = horizontalSpacingDp,
        verticalSpacing = verticalSpacingDp,
        onCellSizeMeasured = { w, h ->
            cellWidthPx = w
            cellHeightPx = h
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        engines.forEachIndexed { index, engine ->
            val engineId = engine.id
            val isBeingDragged = engineId == draggedEngineId

            key(engineId) {
                SmoothGridItemWrapper(
                    engine = engine,
                    slotCol = index % safeCols,
                    slotRow = index / safeCols,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    horizontalSpacingPx = horizontalSpacingPx,
                    verticalSpacingPx = verticalSpacingPx,
                    isDragging = isBeingDragged,
                    showLabel = showLabels,
                    iconSize = iconSize,
                    rootCoordinates = rootCoordinates,
                    onBoundsMeasured = { rect -> itemSlotBounds[engineId] = rect },
                    onDragStart = { touchOffset -> onDragStart(engine, touchOffset) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onClick = { onEngineClick(engine) },
                )
            }
        }
    }
}

/**
 * 带有物理位置弹簧差值动画的独立槽位包装器
 * 核心：当数据重排或行列变化导致其 slotX/Y 变动时，自动从上一个物理位置平滑弹射滑向新位置！
 */
@Composable
private fun SmoothGridItemWrapper(
    engine: SearchEngineConfig,
    slotCol: Int,
    slotRow: Int,
    cellWidthPx: Float,
    cellHeightPx: Float,
    horizontalSpacingPx: Float,
    verticalSpacingPx: Float,
    isDragging: Boolean,
    showLabel: Boolean,
    iconSize: Dp,
    rootCoordinates: LayoutCoordinates?,
    onBoundsMeasured: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    val currentSlotX = slotCol * (cellWidthPx + horizontalSpacingPx)
    val currentSlotY = slotRow * (cellHeightPx + verticalSpacingPx)

    var prevSlotX by remember(engine.id) { mutableStateOf<Float?>(null) }
    var prevSlotY by remember(engine.id) { mutableStateOf<Float?>(null) }
    val animOffset = remember(engine.id) { Animatable(Offset.Zero, Offset.VectorConverter) }

    LaunchedEffect(engine.id, currentSlotX, currentSlotY, cellWidthPx, cellHeightPx) {
        val px = prevSlotX
        val py = prevSlotY
        if (px != null && py != null && cellWidthPx > 0 && cellHeightPx > 0 && (px != currentSlotX || py != currentSlotY)) {
            val deltaX = px - currentSlotX
            val deltaY = py - currentSlotY
            // snap 到旧坐标差值，然后 spring 弹簧滑移到目标槽位 0
            animOffset.snapTo(Offset(deltaX, deltaY))
            animOffset.animateTo(
                targetValue = Offset.Zero,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 420f,
                ),
            )
        }
        prevSlotX = currentSlotX
        prevSlotY = currentSlotY
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(animOffset.value.x.roundToInt(), animOffset.value.y.roundToInt()) }
            .onGloballyPositioned { coords ->
                rootCoordinates?.let { root ->
                    onBoundsMeasured(root.localBoundingBoxOf(coords))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        MiuixEngineGridItem(
            engine = engine,
            showLabel = showLabel,
            iconSize = iconSize,
            isDragging = isDragging,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onClick = onClick,
        )
    }
}

/** 单个引擎单元项（挂载在稳定单元节点，pointerInput 永不断裂） */
@Composable
private fun MiuixEngineGridItem(
    engine: SearchEngineConfig,
    showLabel: Boolean,
    iconSize: Dp,
    isDragging: Boolean,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnClick by rememberUpdatedState(onClick)

    val alpha = if (isDragging) 0f else 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart(it) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                )
            }
            .clickable { currentOnClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SearchEngineIcon(
            engine = engine,
            modifier = Modifier.size(iconSize),
        )

        if (showLabel) {
            MiuixText(
                text = engine.name,
                style = MiuixTheme.textStyles.body2,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 页标头行：长按整行触发整页上下拖动调序 */
@Composable
private fun PageHeaderRow(
    pageIndex: Int,
    engineCount: Int,
    pageSize: Int,
    onDragStart: (Offset) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { currentOnDragStart(it) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount.y)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                )
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = "第 ${pageIndex + 1} 页",
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            MiuixText(
                text = "$engineCount/$pageSize",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        Icon(
            imageVector = Icons.Default.SwapVert,
            contentDescription = "长按拖拽调序整页",
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 扁平网格布局（单容器容纳全部单元，实时测量单元尺寸并抛出） */
@Composable
private fun EngineCustomLayout(
    columns: Int,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    onCellSizeMeasured: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        }
        val safeColumns = columns.coerceAtLeast(1)
        val horizontalSpacingPx = horizontalSpacing.roundToPx()
        val verticalSpacingPx = verticalSpacing.roundToPx()
        val totalSpacing = horizontalSpacingPx * (safeColumns - 1)
        val cellWidth = ((constraints.maxWidth - totalSpacing) / safeColumns).coerceAtLeast(0)
        val childConstraints = constraints.copy(minWidth = cellWidth, maxWidth = cellWidth)
        val placeables = measurables.map { it.measure(childConstraints) }

        val rowCount = (placeables.size + safeColumns - 1) / safeColumns
        val rowHeights = IntArray(rowCount) { 0 }
        placeables.forEachIndexed { index, placeable ->
            val r = index / safeColumns
            rowHeights[r] = maxOf(rowHeights[r], placeable.height)
        }

        var totalHeight = 0
        val rowY = IntArray(rowCount) { 0 }
        for (r in 0 until rowCount) {
            rowY[r] = totalHeight
            totalHeight += rowHeights[r]
            if (r < rowCount - 1) totalHeight += verticalSpacingPx
        }

        val maxCellHeight = rowHeights.maxOrNull() ?: 0
        onCellSizeMeasured(cellWidth.toFloat(), maxCellHeight.toFloat())

        layout(constraints.maxWidth, totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { index, placeable ->
                val r = index / safeColumns
                val c = index % safeColumns
                val x = c * (cellWidth + horizontalSpacingPx)
                val y = rowY[r]
                placeable.placeRelative(x, y)
            }
        }
    }
}

/** 移动整页逻辑（安全无缝移动） */
private fun movePageInWorkingList(
    list: MutableList<SearchEngineConfig>,
    fromPage: Int,
    toPage: Int,
    pageSize: Int,
) {
    val visible = list.filter { it.showInPickPanel }
    val hidden = list.filter { !it.showInPickPanel }
    val pages = visible.chunked(pageSize).map { it.toMutableList() }.toMutableList()

    if (fromPage in pages.indices && toPage in pages.indices && fromPage != toPage) {
        val moved = pages.removeAt(fromPage)
        pages.add(toPage, moved)

        val flattened = pages.flatten()
        list.clear()
        list.addAll(flattened + hidden)
    }
}

/** 图标尺寸计算 */
private fun calculateIconSize(columns: Int): Dp = when {
    columns >= 8 -> 32.dp
    columns >= 7 -> 34.dp
    columns >= 6 -> 36.dp
    columns >= 5 -> 38.dp
    else -> 40.dp
}

/** 单个引擎弹窗操作 */
@Composable
private fun MiuixEngineActionDialog(
    engine: SearchEngineConfig,
    onDismiss: () -> Unit,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        MiuixCard(
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SearchEngineIcon(engine = engine, modifier = Modifier.size(40.dp))
                    Column {
                        MiuixText(
                            text = engine.name,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.Bold,
                        )
                        MiuixText(
                            text = if (engine.showInPickPanel) "当前状态：已在面板显示" else "当前状态：已隐藏",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleVisibility)
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = if (engine.showInPickPanel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                        MiuixText(
                            text = if (engine.showInPickPanel) "隐藏此搜索引擎" else "恢复显示在面板中",
                            style = MiuixTheme.textStyles.body1,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onEdit)
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                        MiuixText(
                            text = "编辑搜索引擎信息",
                            style = MiuixTheme.textStyles.body1,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDelete)
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        MiuixText(
                            text = "删除此搜索引擎",
                            style = MiuixTheme.textStyles.body1,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
