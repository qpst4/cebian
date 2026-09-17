package com.slideindex.app.ui.searchengine

import androidx.compose.animation.core.Spring
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
 * 聚合搜索管理工作台（桌面级交互体验）：
 * 1. 【行列调整松手生效】：滑动滑块仅改变数值展示，手势离开（松手）后才提交生效，杜绝滑动过程中的剧烈重绘；
 * 2. 【桌面级图标平滑挤位动画】：拖拽图标时，周围受影响图标使用 Spring 弹簧动画平滑滑移让出槽位，松手平滑归位；
 * 3. 【整页平滑拖拽且根除抖动】：基于累积物理位移绝对投影目标页码，引入迟滞保护死区，彻底杜绝两页来回振荡；
 * 4. 【严苛 MIUIX 规范】：外边距靠齐标准，标题使用 title4，图标文字为 11sp，操作卡片原生风格。
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

    // 本地响应式引擎数据
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

    // --- 图标拖拽状态（桌面级 Slot 挤位动画）---
    var draggedEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var dragFingerLocalPos by remember { mutableStateOf(Offset.Zero) }
    // 拖拽过程中，手指悬停的目标引擎 ID（用于受影响图标平滑让位）
    var hoverTargetEngineId by remember { mutableStateOf<String?>(null) }

    // --- 整页拖拽状态（绝对投影与滞后死区，彻底告别死锁抖动）---
    var dragStartPageIndex by remember { mutableIntStateOf(-1) }
    var pageTotalDragY by remember { mutableFloatStateOf(0f) }
    var pageHoverTargetIndex by remember { mutableIntStateOf(-1) }
    var singleCardHeightPx by remember { mutableFloatStateOf(0f) }

    // 各项 Bounds 登记表
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
            // 1. 多页平铺卡片（每一页为独立 MiuixCard）
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

                    // 计算非拖拽页的避让位移动画（Spring 弹簧）
                    val cardHeight = if (singleCardHeightPx > 50f) singleCardHeightPx + with(density) { 12.dp.toPx() } else with(density) { 200.dp.toPx() }
                    val targetPageOffset = when {
                        isBeingDragged -> pageTotalDragY
                        dragStartPageIndex >= 0 -> {
                            // 当某页正在被拖动，并且目标槽位影响到当前页时，当前页优雅避让
                            val from = dragStartPageIndex
                            val to = pageHoverTargetIndex
                            if (from < to && pageIndex in (from + 1)..to) {
                                -cardHeight // 向上平移腾出位置
                            } else if (from > to && pageIndex in to until from) {
                                cardHeight // 向下平移腾出位置
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
                            spring(dampingRatio = 1f, stiffness = 10000f) // 拖拽中手跟手无延迟
                        } else {
                            spring(dampingRatio = 0.82f, stiffness = 380f) // 避让平滑弹簧
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
                            // 标头行：长按标头拖拽整页，采用物理绝对投影，零抖动
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
                                    // 带有滞后保护的绝对投影计算
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

                            // 该页网格列表（桌面级槽位挤位动画）
                            DesktopReorderableEngineGrid(
                                engines = pageEngines,
                                columns = columns,
                                showLabels = showLabels,
                                iconSize = iconSizeDp,
                                draggedEngineId = draggedEngine?.id,
                                hoverTargetEngineId = hoverTargetEngineId,
                                rootCoordinates = rootCoordinates,
                                itemSlotBounds = itemSlotBounds,
                                onDragStart = { engine, touchOffset ->
                                    draggedEngine = engine
                                    hoverTargetEngineId = engine.id
                                    val bounds = itemSlotBounds[engine.id] ?: Rect.Zero
                                    dragFingerLocalPos = bounds.topLeft + touchOffset
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { dragAmount ->
                                    dragFingerLocalPos += dragAmount
                                    val currentDragged = draggedEngine ?: return@DesktopReorderableEngineGrid
                                    val fingerPos = dragFingerLocalPos

                                    // 检查命中了哪一个槽位
                                    val hitEntry = itemSlotBounds.entries.firstOrNull { entry ->
                                        entry.key != currentDragged.id && entry.value.contains(fingerPos)
                                    }
                                    if (hitEntry != null) {
                                        val newTargetId = hitEntry.key
                                        if (newTargetId != hoverTargetEngineId) {
                                            hoverTargetEngineId = newTargetId
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    } else if (hiddenCardBounds.contains(fingerPos)) {
                                        // 移入已隐藏区域标记
                                        if (hoverTargetEngineId != "__HIDDEN__") {
                                            hoverTargetEngineId = "__HIDDEN__"
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val currentDragged = draggedEngine
                                    val targetId = hoverTargetEngineId
                                    if (currentDragged != null && targetId != null && currentDragged.id != targetId) {
                                        if (targetId == "__HIDDEN__") {
                                            val fromIdx = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                            if (fromIdx >= 0) {
                                                val itm = workingEngines.removeAt(fromIdx).copy(showInPickPanel = false)
                                                workingEngines.add(itm)
                                                onUpdateEngines(workingEngines.mapIndexed { idx, it -> it.copy(sortOrder = idx) })
                                            }
                                        } else {
                                            val fromIdx = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                            val toIdx = workingEngines.indexOfFirst { it.id == targetId }
                                            if (fromIdx >= 0 && toIdx >= 0) {
                                                val itm = workingEngines.removeAt(fromIdx).copy(showInPickPanel = true)
                                                workingEngines.add(toIdx, itm)
                                                onUpdateEngines(workingEngines.mapIndexed { idx, it -> it.copy(sortOrder = idx) })
                                            }
                                        }
                                    }
                                    draggedEngine = null
                                    hoverTargetEngineId = null
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
                        DesktopReorderableEngineGrid(
                            engines = hiddenEngines,
                            columns = columns,
                            showLabels = showLabels,
                            iconSize = iconSizeDp,
                            draggedEngineId = draggedEngine?.id,
                            hoverTargetEngineId = hoverTargetEngineId,
                            rootCoordinates = rootCoordinates,
                            itemSlotBounds = itemSlotBounds,
                            onDragStart = { engine, touchOffset ->
                                draggedEngine = engine
                                hoverTargetEngineId = engine.id
                                val bounds = itemSlotBounds[engine.id] ?: Rect.Zero
                                dragFingerLocalPos = bounds.topLeft + touchOffset
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { dragAmount ->
                                dragFingerLocalPos += dragAmount
                                val currentDragged = draggedEngine ?: return@DesktopReorderableEngineGrid
                                val fingerPos = dragFingerLocalPos

                                val hitEntry = itemSlotBounds.entries.firstOrNull { entry ->
                                    entry.key != currentDragged.id && entry.value.contains(fingerPos)
                                }
                                if (hitEntry != null) {
                                    val newTargetId = hitEntry.key
                                    if (newTargetId != hoverTargetEngineId) {
                                        hoverTargetEngineId = newTargetId
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDragEnd = {
                                val currentDragged = draggedEngine
                                val targetId = hoverTargetEngineId
                                if (currentDragged != null && targetId != null && currentDragged.id != targetId) {
                                    val fromIdx = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                    val toIdx = workingEngines.indexOfFirst { it.id == targetId }
                                    if (fromIdx >= 0 && toIdx >= 0) {
                                        val isTargetVisible = workingEngines[toIdx].showInPickPanel
                                        val itm = workingEngines.removeAt(fromIdx).copy(showInPickPanel = isTargetVisible)
                                        workingEngines.add(toIdx, itm)
                                        onUpdateEngines(workingEngines.mapIndexed { idx, it -> it.copy(sortOrder = idx) })
                                    }
                                }
                                draggedEngine = null
                                hoverTargetEngineId = null
                            },
                            onEngineClick = { actionTargetEngine = it },
                        )
                    }
                }
            }

            // 3. 网格布局设置（松手再提交变化，滑动中绝不频繁触发重排）
            SmallTitle(text = "网格布局")

            // 维护本地 Draft 状态，滑块滑动中仅更新数字显示，松手后才更新全局布局
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

            // 底部留白
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
 * 桌面级重排网格（实现类似 iOS Springboard / Android Launcher 的果冻弹簧挤位动画）
 */
@Composable
private fun DesktopReorderableEngineGrid(
    engines: List<SearchEngineConfig>,
    columns: Int,
    showLabels: Boolean,
    iconSize: Dp,
    draggedEngineId: String?,
    hoverTargetEngineId: String?,
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

    // 记录各槽位测量的基准宽高
    var cellWidthPx by remember { mutableFloatStateOf(0f) }
    var cellHeightPx by remember { mutableFloatStateOf(0f) }

    // 计算各图标在此刻的“目标逻辑槽位索引”
    val fromIdx = engines.indexOfFirst { it.id == draggedEngineId }
    val toIdx = engines.indexOfFirst { it.id == hoverTargetEngineId }

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
        engines.forEachIndexed { originalIndex, engine ->
            val engineId = engine.id
            val isBeingDragged = engineId == draggedEngineId

            // 计算该图标的目标槽位与相对物理偏移量
            val targetSlot = when {
                fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx -> {
                    if (originalIndex == fromIdx) {
                        toIdx
                    } else if (fromIdx < toIdx && originalIndex in (fromIdx + 1)..toIdx) {
                        originalIndex - 1 // 往前挪一位
                    } else if (fromIdx > toIdx && originalIndex in toIdx until fromIdx) {
                        originalIndex + 1 // 往后挪一位
                    } else {
                        originalIndex
                    }
                }
                else -> originalIndex
            }

            // 计算从 originalIndex 到 targetSlot 的像素位移差值
            val origCol = originalIndex % safeCols
            val origRow = originalIndex / safeCols
            val targetCol = targetSlot % safeCols
            val targetRow = targetSlot / safeCols

            val deltaX = (targetCol - origCol) * (cellWidthPx + horizontalSpacingPx)
            val deltaY = (targetRow - origRow) * (cellHeightPx + verticalSpacingPx)

            val animatedOffset by animateIntOffsetAsState(
                targetValue = if (isBeingDragged) IntOffset.Zero else IntOffset(deltaX.roundToInt(), deltaY.roundToInt()),
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
                label = "slot_spring_$engineId",
            )

            Box(
                modifier = Modifier
                    .offset { animatedOffset }
                    .onGloballyPositioned { coords ->
                        rootCoordinates?.let { root ->
                            itemSlotBounds[engineId] = root.localBoundingBoxOf(coords)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                MiuixEngineGridItem(
                    engine = engine,
                    showLabel = showLabels,
                    iconSize = iconSize,
                    isDragging = isBeingDragged,
                    onDragStart = { offset -> onDragStart(engine, offset) },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onClick = { onEngineClick(engine) },
                )
            }
        }
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

    val alpha = if (isDragging) 0.15f else 1f

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
