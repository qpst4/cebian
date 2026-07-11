package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.withClip
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.layout.visualColumn
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.GestureActionIconBitmap
import com.slideindex.app.util.QuickLauncherIconResolver

internal class QuickLauncherRenderer(
    private val ctrl: QuickLauncherOverlayController,
) {
    private val host get() = ctrl.host

    private val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(230, 255, 255, 255)
    }
    private val cellHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellLongPressHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pageIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cellInitialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val tmpRect = RectF()

    private val quickLauncherGridIconSize get() = host.dp(38f)
    private val quickLauncherGridIconTopInset get() = host.dp(4f)
    private val quickLauncherGridIconLabelGap get() = host.dp(4f)
    private val gridCellInset get() = host.dp(4f)
    private val panelCorner get() = host.dp(18f)

    fun syncSettings(@Suppress("UNUSED_PARAMETER") settings: AppSettings) {
        cellHighlightPaint.color = Color.argb(70, 255, 255, 255)
        cellLongPressHighlightPaint.color = Color.argb(110, 66, 133, 244)
        appLabelPaint.textSize = host.sp(11f)
    }

    fun warmCaches() {
        warmQuickLauncherIconCache()
        warmQuickLauncherShortcutCache()
        warmQuickLauncherActionIconCache()
    }

    fun draw(canvas: Canvas, drawToolbar: Boolean = true) {
        val panelRect = ctrl.quickLauncherPanelRect()
        if (panelRect.isEmpty) return
        ctrl.quickLauncherPagination()
        host.panelGridSession().cellBounds.clear()
        host.panelContentRect().set(panelRect)
        drawQuickLauncherPanelChrome(canvas, panelRect)

        val dragOffset = ctrl.quickLauncherPageDragOffset
        val panelWidth = panelRect.width().coerceAtLeast(1f)
        val pagingActive = ctrl.quickLauncherPageSwipeLocked ||
            ctrl.quickLauncherPageSnapAnimator?.isRunning == true ||
            kotlin.math.abs(dragOffset) > host.dp(0.5f)
        ctrl.quickLauncherLayoutPanelWidth = panelWidth
        val recordCells = !pagingActive
        canvas.withClip(panelRect) {
            drawQuickLauncherPageCells(
                canvas = this,
                panelRect = panelRect,
                pageIndex = ctrl.quickLauncherPageIndex,
                translateX = if (pagingActive) dragOffset else 0f,
                recordCells = recordCells,
            )
            if (pagingActive && kotlin.math.abs(dragOffset) > host.dp(0.5f)) {
                if (dragOffset < 0f && ctrl.quickLauncherPageIndex < ctrl.quickLauncherPageCount - 1) {
                    drawQuickLauncherPageCells(
                        canvas = this,
                        panelRect = panelRect,
                        pageIndex = ctrl.quickLauncherPageIndex + 1,
                        translateX = dragOffset + panelWidth,
                        recordCells = false,
                    )
                }
                if (dragOffset > 0f && ctrl.quickLauncherPageIndex > 0) {
                    drawQuickLauncherPageCells(
                        canvas = this,
                        panelRect = panelRect,
                        pageIndex = ctrl.quickLauncherPageIndex - 1,
                        translateX = dragOffset - panelWidth,
                        recordCells = false,
                    )
                }
            }
        }

        if (ctrl.quickLauncherPanelController.editMode && ctrl.quickLauncherPanelController.isDragging()) {
            drawQuickLauncherEditDragFloater(canvas, panelRect)
        }

        drawQuickLauncherPageIndicator(canvas, panelRect)
        if (drawToolbar) {
            ctrl.quickLauncherPanelController.drawToolbar(canvas, panelRect)
        }
        ctrl.quickLauncherPanelController.layoutDeleteBadges(host.panelGridSession().cellBounds.map { it.second })
        ctrl.quickLauncherPanelController.drawDeleteBadges(canvas)
    }

    private fun warmQuickLauncherIconCache() {
        ctrl.rebuildQuickLauncherAppsByPackage()
        val size = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        ctrl.quickLauncherRootItems().forEach { item ->
            resolveQuickLauncherItemIcon(item, size)
        }
    }

    private fun resolveQuickLauncherItemIcon(item: QuickLauncherItem, size: Int): Bitmap? {
        val key = "${ctrl.quickLauncherItemCacheKey(item)}\u0000$size"
        ctrl.quickLauncherIconCache[key]?.let { return it }
        if (item.type == QuickLauncherItemType.ACTION &&
            QuickLauncherIconResolver.shouldUseGestureVectorIcon(item)
        ) {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
            return GestureActionIconBitmap.get(
                action = action,
                sizePx = size,
                tintArgb = Color.WHITE,
            )?.also { ctrl.quickLauncherIconCache[key] = it }
        }
        return QuickLauncherIconResolver.iconBitmap(
            item = item,
            appsByPackage = ctrl.quickLauncherAppsByPackage,
            size = size,
            context = host.context,
        )?.also { ctrl.quickLauncherIconCache[key] = it }
    }

    private fun warmQuickLauncherShortcutCache() {
        val items = ctrl.quickLauncherRootItems()
        if (items.none { it.type == QuickLauncherItemType.SHORTCUT }) return
        Thread {
            AppShortcutLoader.warmQuickLauncherShortcuts(host.context, items)
        }.start()
    }

    private fun warmQuickLauncherActionIconCache() {
        val sizePx = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        ctrl.quickLauncherRootItems().forEach { item ->
            if (item.type != QuickLauncherItemType.ACTION) return@forEach
            QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                GestureActionIconBitmap.preload(action, sizePx)
            }
        }
    }

    private fun quickLauncherItemLabel(item: QuickLauncherItem): String {
        val cacheKey = ctrl.quickLauncherItemCacheKey(item)
        ctrl.quickLauncherLabelCache[cacheKey]?.let { return it }
        if (ctrl.quickLauncherAppsByPackage.isEmpty()) {
            ctrl.rebuildQuickLauncherAppsByPackage()
        }
        val label = when (item.type) {
            QuickLauncherItemType.APP -> ctrl.quickLauncherAppsByPackage[item.payload]?.label ?: item.label
            QuickLauncherItemType.SHORTCUT -> item.label.ifBlank { "快捷方式" }
            QuickLauncherItemType.ACTION -> item.label.ifBlank { "动作" }
            QuickLauncherItemType.WIDGET -> item.label.ifBlank { "小组件" }
        }
        ctrl.quickLauncherLabelCache[cacheKey] = label
        return label
    }

    private fun quickLauncherItemIcon(item: QuickLauncherItem): Bitmap? {
        val size = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        if (ctrl.quickLauncherAppsByPackage.isEmpty()) {
            ctrl.rebuildQuickLauncherAppsByPackage()
        }
        return resolveQuickLauncherItemIcon(item, size)
    }

    private fun drawQuickLauncherPanelChrome(canvas: Canvas, grid: RectF) {
        panelBgPaint.color = Color.argb(
            (225 * host.settings().panelOpacity).toInt().coerceIn(150, 225),
            48,
            48,
            52,
        )
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)
        letterPaint.textAlign = Paint.Align.LEFT
        letterPaint.color = Color.WHITE
        letterPaint.textSize = host.sp(14f)
        letterPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(
            "快速启动器",
            grid.left + ctrl.quickLauncherGridPadding,
            grid.top + host.dp(16f),
            letterPaint,
        )
        letterPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawQuickLauncherPageCells(
        canvas: Canvas,
        panelRect: RectF,
        pageIndex: Int,
        translateX: Float,
        recordCells: Boolean,
    ) {
        val entries = ctrl.quickLauncherItemsForPage(pageIndex)
        val rootItems = ctrl.quickLauncherRootItems()
        val layer = if (translateX != 0f) canvas.save() else -1
        if (layer >= 0) {
            canvas.translate(translateX, 0f)
        }
        val m = ctrl.quickLauncherColumnsPerPage()
        val appCount = entries.size
        val pageSize = ctrl.quickLauncherPageSize().coerceAtLeast(1)
        val pageStart = pageIndex * pageSize
        val fromGlobal = if (recordCells) ctrl.quickLauncherPanelController.dragSourceGlobal() else -1
        val toGlobal = if (recordCells) ctrl.quickLauncherPanelController.dragDestinationGlobal() else -1
        val itemCount = rootItems.size
        val mappingSize = pageStart + pageSize
        val editDragActive = recordCells &&
            ctrl.quickLauncherPanelController.editMode &&
            fromGlobal >= 0 &&
            toGlobal >= 0
        val dragMapping = if (editDragActive) {
            QuickLauncherGridLogic.displayMapping(
                itemCount = itemCount,
                dragFrom = fromGlobal,
                dragSlotGlobal = toGlobal,
                mappingSize = mappingSize,
            )
        } else {
            null
        }
        if (entries.isEmpty() && dragMapping == null) {
            if (layer >= 0) canvas.restoreToCount(layer)
            return
        }
        val dragSourceIndex = if (recordCells) {
            quickLauncherDragLocalIndexOnPage(
                globalIndex = fromGlobal,
                pageStart = pageStart,
                pageItemCount = pageSize,
            )
        } else {
            -1
        }
        val slotCount = when {
            dragMapping != null -> pageSize
            recordCells && ctrl.quickLauncherPanelController.editMode -> pageSize
            else -> appCount.coerceAtMost(pageSize)
        }
        fun drawCellAt(index: Int) {
            if (index !in 0 until slotCount) return
            val globalHere = pageStart + index
            val item: QuickLauncherItem
            val itemGlobalIndex: Int
            if (dragMapping != null) {
                val showOrig = dragMapping.getOrNull(globalHere) ?: return
                if (showOrig == fromGlobal) return
                item = rootItems.getOrNull(showOrig) ?: return
                itemGlobalIndex = showOrig
            } else {
                if (index !in entries.indices) return
                item = entries[index]
                itemGlobalIndex = globalHere
            }
            val row = index / m
            val visualCol = visualColumn(index, m, slotCount, host.side())
            val left = panelRect.left + ctrl.quickLauncherGridPadding + visualCol * ctrl.quickLauncherCellWidth
            val top = panelRect.top + ctrl.quickLauncherHeaderHeight + ctrl.quickLauncherGridPadding +
                row * ctrl.quickLauncherCellHeight
            val cell = RectF(left, top, left + ctrl.quickLauncherCellWidth, top + ctrl.quickLauncherCellHeight)
            val (offsetX, offsetY) = 0f to 0f
            if (offsetX != 0f || offsetY != 0f) {
                canvas.save()
                canvas.translate(offsetX, offsetY)
            }
            if (recordCells) {
                host.panelGridSession().cellBounds.add(item to cell)
            }
            drawGridCell(
                canvas,
                cell,
                itemGlobalIndex,
                quickLauncherItemLabel(item),
                iconProvider = { quickLauncherItemIcon(item) },
                longPressArmed = recordCells &&
                    itemGlobalIndex == host.panelGridSession().highlightedIndex &&
                    ctrl.quickLauncherLongPressArmed,
                iconSize = quickLauncherGridIconSize,
                iconTopInset = quickLauncherGridIconTopInset,
                iconLabelGap = quickLauncherGridIconLabelGap,
                labelMaxWidth = ctrl.quickLauncherCellWidth - gridCellInset * 2,
            )
            if (offsetX != 0f || offsetY != 0f) {
                canvas.restore()
            }
        }
        for (index in 0 until slotCount) {
            if (index != dragSourceIndex) {
                drawCellAt(index)
            }
        }
        if (dragSourceIndex in 0 until slotCount) {
            drawCellAt(dragSourceIndex)
        }
        if (layer >= 0) {
            canvas.restoreToCount(layer)
        }
    }

    private fun quickLauncherDragLocalIndexOnPage(
        globalIndex: Int,
        pageStart: Int,
        pageItemCount: Int,
    ): Int {
        if (globalIndex < 0 || pageItemCount <= 0) return -1
        if (globalIndex !in pageStart until pageStart + pageItemCount) return -1
        return globalIndex - pageStart
    }

    private fun drawQuickLauncherEditDragFloater(canvas: Canvas, panelRect: RectF) {
        val globalFrom = ctrl.quickLauncherPanelController.dragSourceGlobal()
        val item = ctrl.quickLauncherRootItems().getOrNull(globalFrom) ?: return
        val cx = ctrl.quickLauncherPanelController.dragPointerX()
        val cy = ctrl.quickLauncherPanelController.dragPointerY()
        val halfW = ctrl.quickLauncherCellWidth / 2f
        val halfH = ctrl.quickLauncherCellHeight / 2f
        val cell = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        drawGridCell(
            canvas = canvas,
            cell = cell,
            index = -1,
            label = quickLauncherItemLabel(item),
            iconProvider = { quickLauncherItemIcon(item) },
            iconSize = quickLauncherGridIconSize,
            iconTopInset = quickLauncherGridIconTopInset,
            iconLabelGap = quickLauncherGridIconLabelGap,
            labelMaxWidth = ctrl.quickLauncherCellWidth - gridCellInset * 2,
        )
    }

    private fun drawQuickLauncherPageIndicator(canvas: Canvas, grid: RectF) {
        if (ctrl.quickLauncherPageCount <= 1 || grid.isEmpty) return
        val dotRadius = host.dp(2.5f)
        val dotGap = host.dp(6f)
        val totalWidth = ctrl.quickLauncherPageCount * dotRadius * 2f +
            (ctrl.quickLauncherPageCount - 1) * dotGap
        var cx = grid.centerX() - totalWidth / 2f + dotRadius
        val cy = grid.bottom - host.dp(10f)
        for (page in 0 until ctrl.quickLauncherPageCount) {
            pageIndicatorPaint.color = if (page == ctrl.quickLauncherPageIndex) {
                Color.argb(230, 255, 255, 255)
            } else {
                Color.argb(90, 255, 255, 255)
            }
            canvas.drawCircle(cx, cy, dotRadius, pageIndicatorPaint)
            cx += dotRadius * 2f + dotGap
        }
    }

    private fun drawGridCell(
        canvas: Canvas,
        cell: RectF,
        index: Int,
        label: String,
        iconProvider: () -> Bitmap?,
        longPressArmed: Boolean = false,
        iconSize: Float = quickLauncherGridIconSize,
        iconTopInset: Float = quickLauncherGridIconTopInset,
        iconLabelGap: Float = quickLauncherGridIconLabelGap,
        labelMaxWidth: Float = ctrl.quickLauncherCellWidth - gridCellInset * 2,
    ) {
        if (index == host.panelGridSession().highlightedIndex) {
            tmpRect.set(
                cell.left + host.dp(3f),
                cell.top + host.dp(2f),
                cell.right - host.dp(3f),
                cell.bottom - host.dp(2f),
            )
            val paint = if (longPressArmed) cellLongPressHighlightPaint else cellHighlightPaint
            canvas.drawRoundRect(tmpRect, host.dp(10f), host.dp(10f), paint)
        }
        val icon = iconProvider()
        val iconTop = cell.top + iconTopInset
        val displayLabel = ellipsize(label, labelMaxWidth)
        val labelBaseline = iconTop + iconSize + iconLabelGap - appLabelPaint.fontMetrics.ascent
        val iconCenterX = cell.centerX()
        if (icon != null) {
            tmpRect.set(
                iconCenterX - iconSize / 2f,
                iconTop,
                iconCenterX + iconSize / 2f,
                iconTop + iconSize,
            )
            canvas.drawBitmap(icon, null, tmpRect, iconBitmapPaint)
        } else {
            tmpRect.set(
                iconCenterX - iconSize / 2f,
                iconTop,
                iconCenterX + iconSize / 2f,
                iconTop + iconSize,
            )
            canvas.drawRoundRect(tmpRect, host.dp(10f), host.dp(10f), cellHighlightPaint)
            val initial = displayLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "•"
            cellInitialPaint.textSize = host.sp(14f)
            canvas.drawText(
                initial,
                iconCenterX,
                iconTop + iconSize / 2f - (cellInitialPaint.descent() + cellInitialPaint.ascent()) / 2f,
                cellInitialPaint,
            )
        }
        canvas.drawText(displayLabel, iconCenterX, labelBaseline, appLabelPaint)
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (appLabelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && appLabelPaint.measureText(text.substring(0, end) + "\u2026") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "\u2026"
    }
}
