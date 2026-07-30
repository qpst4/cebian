package com.slideindex.app.overlay.corner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
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
                val bitmap = CornerSlotIconBitmap.get(context, action, iconSizePx, tint)
                val maxIcon = radius * 1.35f
                val drawSize = min(bitmap.width.toFloat(), maxIcon)
                val left = centerX - drawSize / 2f
                val top = centerY - drawSize / 2f
                val srcScale = drawSize / bitmap.width
                if (srcScale >= 0.99f) {
                    canvas.drawBitmap(bitmap, left, top, iconPaint)
                } else {
                    canvas.save()
                    canvas.scale(srcScale, srcScale, centerX, centerY)
                    canvas.drawBitmap(
                        bitmap,
                        centerX - bitmap.width / 2f,
                        centerY - bitmap.height / 2f,
                        iconPaint,
                    )
                    canvas.restore()
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
