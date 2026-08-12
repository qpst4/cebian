package com.slideindex.app.overlay.corner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withScale
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.overlay.ShellCommandBadgeRenderer
import com.slideindex.app.overlay.ShortcutBadgeRenderer
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import kotlin.math.min

/**
 * SearchEVO 风格底角轮盘：三层白圆气泡 + 编辑钮。
 */
internal object CornerRadialMenuRenderer {
    private const val ICON_TINT = 0xFF374151.toInt()
    private const val ICON_TINT_HIGHLIGHT = 0xFF0D9488.toInt()
    private const val BUBBLE_FILL = 0xFFFFFFFF.toInt()
    private const val BUBBLE_FILL_HIGHLIGHT = 0xFFE6FFFA.toInt()
    private const val BUBBLE_FILL_EMPTY = 0xFFF8FAFC.toInt()
    private const val BUBBLE_STROKE = 0x1A000000
    private const val BUBBLE_STROKE_HIGHLIGHT = 0xFF2DD4BF.toInt()
    private const val BUBBLE_STROKE_EMPTY = 0x33000000
    private const val SHADOW_COLOR = 0x33000000
    private const val EDIT_FILL = 0xFFFFFFFF.toInt()
    private const val EDIT_FILL_HIGHLIGHT = 0xFFE6FFFA.toInt()
    private const val EDIT_STROKE = 0x33000000
    private const val EDIT_STROKE_HIGHLIGHT = 0xFF2DD4BF.toInt()
    private const val PLUS_COLOR = 0xFF64748B.toInt()
    private const val PLUS_COLOR_HIGHLIGHT = 0xFF0D9488.toInt()
    private const val NAME_PILL_FILL = 0xDD20263F.toInt()
    private const val NAME_TEXT = 0xFFFFFFFF.toInt()

    fun draw(
        context: Context,
        canvas: Canvas,
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        slots: List<GestureAction>,
        highlightedSlot: Int,
        highlightedEditButton: Boolean,
        editMode: Boolean,
        activeLayerCount: Int,
        density: Float,
        revealProgress: Float,
        hintIconSizeDp: Int = SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
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

        val lastSlot = if (editMode) {
            CornerRadialMenuCodec.SLOT_COUNT - 1
        } else {
            CornerRadialMenuGeometry.lastVisibleSlotIndex(activeLayerCount)
        }

        for (slot in 0..lastSlot) {
            val action = slots.getOrElse(slot) { GestureAction.None }
            val isEmpty = action is GestureAction.None
            if (!editMode && isEmpty) continue

            val target = CornerRadialMenuGeometry.bubbleCenterForSlot(
                anchor = anchor,
                anchorX = anchorX,
                anchorY = anchorY,
                slotIndex = slot,
                settings = settings,
                density = density,
            )
            val centerX = anchorX + (target.x - anchorX) * progress
            val centerY = anchorY + (target.y - anchorY) * progress
            val highlighted = slot == highlightedSlot
            val bubbleRadius = CornerRadialMenuGeometry.bubbleRadiusPx(settings, density, slot)
            val scale = if (highlighted) 1.06f else 1f
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
            } else if (!isEmpty) {
                val iconSizePx = (radius * 1.15f).toInt().coerceAtLeast(12)
                val tint = if (highlighted) ICON_TINT_HIGHLIGHT else ICON_TINT
                val bitmap = CornerSlotIconBitmap.get(
                    context,
                    action,
                    iconSizePx,
                    tint,
                    activityShortcuts,
                    shellCommands,
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
                if (action is GestureAction.LaunchShortcut) {
                    ShortcutBadgeRenderer.draw(
                        canvas,
                        centerX,
                        centerY,
                        drawSize,
                        progress,
                        density,
                    )
                } else if (action.showsShellCommandBadge(shellCommands)) {
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

        drawEditButton(
            canvas = canvas,
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            settings = settings,
            highlighted = highlightedEditButton,
            density = density,
            progress = progress,
            shadowPaint = shadowPaint,
            fillPaint = fillPaint,
            strokePaint = strokePaint,
        )

        if (settings.showSelectedName && !editMode && highlightedSlot >= 0) {
            val action = slots.getOrElse(highlightedSlot) { GestureAction.None }
            if (action !is GestureAction.None) {
                drawSelectedHint(
                    context = context,
                    canvas = canvas,
                    action = action,
                    density = density,
                    progress = progress,
                    hintIconSizeDp = hintIconSizeDp,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                )
            }
        }
    }

    private fun drawSelectedHint(
        context: Context,
        canvas: Canvas,
        action: GestureAction,
        density: Float,
        progress: Float,
        hintIconSizeDp: Int,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
    ) {
        val label = gestureActionLabelText(context, action)
        if (label.isBlank()) return

        val iconSizeDp = SelectedHintMetrics.clampIconSizeDp(hintIconSizeDp)
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAME_TEXT
            textSize = SelectedHintMetrics.textSizePx(iconSizeDp, density)
            textAlign = Paint.Align.LEFT
            alpha = (255f * progress).toInt().coerceIn(0, 255)
        }
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAME_PILL_FILL
            alpha = (221f * progress).toInt().coerceIn(0, 255)
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (255f * progress).toInt().coerceIn(0, 255)
        }

        val maxTextWidth = min(180f * density, canvas.width * 0.48f)
        val fitted = TextUtils.ellipsize(label, namePaint, maxTextWidth, TextUtils.TruncateAt.END)
        val textWidth = namePaint.measureText(fitted, 0, fitted.length)
        val iconSize = iconSizeDp * density
        val paddingX = SelectedHintMetrics.paddingXPx(density)
        val gap = SelectedHintMetrics.gapPx(density)
        val boxHeight = SelectedHintMetrics.boxHeightPx(iconSizeDp, density)
        val boxWidth = paddingX * 2f + iconSize + gap + textWidth

        // 屏幕中上部偏中，略低于首屏列表标题区，避免贴着高亮气泡。
        val margin = 8f * density
        val centerX = (canvas.width * 0.5f).coerceIn(
            boxWidth * 0.5f + margin,
            canvas.width - boxWidth * 0.5f - margin,
        )
        val centerY = (canvas.height * 0.48f).coerceIn(
            boxHeight * 0.5f + 12f * density,
            canvas.height * 0.56f,
        )

        val left = centerX - boxWidth / 2f
        val top = centerY - boxHeight / 2f
        val rect = RectF(left, top, left + boxWidth, top + boxHeight)
        canvas.drawRoundRect(rect, boxHeight / 2f, boxHeight / 2f, pillPaint)

        val iconBitmap = CornerSlotIconBitmap.get(
            context,
            action,
            iconSize.toInt().coerceAtLeast(16),
            0xFFFFFFFF.toInt(),
            activityShortcuts,
            shellCommands,
        )
        val iconLeft = left + paddingX
        val iconTop = centerY - iconSize / 2f
        val iconScale = iconSize / iconBitmap.width
        canvas.withScale(iconScale, iconScale, iconLeft + iconSize / 2f, centerY) {
            drawBitmap(
                iconBitmap,
                iconLeft + iconSize / 2f - iconBitmap.width / 2f,
                centerY - iconBitmap.height / 2f,
                iconPaint,
            )
        }
        if (action is GestureAction.LaunchShortcut) {
            ShortcutBadgeRenderer.draw(
                canvas,
                iconLeft + iconSize / 2f,
                centerY,
                iconSize,
                progress,
                density,
            )
        } else if (action.showsShellCommandBadge(shellCommands)) {
            ShellCommandBadgeRenderer.draw(
                canvas,
                iconLeft + iconSize / 2f,
                centerY,
                iconSize,
                progress,
                density,
            )
        }

        val metrics = namePaint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(fitted, 0, fitted.length, iconLeft + iconSize + gap, baseline, namePaint)
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
        val shadowOffset = 3f * density * progress
        canvas.drawCircle(centerX + shadowOffset, centerY + shadowOffset, radius, shadowPaint)
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

    private fun drawEditButton(
        canvas: Canvas,
        anchor: CornerAnchor,
        anchorX: Float,
        anchorY: Float,
        settings: CornerGestureSettings,
        highlighted: Boolean,
        density: Float,
        progress: Float,
        shadowPaint: Paint,
        fillPaint: Paint,
        strokePaint: Paint,
    ) {
        val target = CornerWheelLayout.editButtonCenter(anchor, anchorX, anchorY, settings, density)
        val centerX = anchorX + (target.x - anchorX) * progress
        val centerY = anchorY + (target.y - anchorY) * progress
        val radius = CornerWheelLayout.editButtonRadius(density) * (0.85f + 0.15f * progress)
        val shadowOffset = 3f * density * progress
        canvas.drawCircle(centerX + shadowOffset, centerY + shadowOffset, radius, shadowPaint)
        fillPaint.color = if (highlighted) EDIT_FILL_HIGHLIGHT else EDIT_FILL
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        strokePaint.color = if (highlighted) EDIT_STROKE_HIGHLIGHT else EDIT_STROKE
        canvas.drawCircle(centerX, centerY, radius, strokePaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (highlighted) ICON_TINT_HIGHLIGHT else ICON_TINT
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = radius * 0.9f
        }
        val textY = centerY - (iconPaint.descent() + iconPaint.ascent()) / 2f
        canvas.drawText("✎", centerX, textY, iconPaint)
    }
}
