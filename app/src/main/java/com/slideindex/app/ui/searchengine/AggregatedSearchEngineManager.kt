package com.slideindex.app.ui.searchengine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.ui.miuix.MiuixSettingsTipCard
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 现代风格的“聚合搜索”管理工作台（全面采用 MIUIX 规范与参考图标准）：
 * 1. 顶部提示小卡片（MiuixSettingsTipCard）
 * 2. 平铺多页卡片，Header 支持长按整行直接上下拖拽调序整页
 * 3. 拖拽图标完全消灭偏移，精准居中于手指正下方
 * 4. 实时动态换位（Live Drag-to-Reorder），拖拽时图标实时挤开让位
 * 5. 独立“已隐藏”卡片池，支持跨卡片自由移入移出
 * 6. MIUIX 规范的网格布局卡片（SliderPreference / SwitchPreference）
 * 7. MIUIX 规范的预设引擎库和导入备份卡片
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

    // 本地响应式列表（用于实时挤位动画与拖拽即时重排）
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

    // 根容器全局定位（用于精准将手指触点转化为局部相对坐标）
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // 图标拖拽全局状态
    var draggedEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var dragFingerLocalPos by remember { mutableStateOf(Offset.Zero) }

    // 整页拖拽状态
    var draggingPageIndex by remember { mutableIntStateOf(-1) }
    var dragPageFingerLocalY by remember { mutableFloatStateOf(0f) }

    // 各项 Bounds
    val itemSlotBounds = remember { mutableStateMapOf<String, Rect>() }
    val pageHeaderBounds = remember { mutableStateMapOf<Int, Rect>() }
    var hiddenCardBounds by remember { mutableStateOf(Rect.Zero) }

    // 快捷管理弹窗
    var actionTargetEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }

    val iconSizeDp = calculateIconSize(columns)
    val iconSizePx = with(density) { iconSizeDp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoordinates = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 0. 顶部描述提示小卡片（MIUIX 风格）
            MiuixSettingsTipCard(
                text = "长按图标可自由跨页拖拽调序或移入已隐藏；长按“第 X 页”标头可直接上下拖动调整整页顺序。",
            )

            // 1. 多页平铺卡片（已启用的搜索引擎）
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
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        pages.forEachIndexed { pageIndex, pageEngines ->
                            val isThisPageDragging = draggingPageIndex == pageIndex

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (isThisPageDragging) 0.35f else 1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // 标头行：长按整行可直接上下拖动调序整页！
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .onGloballyPositioned { coords ->
                                            rootCoordinates?.let { root ->
                                                pageHeaderBounds[pageIndex] = root.localBoundingBoxOf(coords)
                                            }
                                        }
                                        .pointerInput(pageIndex, pages.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { touchOffset ->
                                                    draggingPageIndex = pageIndex
                                                    val pageBounds = pageHeaderBounds[pageIndex] ?: Rect.Zero
                                                    dragPageFingerLocalY = pageBounds.top + touchOffset.y
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragPageFingerLocalY += dragAmount.y

                                                    val curIdx = draggingPageIndex
                                                    if (curIdx in 0 until pages.size) {
                                                        // 向上拖动检测
                                                        if (curIdx > 0) {
                                                            val prevBounds = pageHeaderBounds[curIdx - 1]
                                                            if (prevBounds != null && dragPageFingerLocalY < prevBounds.bottom) {
                                                                swapPagesInWorkingList(workingEngines, curIdx, curIdx - 1, pageSize)
                                                                draggingPageIndex = curIdx - 1
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                        }
                                                        // 向下拖动检测
                                                        if (curIdx < pages.size - 1) {
                                                            val nextBounds = pageHeaderBounds[curIdx + 1]
                                                            if (nextBounds != null && dragPageFingerLocalY > nextBounds.top) {
                                                                swapPagesInWorkingList(workingEngines, curIdx, curIdx + 1, pageSize)
                                                                draggingPageIndex = curIdx + 1
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggingPageIndex = -1
                                                    onUpdateEngines(workingEngines.mapIndexed { idx, itm -> itm.copy(sortOrder = idx) })
                                                },
                                                onDragCancel = {
                                                    draggingPageIndex = -1
                                                    onUpdateEngines(workingEngines.mapIndexed { idx, itm -> itm.copy(sortOrder = idx) })
                                                },
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
                                            style = MiuixTheme.textStyles.title2,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                        MiuixText(
                                            text = "${pageEngines.size}/$pageSize",
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }

                                    // ↕️ 按钮指示
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = "长按拖拽调序整页",
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                // 该页网格列表（带手指无偏移精确定位 + 实时挤位重排）
                                val pageRowChunks = pageEngines.chunked(columns)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    pageRowChunks.forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                        ) {
                                            rowItems.forEach { engine ->
                                                val engineId = engine.id
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
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
                                                        iconSize = iconSizeDp,
                                                        isDragging = draggedEngine?.id == engine.id,
                                                        onDragStart = { touchOffset ->
                                                            draggedEngine = engine
                                                            val itemBounds = itemSlotBounds[engineId] ?: Rect.Zero
                                                            // 将手指触点精准映射到根容器坐标，零偏移！
                                                            dragFingerLocalPos = itemBounds.topLeft + touchOffset
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        },
                                                        onDrag = { dragAmount ->
                                                            dragFingerLocalPos += dragAmount

                                                            // 实时换位判定（Live Reorder）
                                                            val currentDragged = draggedEngine ?: return@MiuixEngineGridItem
                                                            val fingerPos = dragFingerLocalPos

                                                            // 检查是否划入另一个引擎 slot
                                                            val hitEntry = itemSlotBounds.entries.firstOrNull { entry ->
                                                                entry.key != currentDragged.id && entry.value.contains(fingerPos)
                                                            }

                                                            if (hitEntry != null) {
                                                                val targetId = hitEntry.key
                                                                val fromIndex = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                                                val toIndex = workingEngines.indexOfFirst { it.id == targetId }
                                                                if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                                                                    val item = workingEngines.removeAt(fromIndex).copy(showInPickPanel = true)
                                                                    workingEngines.add(toIndex, item)
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
                                                            } else if (hiddenCardBounds.contains(fingerPos)) {
                                                                val fromIndex = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                                                if (fromIndex >= 0 && workingEngines[fromIndex].showInPickPanel) {
                                                                    val item = workingEngines.removeAt(fromIndex).copy(showInPickPanel = false)
                                                                    workingEngines.add(item)
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            draggedEngine = null
                                                            onUpdateEngines(workingEngines.mapIndexed { idx, itm -> itm.copy(sortOrder = idx) })
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
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
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
                        val hiddenRows = hiddenEngines.chunked(columns)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            hiddenRows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    rowItems.forEach { engine ->
                                        val engineId = engine.id
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
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
                                                iconSize = iconSizeDp,
                                                isDragging = draggedEngine?.id == engine.id,
                                                onDragStart = { touchOffset ->
                                                    draggedEngine = engine
                                                    val itemBounds = itemSlotBounds[engineId] ?: Rect.Zero
                                                    dragFingerLocalPos = itemBounds.topLeft + touchOffset
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { dragAmount ->
                                                    dragFingerLocalPos += dragAmount

                                                    val currentDragged = draggedEngine ?: return@MiuixEngineGridItem
                                                    val fingerPos = dragFingerLocalPos

                                                    // 从隐藏池拖往显示区域 slot
                                                    val hitEntry = itemSlotBounds.entries.firstOrNull { entry ->
                                                        entry.key != currentDragged.id && entry.value.contains(fingerPos)
                                                    }

                                                    if (hitEntry != null) {
                                                        val targetId = hitEntry.key
                                                        val fromIndex = workingEngines.indexOfFirst { it.id == currentDragged.id }
                                                        val toIndex = workingEngines.indexOfFirst { it.id == targetId }
                                                        if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                                                            val isTargetVisible = workingEngines[toIndex].showInPickPanel
                                                            val item = workingEngines.removeAt(fromIndex).copy(showInPickPanel = isTargetVisible)
                                                            workingEngines.add(toIndex, item)
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggedEngine = null
                                                    onUpdateEngines(workingEngines.mapIndexed { idx, itm -> itm.copy(sortOrder = idx) })
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

            // 3. 网格布局设置（纯正 MIUIX 规范）
            SmallTitle(text = "网格布局")

            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                SliderPreference(
                    title = "单页行数",
                    summary = "独立搜索页每屏展示的引擎行数（1~4 行）",
                    value = rows.toFloat(),
                    valueRange = 1f..4f,
                    steps = 2,
                    valueText = "$rows 行",
                    onValueChange = { onGridRowsChange(it.roundToInt()) },
                )

                SliderPreference(
                    title = "单页列数",
                    summary = "独立搜索页每行排列的引擎列数（3~8 列）",
                    value = columns.toFloat(),
                    valueRange = 3f..8f,
                    steps = 4,
                    valueText = "$columns 列",
                    onValueChange = { onGridColumnsChange(it.roundToInt()) },
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

        // 5. 严格锁定手指正下方的跟随浮层（中心绝对居中于触点）
        draggedEngine?.let { engine ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (dragFingerLocalPos.x - iconSizePx / 2f).roundToInt(),
                            y = (dragFingerLocalPos.y - iconSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(iconSizeDp + 16.dp)
                    .shadow(16.dp, RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(8.dp),
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

/** 单个引擎单元项 */
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
    val alpha = if (isDragging) 0.15f else 1f

    Column(
        modifier = Modifier
            .pointerInput(engine.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = onDragStart,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .clickable(onClick = onClick)
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
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 整页交换逻辑 */
private fun swapPagesInWorkingList(
    list: MutableList<SearchEngineConfig>,
    pageA: Int,
    pageB: Int,
    pageSize: Int,
) {
    val visible = list.filter { it.showInPickPanel }
    val hidden = list.filter { !it.showInPickPanel }

    val pages = visible.chunked(pageSize).toMutableList()
    if (pageA in pages.indices && pageB in pages.indices && pageA != pageB) {
        val temp = pages[pageA]
        pages[pageA] = pages[pageB]
        pages[pageB] = temp

        val flattened = pages.flatten()
        list.clear()
        list.addAll(flattened + hidden)
    }
}

/** 图标尺寸 */
private fun calculateIconSize(columns: Int): Dp = when {
    columns >= 8 -> 32.dp
    columns >= 7 -> 34.dp
    columns >= 6 -> 36.dp
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SearchEngineIcon(engine = engine, modifier = Modifier.size(40.dp))
                    Column {
                        MiuixText(
                            text = engine.name,
                            style = MiuixTheme.textStyles.title2,
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
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleVisibility() }
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
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEdit() }
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
