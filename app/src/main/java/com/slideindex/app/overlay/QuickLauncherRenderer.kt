package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.withClip
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherLabels
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.launcher.showsShortcutBadge
import com.slideindex.app.overlay.layout.visualColumn
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.QuickLauncherDisplaySettings
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
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cellInitialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val folderTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        color = Color.WHITE
    }
    private val folderSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
    }
    private val folderButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }
    private val folderEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(160, 255, 255, 255)
    }
    private val tmpRect = RectF()
    private val iconClipPath = Path()

    private val quickLauncherDisplay get() = host.settings().quickLauncherDisplay
    private val quickLauncherGridIconSize get() =
        host.dp(quickLauncherDisplay.iconSizeDp.toFloat())
    private val quickLauncherIconShape get() = quickLauncherDisplay.iconShape
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
        Thread {
            warmQuickLauncherIconCache()
            warmQuickLauncherShortcutCache()
            warmQuickLauncherActionIconCache()
        }.start()
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
            ctrl.quickLauncherPageSnapMotion.isRunning ||
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

        if (ctrl.folderOpen) {
            drawQuickLauncherFolderOverlay(canvas, panelRect)
        }
    }

    private fun warmQuickLauncherIconCache() {
        ctrl.rebuildQuickLauncherAppsByPackage()
        val size = quickLauncherGridIconSize.toInt().coerceAtLeast(1)
        ctrl.quickLauncherRootItems().forEach { item ->
            resolveQuickLauncherItemIcon(item, size)
        }
    }

    private fun resolveQuickLauncherItemIcon(item: QuickLauncherItem, size: Int): Bitmap? {
        val catalogStamp = host.settings().activityShortcuts
            .joinToString(";") { "${it.identityKey()}:${it.iconPath.orEmpty()}" }
        val shellStamp = host.settings().shellCommands
            .joinToString(";") { "${it.id}:${it.iconType}:${it.iconPath.orEmpty()}:${it.textIcon.orEmpty()}" }
        val key = "${ctrl.quickLauncherItemCacheKey(item)}\u0000$size\u0000${quickLauncherIconShape}\u0000v34\u0000$catalogStamp\u0000$shellStamp"
        ctrl.quickLauncherIconCache[key]?.let { return it }
        val shellCommands = host.settings().shellCommands
        if (item.type == QuickLauncherItemType.ACTION &&
            QuickLauncherIconResolver.shouldUseGestureVectorIcon(item, shellCommands)
        ) {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
            return GestureActionIconBitmap.get(
                action = action,
                sizePx = size,
                tintArgb = Color.WHITE,
                outlined = true,
                withPlate = true,
            ).also { ctrl.quickLauncherIconCache[key] = it }
        }
        return QuickLauncherIconResolver.iconBitmap(
            item = item,
            appsByPackage = ctrl.quickLauncherAppsByPackage,
            size = size,
            context = host.context,
            activityShortcuts = host.settings().activityShortcuts,
            shellCommands = shellCommands,
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
                GestureActionIconBitmap.preload(action, sizePx, outlined = true)
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
            QuickLauncherItemType.SHORTCUT,
            QuickLauncherItemType.ACTION,
            QuickLauncherItemType.WIDGET,
            QuickLauncherItemType.FOLDER,
            -> QuickLauncherLabels.resolveLabel(host.context, item, ctrl.quickLauncherAppsByPackage)
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

    private val frostedGlassDrawable = LocalFrostedGlassDrawable { host.overlayView() }

    private fun drawQuickLauncherPanelChrome(canvas: Canvas, grid: RectF) {
        val alpha = QuickLauncherDisplaySettings.backgroundAlphaArgb(
            host.settings().quickLauncherDisplay.backgroundOpacityPercent,
        )
        val blurRadiusDp = host.settings().quickLauncherDisplay.blurRadiusDp
        val blurDrawn = if (blurRadiusDp <= 0) {
            false
        } else {
            frostedGlassDrawable.draw(
                canvas = canvas,
                bounds = grid,
                cornerRadiusPx = panelCorner,
                blurRadiusPx = host.dp(blurRadiusDp.toFloat()).toInt(),
                tintColor = Color.argb(alpha, 48, 48, 52),
            )
        }
        if (!blurDrawn) {
            panelBgPaint.color = Color.argb(alpha, 48, 48, 52)
            canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)
        }
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
        val mergeTargetGlobal = if (recordCells) ctrl.quickLauncherPanelController.dragMergeTargetGlobal() else -1
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
                mergeTargetGlobal = mergeTargetGlobal,
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
            if (dragMapping != null) {
                val showOrig = dragMapping.getOrNull(globalHere) ?: return
                if (showOrig == fromGlobal) return
                item = rootItems.getOrNull(showOrig) ?: return
            } else {
                if (index !in entries.indices) return
                item = entries[index]
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
            val isMergeTarget = recordCells && mergeTargetGlobal >= 0 && globalHere == mergeTargetGlobal
            if (isMergeTarget) {
                val corner = host.dp(12f)
                cellHighlightPaint.style = Paint.Style.FILL
                cellHighlightPaint.color = Color.argb(80, 100, 160, 255)
                canvas.drawRoundRect(cell, corner, corner, cellHighlightPaint)
                cellHighlightPaint.style = Paint.Style.STROKE
                cellHighlightPaint.strokeWidth = host.dp(2f)
                cellHighlightPaint.color = Color.argb(230, 100, 180, 255)
                canvas.drawRoundRect(cell, corner, corner, cellHighlightPaint)
                cellHighlightPaint.style = Paint.Style.FILL
            }
            drawGridCell(
                canvas,
                cell,
                index,
                quickLauncherItemLabel(item),
                iconProvider = { quickLauncherItemIcon(item) },
                showShortcutBadge = item.showsShortcutBadge(),
                showShellCommandBadge = item.showsShellCommandBadge(host.settings().shellCommands),
                longPressArmed = recordCells &&
                    index == host.panelGridSession().highlightedIndex &&
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
        val isMerging = ctrl.quickLauncherPanelController.dragMergeTargetGlobal() >= 0
        val scale = if (isMerging) 0.90f else 1.10f
        val halfW = (ctrl.quickLauncherCellWidth * scale) / 2f
        val halfH = (ctrl.quickLauncherCellHeight * scale) / 2f
        val cell = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

        val shadowBlur = host.dp(6f)
        val shadowLayers = 3
        val shadowAlpha = 50
        val shadowRect = RectF()
        for (layer in shadowLayers downTo 1) {
            val fraction = layer / shadowLayers.toFloat()
            val spread = shadowBlur * fraction
            val alpha = (shadowAlpha * fraction * fraction / shadowLayers).toInt().coerceIn(1, 255)
            cellHighlightPaint.color = Color.argb(alpha, 0, 0, 0)
            shadowRect.set(
                cell.left - spread,
                cell.top - spread + host.dp(3f),
                cell.right + spread,
                cell.bottom + spread + host.dp(3f),
            )
            canvas.drawRoundRect(shadowRect, host.dp(14f), host.dp(14f), cellHighlightPaint)
        }

        drawGridCell(
            canvas = canvas,
            cell = cell,
            index = -1,
            label = quickLauncherItemLabel(item),
            iconProvider = { quickLauncherItemIcon(item) },
            showShortcutBadge = item.showsShortcutBadge(),
            showShellCommandBadge = item.showsShellCommandBadge(host.settings().shellCommands),
            iconSize = quickLauncherGridIconSize * scale,
            iconTopInset = quickLauncherGridIconTopInset * scale,
            iconLabelGap = quickLauncherGridIconLabelGap * scale,
            labelMaxWidth = (ctrl.quickLauncherCellWidth - gridCellInset * 2) * scale,
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
        showShortcutBadge: Boolean = false,
        showShellCommandBadge: Boolean = false,
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
        val iconCenterY = iconTop + iconSize / 2f
        tmpRect.set(
            iconCenterX - iconSize / 2f,
            iconTop,
            iconCenterX + iconSize / 2f,
            iconTop + iconSize,
        )
        QuickLauncherIconMask.pathFor(
            shape = quickLauncherIconShape,
            bounds = tmpRect,
            out = iconClipPath,
        )
        if (icon != null) {
            if (quickLauncherIconShape == QuickLauncherDisplaySettings.ICON_SHAPE_DEFAULT) {
                canvas.drawBitmap(icon, null, tmpRect, iconBitmapPaint)
            } else {
                canvas.withClip(iconClipPath) {
                    drawBitmap(icon, null, tmpRect, iconBitmapPaint)
                }
            }
        } else {
            cellHighlightPaint.color = Color.argb(130, 48, 48, 52)
            canvas.drawPath(iconClipPath, cellHighlightPaint)
            cellHighlightPaint.color = Color.argb(90, 255, 255, 255)
            cellHighlightPaint.style = Paint.Style.STROKE
            cellHighlightPaint.strokeWidth = host.dp(1f)
            canvas.drawPath(iconClipPath, cellHighlightPaint)
            cellHighlightPaint.style = Paint.Style.FILL

            val initial = displayLabel.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
            cellInitialPaint.textSize = host.sp(15f)
            cellInitialPaint.color = Color.argb(220, 255, 255, 255)
            cellInitialPaint.isFakeBoldText = true
            canvas.drawText(
                initial,
                iconCenterX,
                iconCenterY - (cellInitialPaint.descent() + cellInitialPaint.ascent()) / 2f,
                cellInitialPaint,
            )
        }
        if (showShortcutBadge) {
            ShortcutBadgeRenderer.draw(
                canvas,
                iconCenterX,
                iconCenterY,
                iconSize,
                1f,
                host.dp(1f),
            )
        } else if (showShellCommandBadge) {
            ShellCommandBadgeRenderer.draw(
                canvas,
                iconCenterX,
                iconCenterY,
                iconSize,
                1f,
                host.dp(1f),
            )
        }
        canvas.drawText(displayLabel, iconCenterX, labelBaseline, appLabelPaint)
    }

    private fun drawQuickLauncherFolderOverlay(canvas: Canvas, panelRect: RectF) {
        val folder = ctrl.folderItem ?: return
        val children = ctrl.folderSubPanelItems
        val childCount = children.size

        // Scrim over background
        cellHighlightPaint.color = Color.argb(130, 0, 0, 0)
        canvas.drawRoundRect(panelRect, panelCorner, panelCorner, cellHighlightPaint)

        val columns = ctrl.quickLauncherColumnsPerPage().coerceIn(2, 4)
        val rows = ((childCount + columns - 1) / columns).coerceIn(1, 4)
        val headerHeight = host.dp(44f)
        val padding = host.dp(10f)
        val cellW = (panelRect.width() - padding * 2f) / columns.toFloat()
        val cellH = ctrl.quickLauncherCellHeight
        val folderH = headerHeight + rows * cellH + padding
        val maxH = panelRect.height() - host.dp(24f)
        val clampedH = folderH.coerceAtMost(maxH)

        val folderLeft = panelRect.left + host.dp(6f)
        val folderRight = panelRect.right - host.dp(6f)
        val folderTop = (panelRect.centerY() - clampedH / 2f).coerceIn(
            panelRect.top + host.dp(10f),
            panelRect.bottom - clampedH - host.dp(10f),
        )
        val folderBottom = folderTop + clampedH
        ctrl.folderRect.set(folderLeft, folderTop, folderRight, folderBottom)

        // Draw card background
        val folderCorner = host.dp(20f)
        val blurRadiusDp = host.settings().quickLauncherDisplay.blurRadiusDp
        val blurDrawn = if (blurRadiusDp <= 0) {
            false
        } else {
            frostedGlassDrawable.draw(
                canvas = canvas,
                bounds = ctrl.folderRect,
                cornerRadiusPx = folderCorner,
                blurRadiusPx = host.dp(blurRadiusDp.toFloat()).toInt(),
                tintColor = Color.argb(235, 34, 34, 38),
            )
        }
        if (!blurDrawn) {
            panelBgPaint.color = Color.argb(235, 34, 34, 38)
            canvas.drawRoundRect(ctrl.folderRect, folderCorner, folderCorner, panelBgPaint)
        }
        cellHighlightPaint.color = Color.argb(45, 255, 255, 255)
        cellHighlightPaint.style = Paint.Style.STROKE
        cellHighlightPaint.strokeWidth = host.dp(1f)
        canvas.drawRoundRect(ctrl.folderRect, folderCorner, folderCorner, cellHighlightPaint)
        cellHighlightPaint.style = Paint.Style.FILL

        // Header: Title & Close / Add buttons
        val title = folder.label.ifBlank { host.context.getString(R.string.quick_launcher_item_folder) }
        folderTitlePaint.textSize = host.sp(14f)
        folderTitlePaint.color = Color.WHITE
        val titleX = folderLeft + host.dp(14f)
        val titleY = folderTop + host.dp(28f)
        canvas.drawText(title, titleX, titleY, folderTitlePaint)

        val titleW = folderTitlePaint.measureText(title)
        val subtitle = host.context.getString(R.string.quick_launcher_folder_items_count, childCount)
        folderSubtitlePaint.textSize = host.sp(11f)
        canvas.drawText(subtitle, titleX + titleW + host.dp(8f), titleY, folderSubtitlePaint)

        // Close button (X)
        val closeBtnR = host.dp(12f)
        val closeCenterX = folderRight - host.dp(20f)
        val closeCenterY = folderTop + host.dp(22f)
        ctrl.folderCloseButtonBounds.set(
            closeCenterX - closeBtnR - host.dp(4f),
            closeCenterY - closeBtnR - host.dp(4f),
            closeCenterX + closeBtnR + host.dp(4f),
            closeCenterY + closeBtnR + host.dp(4f),
        )
        cellHighlightPaint.color = Color.argb(60, 255, 255, 255)
        canvas.drawCircle(closeCenterX, closeCenterY, closeBtnR, cellHighlightPaint)
        folderButtonPaint.textSize = host.sp(12f)
        folderButtonPaint.isFakeBoldText = true
        canvas.drawText("✕", closeCenterX, closeCenterY - (folderButtonPaint.descent() + folderButtonPaint.ascent()) / 2f, folderButtonPaint)

        // Edit Mode: "+ 添加" button
        if (ctrl.quickLauncherPanelController.editMode) {
            val addBtnW = host.dp(46f)
            val addBtnH = host.dp(24f)
            val addBtnRight = closeCenterX - closeBtnR - host.dp(10f)
            val addBtnLeft = addBtnRight - addBtnW
            val addBtnTop = folderTop + host.dp(10f)
            val addBtnBottom = addBtnTop + addBtnH
            ctrl.folderAddButtonBounds.set(addBtnLeft, addBtnTop, addBtnRight, addBtnBottom)
            cellHighlightPaint.color = Color.argb(80, 66, 133, 244)
            canvas.drawRoundRect(ctrl.folderAddButtonBounds, host.dp(12f), host.dp(12f), cellHighlightPaint)
            folderButtonPaint.textSize = host.sp(11f)
            folderButtonPaint.isFakeBoldText = false
            canvas.drawText("+ 添加", ctrl.folderAddButtonBounds.centerX(), ctrl.folderAddButtonBounds.centerY() - (folderButtonPaint.descent() + folderButtonPaint.ascent()) / 2f, folderButtonPaint)
        } else {
            ctrl.folderAddButtonBounds.setEmpty()
        }

        // Child Cells
        ctrl.folderCellBounds.clear()
        if (children.isEmpty()) {
            val emptyText = host.context.getString(R.string.quick_launcher_folder_empty)
            folderEmptyPaint.textSize = host.sp(12f)
            canvas.drawText(
                emptyText,
                ctrl.folderRect.centerX(),
                ctrl.folderRect.centerY() + host.dp(10f),
                folderEmptyPaint,
            )
        } else {
            val startY = folderTop + headerHeight
            for (i in children.indices) {
                val child = children[i]
                val col = i % columns
                val row = i / columns
                val visualCol = visualColumn(col, columns, columns, host.side())
                val cLeft = folderLeft + padding + visualCol * cellW
                val cTop = startY + row * cellH
                val cRect = RectF(cLeft, cTop, cLeft + cellW, cTop + cellH)
                ctrl.folderCellBounds.add(child to cRect)

                drawGridCell(
                    canvas = canvas,
                    cell = cRect,
                    index = if (i == ctrl.folderHighlightLocalIndex) host.panelGridSession().highlightedIndex else -2,
                    label = quickLauncherItemLabel(child),
                    iconProvider = { quickLauncherItemIcon(child) },
                    showShortcutBadge = child.showsShortcutBadge(),
                    showShellCommandBadge = child.showsShellCommandBadge(host.settings().shellCommands),
                    longPressArmed = i == ctrl.folderHighlightLocalIndex && ctrl.folderLongPressArmed,
                    labelMaxWidth = cellW - gridCellInset * 2,
                )

                // If in edit mode, draw delete badge for child
                if (ctrl.quickLauncherPanelController.editMode) {
                    val badgeSize = host.dp(16f)
                    val badgeLeft = cRect.left + host.dp(6f)
                    val badgeTop = cRect.top + host.dp(2f)
                    val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize)
                    cellHighlightPaint.color = Color.argb(220, 229, 57, 53)
                    canvas.drawCircle(badgeRect.centerX(), badgeRect.centerY(), badgeSize / 2f, cellHighlightPaint)
                    folderButtonPaint.textSize = host.sp(10f)
                    folderButtonPaint.isFakeBoldText = true
                    canvas.drawText("−", badgeRect.centerX(), badgeRect.centerY() - (folderButtonPaint.descent() + folderButtonPaint.ascent()) / 2f, folderButtonPaint)
                }
            }
        }
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (appLabelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && appLabelPaint.measureText(text.substring(0, end) + "\u2026") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "\u2026"
    }
}
