package com.slideindex.app.overlay.appswitcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withScale
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.overlay.ShellCommandBadgeRenderer
import com.slideindex.app.overlay.ShortcutBadgeRenderer
import com.slideindex.app.overlay.HoneycombRuntimeTarget
import com.slideindex.app.overlay.layout.AppSwitcherLayoutEngine
import com.slideindex.app.overlay.layout.AppSwitcherPanelLayout
import com.slideindex.app.overlay.layout.AppSwitcherSide
import com.slideindex.app.settings.AppSwitcherDisplaySettings
import com.slideindex.app.shell.ShellCommand
import kotlin.math.min

internal object AppSwitcherRenderer {
    private const val ICON_TINT = 0xFF374151.toInt()
    private const val ICON_TINT_HIGHLIGHT = 0xFF0D9488.toInt()
    private const val BUBBLE_FILL = 0xFFFFFFFF.toInt()
    private const val BUBBLE_FILL_HIGHLIGHT = 0xFFE6FFFA.toInt()
    private const val BUBBLE_FILL_EMPTY = 0xFFF8FAFC.toInt()
    private const val BUBBLE_STROKE = 0x1A000000
    private const val BUBBLE_STROKE_HIGHLIGHT = 0xFF2DD4BF.toInt()
    private const val BUBBLE_STROKE_EMPTY = 0x33000000
    private const val SHADOW_COLOR = 0x33000000
    private const val PLUS_COLOR = 0xFF64748B.toInt()
    private const val PLUS_COLOR_HIGHLIGHT = 0xFF0D9488.toInt()
    private const val NAME_PILL_FILL = 0xDD20263F.toInt()
    private const val NAME_TEXT = 0xFFFFFFFF.toInt()
    private const val TOOLBAR_FILL = 0xFFFFFFFF.toInt()
    private const val TOOLBAR_FILL_HIGHLIGHT = 0xFFE6FFFA.toInt()
    private const val TOOLBAR_STROKE = 0x33000000
    private const val TOOLBAR_STROKE_HIGHLIGHT = 0xFF2DD4BF.toInt()

    fun draw(
        context: Context,
        canvas: Canvas,
        layout: AppSwitcherPanelLayout,
        display: AppSwitcherDisplaySettings,
        targets: List<HoneycombRuntimeTarget>,
        editMode: Boolean,
        highlightedSlot: Int,
        highlightedToolbarButton: AppSwitcherPinToolbarGeometry.Button?,
        panelPinned: Boolean,
        density: Float,
        revealProgress: Float,
        appsByPackage: Map<String, AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
    ) {
        val progress = revealProgress.coerceIn(0f, 1f)
        if (progress <= 0.01f) return

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SHADOW_COLOR }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val slotCount = layout.slots.size
        val selectionScale = display.selectionScale.coerceIn(
            AppSwitcherDisplaySettings.MIN_SELECTION_SCALE,
            AppSwitcherDisplaySettings.MAX_SELECTION_SCALE,
        ) / 100f

        for (slot in 0 until slotCount) {
            val target = targets.getOrNull(slot)
            val isEmpty = target == null
            if (!editMode && isEmpty) continue

            val slotLayout = layout.slots[slot]
            val centerX = layout.anchorX + (slotLayout.centerX - layout.anchorX) * progress
            val centerY = layout.anchorY + (slotLayout.centerY - layout.anchorY) * progress
            val highlighted = slot == highlightedSlot
            val bubbleRadius = layout.itemSizePx * 0.5f
            val scale = if (highlighted) selectionScale else 1f
            val radius = bubbleRadius * scale

            drawBubble(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                highlighted = highlighted,
                empty = isEmpty && editMode,
                density = density,
                progress = progress,
                shadowPaint = shadowPaint,
                fillPaint = fillPaint,
                strokePaint = strokePaint,
            )

            if (isEmpty && editMode) {
                plusPaint.color = if (highlighted) PLUS_COLOR_HIGHLIGHT else PLUS_COLOR
                plusPaint.textSize = radius * 1.1f
                val textY = centerY - (plusPaint.descent() + plusPaint.ascent()) / 2f
                canvas.drawText("+", centerX, textY, plusPaint)
            } else if (target != null) {
                val iconSizePx = (radius * 1.15f).toInt().coerceAtLeast(12)
                val bitmap = AppSwitcherSlotIconBitmap.get(
                    context = context,
                    item = target.item,
                    sizePx = iconSizePx,
                    appsByPackage = appsByPackage,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                )
                val maxIcon = radius * 1.35f
                val drawSize = min(bitmap.width.toFloat(), maxIcon)
                val left = centerX - drawSize / 2f
                val top = centerY - drawSize / 2f
                val srcScale = drawSize / bitmap.width
                if (srcScale >= 0.99f) {
                    canvas.drawBitmap(bitmap, left, top, iconPaint)
                } else {
                    canvas.withScale(srcScale, srcScale, centerX, centerY) {
                        drawBitmap(
                            bitmap,
                            centerX - bitmap.width / 2f,
                            centerY - bitmap.height / 2f,
                            iconPaint,
                        )
                    }
                }
                if (target.isShortcut) {
                    ShortcutBadgeRenderer.draw(
                        canvas,
                        centerX,
                        centerY,
                        drawSize,
                        progress,
                        density,
                    )
                }
                if (target.isShellCommandBadge) {
                    ShellCommandBadgeRenderer.draw(
                        canvas,
                        centerX,
                        centerY,
                        drawSize,
                        progress,
                        density,
                    )
                }
            }
        }

        if (panelPinned) {
            for (button in AppSwitcherPinToolbarGeometry.Button.entries) {
                val (cx, cy) = AppSwitcherPinToolbarGeometry.buttonCenter(layout, button, density)
                val highlighted = button == highlightedToolbarButton
                val radius = AppSwitcherPinToolbarGeometry.buttonRadius(layout)
                drawToolbarButton(
                    canvas = canvas,
                    centerX = cx,
                    centerY = cy,
                    radius = radius,
                    highlighted = highlighted,
                    density = density,
                    progress = progress,
                    shadowPaint = shadowPaint,
                    fillPaint = fillPaint,
                    strokePaint = strokePaint,
                    plusPaint = plusPaint,
                    button = button,
                )
            }
        }

        if (display.showSelectedName && highlightedSlot >= 0) {
            val target = targets.getOrNull(highlightedSlot) ?: return
            drawSelectedName(
                canvas = canvas,
                layout = layout,
                label = target.label,
                density = density,
                progress = progress,
                hintIconSizeDp = display.selectedHintIconSizeDp,
            )
        }
    }

    private fun drawBubble(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        highlighted: Boolean,
        empty: Boolean,
        density: Float,
        progress: Float,
        shadowPaint: Paint,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val shadowOffset = 2f * density * progress
        canvas.drawCircle(centerX, centerY + shadowOffset, radius, shadowPaint)
        fillPaint.color = when {
            highlighted -> BUBBLE_FILL_HIGHLIGHT
            empty -> BUBBLE_FILL_EMPTY
            else -> BUBBLE_FILL
        }
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        strokePaint.color = when {
            highlighted -> BUBBLE_STROKE_HIGHLIGHT
            empty -> BUBBLE_STROKE_EMPTY
            else -> BUBBLE_STROKE
        }
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
    }

    private fun drawToolbarButton(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        highlighted: Boolean,
        density: Float,
        progress: Float,
        shadowPaint: Paint,
        fillPaint: Paint,
        strokePaint: Paint,
        plusPaint: Paint,
        button: AppSwitcherPinToolbarGeometry.Button,
    ) {
        val shadowOffset = 2f * density * progress
        canvas.drawCircle(centerX, centerY + shadowOffset, radius, shadowPaint)
        fillPaint.color = if (highlighted) TOOLBAR_FILL_HIGHLIGHT else TOOLBAR_FILL
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        strokePaint.color = if (highlighted) TOOLBAR_STROKE_HIGHLIGHT else TOOLBAR_STROKE
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
        plusPaint.color = if (highlighted) ICON_TINT_HIGHLIGHT else ICON_TINT
        plusPaint.textSize = radius * 1.1f
        val glyph = when (button) {
            AppSwitcherPinToolbarGeometry.Button.EDIT -> "✎"
            AppSwitcherPinToolbarGeometry.Button.DISMISS -> "×"
        }
        val textY = centerY - (plusPaint.descent() + plusPaint.ascent()) / 2f
        canvas.drawText(glyph, centerX, textY, plusPaint)
    }

    private fun drawSelectedName(
        canvas: Canvas,
        layout: AppSwitcherPanelLayout,
        label: String,
        density: Float,
        progress: Float,
        hintIconSizeDp: Int,
    ) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAME_TEXT
            textSize = hintIconSizeDp.coerceAtLeast(10).toFloat() * density
            typeface = Typeface.DEFAULT_BOLD
        }
        val maxWidth = layout.itemSizePx * 6f
        val ellipsized = TextUtils.ellipsize(label, textPaint, maxWidth, TextUtils.TruncateAt.END).toString()
        val textWidth = textPaint.measureText(ellipsized)
        val paddingH = 12f * density
        val paddingV = 6f * density
        val pillWidth = textWidth + paddingH * 2f
        val pillHeight = textPaint.textSize + paddingV * 2f
        val pillLeft = when (layout.side) {
            AppSwitcherSide.LEFT -> layout.anchorX + layout.itemSizePx * 0.4f
            AppSwitcherSide.RIGHT -> layout.anchorX - layout.itemSizePx * 0.4f - pillWidth
        }
        val pillTop = layout.anchorY - layout.itemSizePx * 1.8f - pillHeight
        val rect = RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAME_PILL_FILL }
        canvas.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, fillPaint)
        val textX = rect.left + paddingH
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(ellipsized, textX, textY, textPaint)
    }

    fun buildLayout(
        slotCount: Int,
        side: AppSwitcherSide,
        anchorRawY: Float,
        screenWidth: Float,
        screenHeight: Float,
        display: AppSwitcherDisplaySettings,
        density: Float,
    ): AppSwitcherPanelLayout = AppSwitcherLayoutEngine.layout(
        itemCount = slotCount.coerceAtLeast(1),
        side = side,
        anchorRawY = anchorRawY,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        itemSizeDp = display.iconSizeDp.toFloat(),
        spacingDp = display.spacingDp.toFloat(),
        density = density,
        initialRadiusRatio = display.initialRadiusRatioPercent / 100f,
    )
}
