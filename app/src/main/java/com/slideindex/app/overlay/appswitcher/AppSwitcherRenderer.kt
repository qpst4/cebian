package com.slideindex.app.overlay.appswitcher

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.HoneycombRuntimeTarget
import com.slideindex.app.overlay.ShellCommandBadgeRenderer
import com.slideindex.app.overlay.ShortcutBadgeRenderer
import com.slideindex.app.overlay.layout.FvAppSwitcherSide
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.overlay.layout.FvPanelLayout
import com.slideindex.app.overlay.layout.FvToolbarButton
import com.slideindex.app.shell.ShellCommand
import kotlin.math.min

internal object AppSwitcherRenderer {
    private const val ICON_SHADOW = 0x55000000
    private const val HIGHLIGHT_RING = 0xCCFFFFFF.toInt()
    private const val EMPTY_FILL = 0xAAFFFFFF.toInt()
    private const val EMPTY_FILL_HIGHLIGHT = 0xD9FFFFFF.toInt()
    private const val EMPTY_STROKE = 0x88FFFFFF.toInt()
    private const val EMPTY_STROKE_HIGHLIGHT = 0xFFFFFFFF.toInt()
    private const val PLUS_COLOR = 0xCCFFFFFF.toInt()
    private const val PLUS_COLOR_HIGHLIGHT = 0xFFFFFFFF.toInt()
    private const val TOOLBAR_FILL = 0xCC2B3137.toInt()
    private const val TOOLBAR_FILL_HIGHLIGHT = 0xE63A4249.toInt()
    private const val TOOLBAR_GLYPH = 0xFFFFFFFF.toInt()
    private const val NAME_TEXT = 0xFFFFFFFF.toInt()
    private const val PREVIEW_BG = 0xCC1E2328.toInt()
    private const val SELECTION_SCALE = 1.18f

    fun buildLayout(
        circleCount: Int,
        side: FvAppSwitcherSide,
        anchorX: Float,
        anchorY: Float,
        screenWidth: Float,
        density: Float,
        iconSizeDp: Float = FvCircleLayoutEngine.ICON_SIZE_DP,
        iconShape: com.slideindex.app.overlay.layout.FvIconShape = com.slideindex.app.overlay.layout.FvIconShape.ROUNDED_RECT,
        baseRadiusDp: Float = FvCircleLayoutEngine.DEFAULT_BASE_RADIUS_DP,
        layerGapDp: Float = FvCircleLayoutEngine.DEFAULT_LAYER_GAP_DP,
        endMarginDeg: Float = FvCircleLayoutEngine.DEFAULT_END_MARGIN_DEG,
    ): FvPanelLayout = FvCircleLayoutEngine.layout(
        circleCount = circleCount,
        side = side,
        anchorX = anchorX,
        anchorY = anchorY,
        screenWidth = screenWidth,
        density = density,
        iconSizeDp = iconSizeDp,
        iconShape = iconShape,
        baseRadiusDp = baseRadiusDp,
        layerGapDp = layerGapDp,
        endMarginDeg = endMarginDeg,
    )

    fun draw(
        context: Context,
        canvas: Canvas,
        layout: FvPanelLayout,
        targets: List<HoneycombRuntimeTarget?>,
        editMode: Boolean,
        highlightedSlot: Int,
        highlightedToolbarButton: FvToolbarButton?,
        showToolbar: Boolean,
        density: Float,
        revealProgress: Float,
        appsByPackage: Map<String, AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
    ) {
        val progress = revealProgress.coerceIn(0f, 1f)
        if (progress <= 0.01f) return

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ICON_SHADOW }
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
        val baseRadius = layout.itemSizePx * 0.5f

        for (pass in 0..1) {
            for (slot in layout.slots.indices) {
                val highlighted = slot == highlightedSlot
                if (pass == 0 && highlighted) continue
                if (pass == 1 && !highlighted) continue

                val target = targets.getOrNull(slot)
                val isEmpty = target == null
                if (!editMode && isEmpty) continue

                val slotLayout = layout.slots[slot]
                val centerX = layout.anchorX + (slotLayout.centerX - layout.anchorX) * progress
                val centerY = layout.anchorY + (slotLayout.centerY - layout.anchorY) * progress
                val scale = if (highlighted) SELECTION_SCALE else 1f
                val radius = baseRadius * scale

                if (isEmpty && editMode) {
                    drawEmptySlot(
                        canvas = canvas,
                        centerX = centerX,
                        centerY = centerY,
                        radius = radius,
                        highlighted = highlighted,
                        density = density,
                        progress = progress,
                        cornerRadiusRatio = layout.cornerRadiusRatio,
                        shadowPaint = shadowPaint,
                        fillPaint = fillPaint,
                        strokePaint = strokePaint,
                        plusPaint = plusPaint,
                    )
                } else if (target != null) {
                    val iconSizePx = (radius * 2f).toInt().coerceAtLeast(12)
                    val bitmap = AppSwitcherSlotIconBitmap.get(
                        context = context,
                        item = target.item,
                        sizePx = iconSizePx,
                        appsByPackage = appsByPackage,
                        activityShortcuts = activityShortcuts,
                        shellCommands = shellCommands,
                    )
                    drawAppIcon(
                        canvas = canvas,
                        centerX = centerX,
                        centerY = centerY,
                        radius = radius,
                        bitmap = bitmap,
                        highlighted = highlighted,
                        density = density,
                        progress = progress,
                        cornerRadiusRatio = layout.cornerRadiusRatio,
                        shadowPaint = shadowPaint,
                        strokePaint = strokePaint,
                        iconPaint = iconPaint,
                    )
                    val drawSize = min(bitmap.width.toFloat(), radius * 2f)
                    if (target.isShortcut) {
                        ShortcutBadgeRenderer.draw(canvas, centerX, centerY, drawSize, progress, density)
                    }
                    if (target.isShellCommandBadge) {
                        ShellCommandBadgeRenderer.draw(canvas, centerX, centerY, drawSize, progress, density)
                    }
                }
            }
        }

        if (showToolbar) {
            FvToolbarButton.entries.forEach { button ->
                val (cx, cy) = layout.toolbarButtonCenters.getOrNull(button.ordinal) ?: return@forEach
                drawToolbarButton(
                    canvas = canvas,
                    centerX = cx,
                    centerY = cy,
                    radius = layout.toolbarButtonRadiusPx,
                    highlighted = button == highlightedToolbarButton,
                    density = density,
                    fillPaint = fillPaint,
                    glyphPaint = plusPaint,
                    button = button,
                )
            }
        }

        if (highlightedSlot >= 0) {
            val target = targets.getOrNull(highlightedSlot)
            if (target != null) {
                val bigIconSizePx = (52f * density).toInt().coerceAtLeast(24)
                val bigBitmap = AppSwitcherSlotIconBitmap.get(
                    context = context,
                    item = target.item,
                    sizePx = bigIconSizePx,
                    appsByPackage = appsByPackage,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                )
                val previewBadgeInfo = drawTopPreview(
                    canvas = canvas,
                    label = target.label,
                    bitmap = bigBitmap,
                    density = density,
                    progress = progress,
                    cornerRadiusRatio = layout.cornerRadiusRatio,
                )
                if (previewBadgeInfo != null) {
                    val (badgeCx, badgeCy, badgeSize) = previewBadgeInfo
                    if (target.isShortcut) {
                        ShortcutBadgeRenderer.draw(canvas, badgeCx, badgeCy, badgeSize, progress, density)
                    }
                    if (target.isShellCommandBadge) {
                        ShellCommandBadgeRenderer.draw(canvas, badgeCx, badgeCy, badgeSize, progress, density)
                    }
                }
            }
        }
    }

    private val iconDstRect = RectF()
    private val iconHighlightRect = RectF()
    private val iconShadowRect = RectF()
    private val iconShaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val iconShaderMatrix = Matrix()

    private fun drawAppIcon(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        bitmap: android.graphics.Bitmap,
        highlighted: Boolean,
        density: Float,
        progress: Float,
        cornerRadiusRatio: Float,
        shadowPaint: Paint,
        strokePaint: Paint,
        iconPaint: Paint,
    ) {
        val diameter = radius * 2f
        val left = centerX - radius
        val top = centerY - radius
        val right = left + diameter
        val bottom = top + diameter
        iconDstRect.set(left, top, right, bottom)
        val cornerRadius = diameter * cornerRadiusRatio

        if (highlighted) {
            val shadowOffset = 3f * density * progress
            shadowPaint.color = ICON_SHADOW
            iconShadowRect.set(left, top + shadowOffset, right, bottom + shadowOffset)
            if (cornerRadiusRatio >= 0.49f) {
                canvas.drawCircle(centerX, centerY + shadowOffset, radius, shadowPaint)
            } else {
                canvas.drawRoundRect(iconShadowRect, cornerRadius, cornerRadius, shadowPaint)
            }
        }

        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        iconShaderMatrix.reset()
        iconShaderMatrix.setScale(
            iconDstRect.width() / bitmap.width.toFloat(),
            iconDstRect.height() / bitmap.height.toFloat(),
        )
        iconShaderMatrix.postTranslate(iconDstRect.left, iconDstRect.top)
        shader.setLocalMatrix(iconShaderMatrix)
        iconShaderPaint.shader = shader
        iconShaderPaint.alpha = iconPaint.alpha

        if (cornerRadiusRatio >= 0.49f) {
            canvas.drawCircle(centerX, centerY, radius, iconShaderPaint)
        } else {
            canvas.drawRoundRect(iconDstRect, cornerRadius, cornerRadius, iconShaderPaint)
        }

        if (highlighted) {
            strokePaint.style = Paint.Style.STROKE
            strokePaint.pathEffect = null
            strokePaint.color = HIGHLIGHT_RING
            strokePaint.strokeWidth = 2.5f * density
            val strokeOffset = 1.5f * density
            iconHighlightRect.set(
                left - strokeOffset,
                top - strokeOffset,
                right + strokeOffset,
                bottom + strokeOffset,
            )
            val highlightCorner = cornerRadius + strokeOffset
            if (cornerRadiusRatio >= 0.49f) {
                canvas.drawCircle(centerX, centerY, radius + strokeOffset, strokePaint)
            } else {
                canvas.drawRoundRect(iconHighlightRect, highlightCorner, highlightCorner, strokePaint)
            }
        }
    }

    private fun drawEmptySlot(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        highlighted: Boolean,
        density: Float,
        progress: Float,
        cornerRadiusRatio: Float,
        shadowPaint: Paint,
        fillPaint: Paint,
        strokePaint: Paint,
        plusPaint: Paint,
    ) {
        val shadowOffset = 2f * density * progress
        val diameter = radius * 2f
        val left = centerX - radius
        val top = centerY - radius
        val right = left + diameter
        val bottom = top + diameter
        val cornerRadius = diameter * cornerRadiusRatio

        fillPaint.color = if (highlighted) EMPTY_FILL_HIGHLIGHT else EMPTY_FILL
        strokePaint.style = Paint.Style.STROKE
        strokePaint.pathEffect = null
        strokePaint.color = if (highlighted) EMPTY_STROKE_HIGHLIGHT else EMPTY_STROKE
        strokePaint.strokeWidth = 1.75f * density

        if (cornerRadiusRatio >= 0.49f) {
            canvas.drawCircle(centerX, centerY + shadowOffset, radius * 1.02f, shadowPaint)
            canvas.drawCircle(centerX, centerY, radius, fillPaint)
            canvas.drawCircle(centerX, centerY, radius, strokePaint)
        } else {
            iconShadowRect.set(left, top + shadowOffset, right, bottom + shadowOffset)
            canvas.drawRoundRect(iconShadowRect, cornerRadius, cornerRadius, shadowPaint)
            iconDstRect.set(left, top, right, bottom)
            canvas.drawRoundRect(iconDstRect, cornerRadius, cornerRadius, fillPaint)
            canvas.drawRoundRect(iconDstRect, cornerRadius, cornerRadius, strokePaint)
        }

        plusPaint.color = if (highlighted) PLUS_COLOR_HIGHLIGHT else PLUS_COLOR
        plusPaint.textSize = radius * 0.82f
        val textY = centerY - (plusPaint.descent() + plusPaint.ascent()) / 2f
        canvas.drawText("+", centerX, textY, plusPaint)
    }

    private fun drawToolbarButton(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        highlighted: Boolean,
        density: Float,
        fillPaint: Paint,
        glyphPaint: Paint,
        button: FvToolbarButton,
    ) {
        val (baseColor, highlightColor) = when (button) {
            FvToolbarButton.HIDE -> 0xFFFFA000.toInt() to 0xFFFFB300.toInt()
            FvToolbarButton.MOVE -> 0xFF0288D1.toInt() to 0xFF039BE5.toInt()
            FvToolbarButton.PIN -> 0xFF43A047.toInt() to 0xFF4CAF50.toInt()
            FvToolbarButton.SETTINGS -> 0xFF607D8B.toInt() to 0xFF78909C.toInt()
            FvToolbarButton.KEYBOARD -> 0xFF455A64.toInt() to 0xFF546E7A.toInt()
        }
        val drawRadius = if (highlighted) radius * 1.14f else radius

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
        canvas.drawCircle(centerX, centerY + 2f * density, drawRadius * 1.04f, shadowPaint)

        fillPaint.color = if (highlighted) highlightColor else baseColor
        canvas.drawCircle(centerX, centerY, drawRadius, fillPaint)

        if (highlighted) {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.WHITE
                strokeWidth = 2f * density
            }
            canvas.drawCircle(centerX, centerY, drawRadius + 1f * density, ringPaint)
        }

        glyphPaint.color = Color.WHITE
        glyphPaint.textSize = drawRadius * 1.05f
        val glyph = when (button) {
            FvToolbarButton.HIDE -> "−"
            FvToolbarButton.MOVE -> "↕"
            FvToolbarButton.PIN -> "✎"
            FvToolbarButton.SETTINGS -> "⚙"
            FvToolbarButton.KEYBOARD -> "⌨"
        }
        val textY = centerY - (glyphPaint.descent() + glyphPaint.ascent()) / 2f
        canvas.drawText(glyph, centerX, textY, glyphPaint)
    }

    private fun drawTopPreview(
        canvas: Canvas,
        label: String,
        bitmap: android.graphics.Bitmap,
        density: Float,
        progress: Float,
        cornerRadiusRatio: Float,
    ): Triple<Float, Float, Float>? {
        val centerX = canvas.width * 0.5f
        val iconSizePx = 54f * density
        val iconRadius = iconSizePx * 0.5f
        val iconCenterY = 92f * density
        val alpha = (255f * progress).toInt().coerceIn(0, 255)

        val iconRect = RectF(
            centerX - iconRadius,
            iconCenterY - iconRadius,
            centerX + iconRadius,
            iconCenterY + iconRadius,
        )
        val previewCornerRadius = iconSizePx * cornerRadiusRatio

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            this.alpha = (0x55 * progress).toInt().coerceIn(0, 255)
        }
        if (cornerRadiusRatio >= 0.49f) {
            canvas.drawCircle(centerX, iconCenterY + 3f * density, iconRadius, shadowPaint)
        } else {
            canvas.drawRoundRect(
                iconRect.left,
                iconRect.top + 3f * density,
                iconRect.right,
                iconRect.bottom + 3f * density,
                previewCornerRadius,
                previewCornerRadius,
                shadowPaint,
            )
        }

        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        iconShaderMatrix.reset()
        iconShaderMatrix.setScale(
            iconRect.width() / bitmap.width.toFloat(),
            iconRect.height() / bitmap.height.toFloat(),
        )
        iconShaderMatrix.postTranslate(iconRect.left, iconRect.top)
        shader.setLocalMatrix(iconShaderMatrix)
        iconShaderPaint.shader = shader
        iconShaderPaint.alpha = alpha

        if (cornerRadiusRatio >= 0.49f) {
            canvas.drawCircle(centerX, iconCenterY, iconRadius, iconShaderPaint)
        } else {
            canvas.drawRoundRect(iconRect, previewCornerRadius, previewCornerRadius, iconShaderPaint)
        }

        if (label.isNotBlank()) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 15f * density
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                this.alpha = alpha
                setShadowLayer(3.5f * density, 0f, 1.5f * density, Color.BLACK)
            }
            val maxWidth = canvas.width * 0.7f
            val fitted = TextUtils.ellipsize(label, textPaint, maxWidth, TextUtils.TruncateAt.END)
            val textY = iconCenterY + iconRadius + 16f * density - textPaint.ascent() * 0.4f
            canvas.drawText(fitted, 0, fitted.length, centerX, textY, textPaint)
        }

        return Triple(centerX, iconCenterY, iconSizePx)
    }
}
