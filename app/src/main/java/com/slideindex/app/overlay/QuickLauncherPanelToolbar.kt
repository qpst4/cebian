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
    enum class ToolbarAction { ADD, SWITCH, EDIT }

    data class ToolbarLayoutMetrics(
        val toolbarHeight: Float,
        val toolbarPanelGap: Float,
        val edgeInset: Float,
        val buttonHeight: Float,
        val buttonGap: Float,
    )

    private val addButtonRect = RectF()
    private val switchButtonRect = RectF()
    private val editButtonRect = RectF()
    private val toolbarRect = RectF()
    private val deleteBadgeRects = mutableListOf<RectF>()

    var armedToolbarAction: ToolbarAction? = null
        private set

    private val toolbarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val toolbarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.STROKE
    }
    private val toolbarButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val toolbarIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val deleteBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#EA4335".toColorInt()
    }
    private val deleteBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
    }
    private val deleteBadgeMinusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
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
        toolbarHeight = host.dp(36f),
        toolbarPanelGap = host.dp(8f),
        edgeInset = host.dp(8f),
        buttonHeight = host.dp(28f),
        buttonGap = host.dp(4f),
    )

    fun contentReserveWidth(): Float = 0f

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
            switchButtonRect.setEmpty()
            editButtonRect.setEmpty()
            return
        }
        val toolbarHeight = host.dp(36f)
        val toolbarGap = host.dp(8f)
        val padding = host.dp(4f)
        val hasMulti = host.hasMultiplePanels()

        val buttonHeight = toolbarHeight - padding * 2f
        val smallBtnWidth = host.dp(34f)
        val switchBtnMaxWidth = host.dp(110f)
        val gap = host.dp(4f)

        val desiredWidth = if (hasMulti) {
            smallBtnWidth * 2f + switchBtnMaxWidth + gap * 2f + padding * 2f
        } else {
            smallBtnWidth * 2f + gap + padding * 2f
        }
        val toolbarWidth = minOf(panelRect.width(), desiredWidth)
        val left = panelRect.centerX() - toolbarWidth / 2f
        val top = panelRect.bottom + toolbarGap
        toolbarRect.set(left, top, left + toolbarWidth, top + toolbarHeight)

        val buttonTop = toolbarRect.top + padding
        val buttonBottom = buttonTop + buttonHeight

        if (hasMulti) {
            val totalSmallBtns = smallBtnWidth * 2f
            val availableMiddle = (toolbarWidth - padding * 2f - totalSmallBtns - gap * 2f).coerceAtLeast(host.dp(40f))

            // Left: Add Button
            val addLeft = toolbarRect.left + padding
            addButtonRect.set(addLeft, buttonTop, addLeft + smallBtnWidth, buttonBottom)

            // Middle: Switch Button
            val switchLeft = addButtonRect.right + gap
            switchButtonRect.set(switchLeft, buttonTop, switchLeft + availableMiddle, buttonBottom)

            // Right: Edit Button
            val editLeft = switchButtonRect.right + gap
            editButtonRect.set(editLeft, buttonTop, editLeft + smallBtnWidth, buttonBottom)
        } else {
            val totalButtons = smallBtnWidth * 2f + gap
            val startLeft = toolbarRect.centerX() - totalButtons / 2f

            addButtonRect.set(startLeft, buttonTop, startLeft + smallBtnWidth, buttonBottom)
            switchButtonRect.setEmpty()
            val editLeft = addButtonRect.right + gap
            editButtonRect.set(editLeft, buttonTop, editLeft + smallBtnWidth, buttonBottom)
        }
    }

    fun drawToolbar(canvas: Canvas, panelRect: RectF) {
        layoutToolbar(panelRect)
        if (toolbarRect.isEmpty) return

        val theme = OverlayPanelTheme.colors(host.context)
        val corner = toolbarRect.height() / 2f
        toolbarBgPaint.color = Color.argb(235, 32, 32, 36)
        canvas.drawRoundRect(toolbarRect, corner, corner, toolbarBgPaint)

        toolbarBorderPaint.strokeWidth = host.dp(1f)
        canvas.drawRoundRect(
            RectF(
                toolbarRect.left + host.dp(0.5f),
                toolbarRect.top + host.dp(0.5f),
                toolbarRect.right - host.dp(0.5f),
                toolbarRect.bottom - host.dp(0.5f),
            ),
            corner - host.dp(0.5f),
            corner - host.dp(0.5f),
            toolbarBorderPaint,
        )

        drawToolbarButton(canvas, addButtonRect, ToolbarAction.ADD, theme.accent, active = false)
        if (host.hasMultiplePanels()) {
            drawToolbarButton(canvas, switchButtonRect, ToolbarAction.SWITCH, Color.argb(230, 255, 255, 255), active = false)
        }
        drawToolbarButton(
            canvas,
            editButtonRect,
            ToolbarAction.EDIT,
            if (controller.editMode) theme.accent else Color.argb(230, 255, 255, 255),
            active = controller.editMode,
        )
    }

    fun layoutDeleteBadges(cells: List<RectF>, dragFromIndex: Int = -1) {
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
        deleteBadgeBorderPaint.strokeWidth = host.dp(1f)
        deleteBadgeMinusPaint.strokeWidth = host.dp(1.8f)
        val minusHalfLen = host.dp(3.5f)
        deleteBadgeRects.forEach { badge ->
            val cx = badge.centerX()
            val cy = badge.centerY()
            val r = badge.width() / 2f
            canvas.drawCircle(cx, cy, r, deleteBadgePaint)
            canvas.drawCircle(cx, cy, r - host.dp(0.5f), deleteBadgeBorderPaint)
            canvas.drawLine(cx - minusHalfLen, cy, cx + minusHalfLen, cy, deleteBadgeMinusPaint)
        }
    }

    fun resolveToolbarAction(localX: Float, localY: Float, panelRect: RectF): ToolbarAction? {
        if (!shouldShowToolbar()) return null
        layoutToolbar(panelRect)
        return toolbarActionAt(localX, localY)
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
            ToolbarAction.SWITCH -> {
                controller.switchToNextPanel()
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
        if (rect.isEmpty) return
        val buttonCorner = rect.height() / 2f
        toolbarButtonPaint.color = if (active) {
            Color.argb(90, Color.red(color), Color.green(color), Color.blue(color))
        } else {
            Color.argb(50, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, buttonCorner, buttonCorner, toolbarButtonPaint)
        toolbarIconPaint.color = color

        when (action) {
            ToolbarAction.ADD -> {
                toolbarIconPaint.textSize = host.sp(18f)
                toolbarIconPaint.isFakeBoldText = true
                canvas.drawText(
                    "+",
                    rect.centerX(),
                    rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,
                    toolbarIconPaint,
                )
            }
            ToolbarAction.SWITCH -> {
                toolbarIconPaint.textSize = host.sp(11.5f)
                toolbarIconPaint.isFakeBoldText = false
                val name = host.currentPanelName()
                val label = "$name ⇄"
                val maxTextWidth = rect.width() - host.dp(8f)
                val displayLabel = if (toolbarIconPaint.measureText(label) <= maxTextWidth) {
                    label
                } else {
                    var end = name.length
                    while (end > 1 && toolbarIconPaint.measureText(name.substring(0, end) + "… ⇄") > maxTextWidth) {
                        end--
                    }
                    name.substring(0, end.coerceAtLeast(1)) + "… ⇄"
                }
                canvas.drawText(
                    displayLabel,
                    rect.centerX(),
                    rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,
                    toolbarIconPaint,
                )
            }
            ToolbarAction.EDIT -> {
                toolbarIconPaint.textSize = if (controller.editMode) host.sp(15f) else host.sp(16f)
                toolbarIconPaint.isFakeBoldText = true
                val glyph = if (controller.editMode) "✓" else "−"
                canvas.drawText(
                    glyph,
                    rect.centerX(),
                    rect.centerY() - (toolbarIconPaint.descent() + toolbarIconPaint.ascent()) / 2f,
                    toolbarIconPaint,
                )
            }
        }
    }

    private fun toolbarActionAt(localX: Float, localY: Float): ToolbarAction? {
        if (!toolbarRect.contains(localX, localY)) {
            val slop = host.dp(10f)
            val expanded = RectF(toolbarRect.left - slop, toolbarRect.top - slop, toolbarRect.right + slop, toolbarRect.bottom + slop)
            if (!expanded.contains(localX, localY)) return null
        }
        if (addButtonRect.contains(localX, localY)) return ToolbarAction.ADD
        if (host.hasMultiplePanels() && switchButtonRect.contains(localX, localY)) return ToolbarAction.SWITCH
        if (editButtonRect.contains(localX, localY)) return ToolbarAction.EDIT

        if (host.hasMultiplePanels()) {
            val candidates = listOf(
                ToolbarAction.ADD to kotlin.math.abs(localX - addButtonRect.centerX()),
                ToolbarAction.SWITCH to kotlin.math.abs(localX - switchButtonRect.centerX()),
                ToolbarAction.EDIT to kotlin.math.abs(localX - editButtonRect.centerX()),
            )
            return candidates.minByOrNull { it.second }?.first
        } else {
            return if (localX < toolbarRect.centerX()) ToolbarAction.ADD else ToolbarAction.EDIT
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
        if (!switchButtonRect.isEmpty) {
            nodes += OverlayVirtualNode(
                description = context.getString(R.string.quick_launcher_panel_switch),
                boundsInParent = RectF(switchButtonRect),
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
