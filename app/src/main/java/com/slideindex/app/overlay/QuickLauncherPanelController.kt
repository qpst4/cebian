package com.slideindex.app.overlay



import android.content.Context

import android.graphics.Canvas

import android.graphics.Color

import android.graphics.Paint

import android.graphics.RectF

import android.graphics.Typeface

import android.view.MotionEvent

import com.slideindex.app.data.AppInfo

import com.slideindex.app.launcher.QuickLauncherDefaults

import com.slideindex.app.launcher.QuickLauncherItem

import com.slideindex.app.launcher.QuickLauncherItemCodec

import com.slideindex.app.launcher.QuickLauncherItemType

import com.slideindex.app.settings.AppSettings



class QuickLauncherPanelController(

    private val host: Host,

) {

    interface Host {

        val context: Context

        fun settings(): AppSettings

        fun side(): PanelSide

        fun apps(): List<AppInfo>

        fun isPanelReady(): Boolean

        fun isAddDialogShowing(): Boolean

        fun dp(value: Float): Float

        fun sp(value: Float): Float

        fun invalidate()

        fun hapticTick()

        fun showAddDialog(

            configuredAppPackages: Set<String>,

            configuredShortcutKeys: Set<String>,

            configuredActionKeys: Set<String>,

            onAdd: (QuickLauncherItem) -> Unit,

            onRemove: (QuickLauncherItem) -> Unit,

        )

        fun onPersist(items: List<QuickLauncherItem>)

    }



    enum class ToolbarAction { ADD, EDIT }



    data class ToolbarLayoutMetrics(

        val toolbarWidth: Float,

        val toolbarPanelGap: Float,

        val edgeInset: Float,

        val buttonSize: Float,

        val buttonGap: Float,

    )



    private val addButtonRect = RectF()

    private val editButtonRect = RectF()

    private val toolbarRect = RectF()

    private val deleteBadgeRects = mutableListOf<RectF>()

    var editMode: Boolean = false
        private set

    var itemPageOffset: Int = 0
        private set

    fun setItemPageOffset(offset: Int) {
        itemPageOffset = offset.coerceAtLeast(0)
    }



    private var localItems: List<QuickLauncherItem> = emptyList()

    private var defaultsPersisted = false

    private var dragFromIndex = -1

    private var dragTargetIndex = -1

    private var dragStartX = 0f

    private var dragStartY = 0f

    private var dragCurrentX = 0f

    private var dragCurrentY = 0f

    private var managementTouchActive = false



    private val toolbarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val toolbarButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val toolbarIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {

        color = Color.WHITE

        textAlign = Paint.Align.CENTER

        typeface = Typeface.DEFAULT_BOLD

    }

    private val deleteBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {

        color = Color.parseColor("#E53935")

    }

    private val deleteBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {

        color = Color.WHITE

        textAlign = Paint.Align.CENTER

        typeface = Typeface.DEFAULT_BOLD

    }



    fun reset() {

        editMode = false

        dragFromIndex = -1

        dragTargetIndex = -1

        managementTouchActive = false

        deleteBadgeRects.clear()

    }



    fun syncSettings(settings: AppSettings) {
        if (!editMode && dragFromIndex < 0) {
            val configured = configuredItems(settings)
            if (localItems.isEmpty() || configured == localItems) {
                localItems = configured
            }
        }
    }



    fun displayItems(settings: AppSettings): List<QuickLauncherItem> {

        if (localItems.isNotEmpty()) return localItems

        return QuickLauncherDefaults.effectiveItems(configuredItems(settings), host.apps())

    }



    fun ensureDefaultsPersisted(settings: AppSettings) {

        if (defaultsPersisted || configuredItems(settings).isNotEmpty()) return

        val defaults = QuickLauncherDefaults.fromApps(host.apps())

        if (defaults.isEmpty()) return

        defaultsPersisted = true

        localItems = defaults

        host.onPersist(defaults)

    }



    fun shouldShowToolbar(settings: AppSettings): Boolean =

        host.isPanelReady() &&

            displayItems(settings).isNotEmpty() &&

            !host.isAddDialogShowing()



    fun toolbarLayoutMetrics(): ToolbarLayoutMetrics = ToolbarLayoutMetrics(
        toolbarWidth = host.dp(44f),
        toolbarPanelGap = host.dp(10f),
        edgeInset = host.dp(8f),
        buttonSize = host.dp(36f),
        buttonGap = host.dp(6f),
    )



    fun contentReserveWidth(settings: AppSettings): Float {
        if (displayItems(settings).isEmpty()) return 0f
        val metrics = toolbarLayoutMetrics()
        return metrics.toolbarWidth + metrics.toolbarPanelGap + metrics.edgeInset
    }



    fun toolbarBounds(): RectF = RectF(toolbarRect)



    fun toolbarContains(localX: Float, localY: Float): Boolean =

        toolbarRect.contains(localX, localY)



    fun combinedContentRect(panelRect: RectF): RectF {

        layoutToolbar(panelRect)

        if (toolbarRect.isEmpty) return RectF(panelRect)

        return RectF(

            minOf(panelRect.left, toolbarRect.left),

            minOf(panelRect.top, toolbarRect.top),

            maxOf(panelRect.right, toolbarRect.right),

            maxOf(panelRect.bottom, toolbarRect.bottom),

        )

    }



    fun layoutToolbar(panelRect: RectF) {

        if (!shouldShowToolbar(host.settings())) {

            toolbarRect.setEmpty()

            addButtonRect.setEmpty()

            editButtonRect.setEmpty()

            return

        }

        val metrics = toolbarLayoutMetrics()
        val buttonSize = metrics.buttonSize
        val gap = metrics.buttonGap
        val padding = host.dp(6f)
        val toolbarWidth = buttonSize + padding * 2f
        val toolbarHeight = buttonSize * 2f + gap + padding * 2f
        val left = when (host.side()) {
            PanelSide.LEFT -> panelRect.right + metrics.toolbarPanelGap
            PanelSide.RIGHT -> panelRect.left - metrics.toolbarPanelGap - toolbarWidth
        }
        val top = panelRect.bottom - toolbarHeight - host.dp(8f)
        toolbarRect.set(left, top, left + toolbarWidth, top + toolbarHeight)
        val insetX = (toolbarWidth - buttonSize) / 2f
        val buttonsLeft = toolbarRect.left + insetX
        var buttonTop = toolbarRect.top + padding
        addButtonRect.set(
            buttonsLeft,
            buttonTop,
            buttonsLeft + buttonSize,
            buttonTop + buttonSize,
        )
        buttonTop += buttonSize + gap
        editButtonRect.set(
            buttonsLeft,
            buttonTop,
            buttonsLeft + buttonSize,
            buttonTop + buttonSize,
        )

    }



    fun drawToolbar(canvas: Canvas, panelRect: RectF) {

        layoutToolbar(panelRect)

        if (toolbarRect.isEmpty) return

        val theme = OverlayPanelTheme.colors(host.context)

        val corner = host.dp(14f)

        toolbarBgPaint.color = Color.argb(235, 32, 32, 36)
        canvas.drawRoundRect(toolbarRect, corner, corner, toolbarBgPaint)
        canvas.drawRoundRect(
            RectF(
                toolbarRect.left + host.dp(1f),
                toolbarRect.top + host.dp(1f),
                toolbarRect.right - host.dp(1f),
                toolbarRect.bottom - host.dp(1f),
            ),
            corner - host.dp(1f),
            corner - host.dp(1f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) },
        )

        drawToolbarButton(canvas, addButtonRect, ToolbarAction.ADD, theme.accent, active = false)

        drawToolbarButton(

            canvas,

            editButtonRect,

            ToolbarAction.EDIT,

            if (editMode) theme.accent else Color.argb(230, 255, 255, 255),

            active = editMode,

        )

    }



    private fun drawToolbarButton(

        canvas: Canvas,

        rect: RectF,

        action: ToolbarAction,

        color: Int,

        active: Boolean,

    ) {

        val buttonCorner = host.dp(12f)

        toolbarButtonPaint.color = if (active) {
            Color.argb(90, Color.red(color), Color.green(color), Color.blue(color))
        } else {
            Color.argb(80, 255, 255, 255)
        }

        canvas.drawRoundRect(rect, buttonCorner, buttonCorner, toolbarButtonPaint)

        toolbarIconPaint.color = color

        toolbarIconPaint.textSize = if (action == ToolbarAction.ADD) host.sp(22f) else host.sp(18f)

        val glyph = when (action) {

            ToolbarAction.ADD -> "+"

            ToolbarAction.EDIT -> if (editMode) "✓" else "−"

        }

        canvas.drawText(

            glyph,

            rect.centerX(),

            rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,

            toolbarIconPaint,

        )

    }



    fun layoutDeleteBadges(cells: List<RectF>) {

        deleteBadgeRects.clear()

        if (!editMode) return

        val radius = host.dp(8f)

        cells.forEachIndexed { index, cell ->
            if (index == dragFromIndex && dragFromIndex >= 0) return@forEachIndexed
            deleteBadgeRects += RectF(
                cell.left + host.dp(2f),
                cell.top + host.dp(2f),
                cell.left + host.dp(2f) + radius * 2f,
                cell.top + host.dp(2f) + radius * 2f,
            )
        }

    }



    fun drawDeleteBadges(canvas: Canvas) {

        if (!editMode) return

        deleteBadgeTextPaint.textSize = host.sp(11f)

        deleteBadgeRects.forEach { badge ->

            canvas.drawCircle(badge.centerX(), badge.centerY(), badge.width() / 2f, deleteBadgePaint)

            canvas.drawText(

                "−",

                badge.centerX(),

                badge.centerY() - (deleteBadgeTextPaint.descent() + deleteBadgeTextPaint.ascent()) / 2f,

                deleteBadgeTextPaint,

            )

        }

    }



    fun handleManagementTouch(

        event: MotionEvent,

        localX: Float,

        localY: Float,

        panelRect: RectF,

        cellBounds: List<Pair<Any, RectF>>,

    ): Boolean {

        val settings = host.settings()

        if (!shouldShowToolbar(settings)) return false

        layoutToolbar(panelRect)

        val quickCells = cellBounds.mapNotNull { (item, rect) ->

            (item as? QuickLauncherItem)?.let { it to rect }

        }

        layoutDeleteBadges(quickCells.map { it.second })



        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                managementTouchActive = toolbarRect.contains(localX, localY) ||

                    (editMode && deleteBadgeRects.any { it.contains(localX, localY) }) ||

                    (editMode && indexAt(localX, localY, quickCells) >= 0)

                if (toolbarActionAt(localX, localY) != null) return true

                if (editMode) {

                    val badgeIndex = deleteBadgeIndexAt(localX, localY)

                    if (badgeIndex >= 0) return true

                    val cellIndex = indexAt(localX, localY, quickCells)

                    if (cellIndex >= 0) {

                        dragFromIndex = cellIndex

                        dragTargetIndex = cellIndex

                        dragStartX = localX

                        dragStartY = localY

                        dragCurrentX = localX

                        dragCurrentY = localY

                        return true

                    }

                }

                return false

            }

            MotionEvent.ACTION_MOVE -> {

                if (dragFromIndex >= 0) {
                    dragCurrentX = localX
                    dragCurrentY = localY
                    dragTargetIndex = targetIndexForDrag(localX, localY, quickCells)
                    host.invalidate()
                    return true
                }

                return managementTouchActive

            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                var handled = managementTouchActive

                when (toolbarActionAt(localX, localY)) {

                    ToolbarAction.ADD -> {

                        openAddDialog()

                        handled = true

                    }

                    ToolbarAction.EDIT -> {

                        editMode = !editMode

                        if (!editMode) {

                            persistLocalItems()

                        }

                        handled = true

                    }

                    null -> Unit

                }

                if (editMode) {
                    val badgeIndex = deleteBadgeIndexAt(localX, localY)
                    if (badgeIndex >= 0) {
                        removeItemAt(badgeIndex)
                        handled = true
                    } else if (dragFromIndex >= 0 &&
                        dragTargetIndex >= 0 &&
                        dragFromIndex != dragTargetIndex
                    ) {
                        moveItem(dragFromIndex, dragTargetIndex)
                        handled = true
                    }
                }
                dragFromIndex = -1
                dragTargetIndex = -1

                managementTouchActive = false

                if (handled) host.invalidate()

                return handled

            }

        }

        return false

    }



    fun isDragging(): Boolean = dragFromIndex >= 0

    fun dragSourceIndex(): Int = dragFromIndex

    fun dragDestinationIndex(): Int = dragTargetIndex

    fun dragVisualOffset(index: Int): Pair<Float, Float> {
        if (index != dragFromIndex || dragFromIndex < 0) return 0f to 0f
        return (dragCurrentX - dragStartX) to (dragCurrentY - dragStartY)
    }



    private fun toolbarActionAt(localX: Float, localY: Float): ToolbarAction? {

        val slop = host.dp(8f)

        fun hit(rect: RectF): Boolean =

            RectF(

                rect.left - slop,

                rect.top - slop,

                rect.right + slop,

                rect.bottom + slop,

            ).contains(localX, localY)

        return when {

            hit(addButtonRect) -> ToolbarAction.ADD

            hit(editButtonRect) -> ToolbarAction.EDIT

            else -> null

        }

    }



    private fun openAddDialog() {

        val items = workingItems()

        val configuredAppPackages = items

            .filter { it.type == QuickLauncherItemType.APP }

            .map { it.payload }

            .toSet()

        val configuredShortcutKeys = items

            .filter { it.type == QuickLauncherItemType.SHORTCUT }

            .mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }

            .toSet()

        val configuredActionKeys = items

            .filter { it.type == QuickLauncherItemType.ACTION }

            .mapNotNull { QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) }

            .toSet()

        host.showAddDialog(configuredAppPackages, configuredShortcutKeys, configuredActionKeys,
            onAdd = { added ->
                localItems = workingItems() + added
                persistLocalItems()
                host.invalidate()
            },
            onRemove = { removed ->
                val current = workingItems()
                val removeIndex = current.indexOfFirst { item ->
                    item.type == removed.type && item.payload == removed.payload
                }
                if (removeIndex >= 0) {
                    localItems = current.filterIndexed { index, _ -> index != removeIndex }
                    persistLocalItems()
                    host.invalidate()
                }
            },
        )

    }



    private fun workingItems(): List<QuickLauncherItem> {

        if (localItems.isNotEmpty()) return localItems

        return QuickLauncherDefaults.effectiveItems(configuredItems(host.settings()), host.apps())

    }



    private fun removeItemAt(pageLocalIndex: Int) {

        val index = itemPageOffset + pageLocalIndex

        val current = workingItems()

        if (index !in current.indices) return

        localItems = current.filterIndexed { i, _ -> i != index }

        host.hapticTick()

        persistLocalItems()

        host.invalidate()

    }



    private fun moveItem(pageLocalFrom: Int, pageLocalTo: Int) {

        val from = itemPageOffset + pageLocalFrom
        val to = itemPageOffset + pageLocalTo

        val current = workingItems().toMutableList()

        if (from !in current.indices || to !in current.indices) return

        val item = current.removeAt(from)

        current.add(to, item)

        localItems = current

        persistLocalItems()

    }



    private fun persistLocalItems() {

        val items = localItems.ifEmpty { return }

        host.onPersist(items)

    }



    private fun configuredItems(settings: AppSettings): List<QuickLauncherItem> =
        settings.quickLauncher



    private fun indexAt(

        localX: Float,

        localY: Float,

        cellBounds: List<Pair<QuickLauncherItem, RectF>>,

    ): Int {

        cellBounds.forEachIndexed { index, (_, rect) ->

            if (rect.contains(localX, localY)) return index

        }

        return -1

    }



    private fun deleteBadgeIndexAt(localX: Float, localY: Float): Int {

        deleteBadgeRects.forEachIndexed { index, rect ->

            if (rect.contains(localX, localY)) return index

        }

        return -1

    }



    private fun targetIndexForDrag(

        localX: Float,

        localY: Float,

        cellBounds: List<Pair<QuickLauncherItem, RectF>>,

    ): Int {

        if (cellBounds.isEmpty()) return dragFromIndex

        cellBounds.forEachIndexed { index, (_, rect) ->

            if (rect.contains(localX, localY)) return index

        }

        var best = dragFromIndex

        var bestDist = Float.MAX_VALUE

        cellBounds.forEachIndexed { index, (_, rect) ->

            val dx = localX - rect.centerX()

            val dy = localY - rect.centerY()

            val dist = dx * dx + dy * dy

            if (dist < bestDist) {

                bestDist = dist

                best = index

            }

        }

        return best

    }

}

