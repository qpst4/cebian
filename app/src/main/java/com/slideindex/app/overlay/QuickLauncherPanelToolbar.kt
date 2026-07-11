package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.toColorInt
import com.slideindex.app.R

internal class QuickLauncherPanelToolbar(
    private val controller: QuickLauncherPanelController,
    private val host: QuickLauncherPanelController.Host,
) {
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

    var armedToolbarAction: ToolbarAction? = null
        private set

    private val toolbarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val toolbarButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val toolbarIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val deleteBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#E53935".toColorInt()
    }
    private val deleteBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun reset() {
        armedToolbarAction = null
        deleteBadgeRects.clear()
    }

    fun onEditModeDisabled() {
        deleteBadgeRects.clear()
    }

    fun shouldShowToolbar(): Boolean =
        host.isPanelReady() &&
            controller.displayItems().isNotEmpty() &&
            !host.isAddDialogShowing()

    fun toolbarLayoutMetrics(): ToolbarLayoutMetrics = ToolbarLayoutMetrics(
        toolbarWidth = host.dp(44f),
        toolbarPanelGap = host.dp(10f),
        edgeInset = host.dp(8f),
        buttonSize = host.dp(36f),
        buttonGap = host.dp(6f),
    )

    fun contentReserveWidth(): Float {
        if (controller.displayItems().isEmpty()) return 0f
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
        if (!shouldShowToolbar()) {
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
            if (controller.editMode) theme.accent else Color.argb(230, 255, 255, 255),
            active = controller.editMode,
        )
    }

    fun layoutDeleteBadges(cells: List<RectF>, dragFromIndex: Int) {
        deleteBadgeRects.clear()
        if (!controller.editMode) return
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
        if (!controller.editMode) return
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

    fun resolveToolbarAction(localX: Float, localY: Float, panelRect: RectF): ToolbarAction? {
        if (!shouldShowToolbar()) return null
        layoutToolbar(panelRect)
        toolbarActionAt(localX, localY)?.let { return it }
        if (!toolbarRect.contains(localX, localY)) return null
        val splitY = (addButtonRect.bottom + editButtonRect.top) / 2f
        return if (localY < splitY) ToolbarAction.ADD else ToolbarAction.EDIT
    }

    fun commitToolbarAtRelease(
        localX: Float,
        localY: Float,
        panelRect: RectF,
        tapGesture: Boolean,
        toolbarCommitAllowed: Boolean,
        allowSlideRelease: Boolean = false,
    ): Boolean {
        if (!toolbarCommitAllowed || !shouldShowToolbar()) return false
        layoutToolbar(panelRect)
        val action = when {
            allowSlideRelease -> resolveToolbarAction(localX, localY, panelRect)
            tapGesture -> armedToolbarAction
            else -> null
        } ?: return false
        when (action) {
            ToolbarAction.ADD -> {
                controller.openAddDialog()
                host.hapticTick()
            }
            ToolbarAction.EDIT -> {
                controller.setEditMode(!controller.editMode)
                host.hapticTick()
            }
        }
        armedToolbarAction = null
        return true
    }

    fun setArmedToolbarAction(action: ToolbarAction?) {
        armedToolbarAction = action
    }

    fun deleteBadgeIndexAt(localX: Float, localY: Float): Int {
        deleteBadgeRects.forEachIndexed { index, rect ->
            if (rect.contains(localX, localY)) return index
        }
        return -1
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
            ToolbarAction.EDIT -> if (controller.editMode) "✓" else "−"
        }
        canvas.drawText(
            glyph,
            rect.centerX(),
            rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,
            toolbarIconPaint,
        )
    }

    private fun toolbarActionAt(localX: Float, localY: Float): ToolbarAction? {
        if (addButtonRect.contains(localX, localY)) return ToolbarAction.ADD
        if (editButtonRect.contains(localX, localY)) return ToolbarAction.EDIT

        val slop = host.dp(10f)
        fun slopRect(rect: RectF) = RectF(
            rect.left - slop,
            rect.top - slop,
            rect.right + slop,
            rect.bottom + slop,
        )
        val addSlop = slopRect(addButtonRect)
        val editSlop = slopRect(editButtonRect)
        val inAddSlop = addSlop.contains(localX, localY)
        val inEditSlop = editSlop.contains(localX, localY)
        return when {
            inAddSlop && !inEditSlop -> ToolbarAction.ADD
            inEditSlop && !inAddSlop -> ToolbarAction.EDIT
            inAddSlop && inEditSlop -> {
                val addDistance = kotlin.math.abs(localY - addButtonRect.centerY())
                val editDistance = kotlin.math.abs(localY - editButtonRect.centerY())
                if (addDistance <= editDistance) ToolbarAction.ADD else ToolbarAction.EDIT
            }
            else -> null
        }
    }

    fun collectAccessibilityNodes(context: android.content.Context, panelRect: RectF): List<OverlayVirtualNode> {
        layoutToolbar(panelRect)
        val nodes = mutableListOf<OverlayVirtualNode>()
        if (!addButtonRect.isEmpty) {
            nodes += OverlayVirtualNode(
                description = context.getString(R.string.quick_launcher_add),
                boundsInParent = RectF(addButtonRect),
            )
        }
        if (!editButtonRect.isEmpty) {
            val label = if (controller.editMode) {
                context.getString(R.string.cd_action_confirm)
            } else {
                context.getString(R.string.widget_panel_edit_mode)
            }
            nodes += OverlayVirtualNode(
                description = label,
                boundsInParent = RectF(editButtonRect),
            )
        }
        return nodes
    }
}
