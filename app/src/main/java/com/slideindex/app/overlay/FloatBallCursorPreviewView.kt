package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.core.content.res.ResourcesCompat
import com.slideindex.app.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * FV p1.o1-style fullscreen pick layer: preview bounds, cross marker, and mode hint.
 * All chrome moves via [pickAnchor] + [invalidate] — no separate WM windows on MOVE.
 */
internal class FloatBallCursorPreviewView(context: Context) : View(context) {

    enum class HintMode {
        HIDDEN,
        TEXT,
        SCREENSHOT,
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val hintFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = COLOR_HINT_BADGE
    }

    private val textHintIcon: Drawable? =
        ResourcesCompat.getDrawable(context.resources, R.drawable.float_ball_hint_text, null)
    private val screenshotHintIcon: Drawable? =
        ResourcesCompat.getDrawable(context.resources, R.drawable.float_ball_hint_screenshot, null)

    private var layerVisible = false
    private var paused = false
    private var regionalDragActive = false
    private var hasSelectionStart = false
    private var selectionStartX = 0f
    private var selectionStartY = 0f
    private var previewBounds: Rect? = null
    private var pickAnchorX = 0f
    private var pickAnchorY = 0f
    private var crossVisible = false
    private var crossAlpha = 1f
    private var crossPaused = false
    private var crossArmDp = DEFAULT_CROSS_ARM_DP
    private var hintMode = HintMode.HIDDEN

    private val density: Float
        get() = resources.displayMetrics.density

    private val strokePx: Float
        get() = PREVIEW_STROKE_DP * density

    private val crossArmPx: Float
        get() = crossArmDp * density

    private val crossStrokePx: Float
        get() = CROSS_STROKE_DP * density

    fun setChromeState(
        visible: Boolean,
        paused: Boolean,
        selectionStart: Offset?,
        selectionPreviewBounds: Rect?,
        pickAnchor: Offset,
        regionalDragActive: Boolean,
        crossVisible: Boolean,
        crossAlpha: Float,
        crossPaused: Boolean,
        crossArmDp: Float,
        hintMode: HintMode,
    ) {
        val start = selectionStart
        val nextHasStart = start != null
        val bounds = selectionPreviewBounds?.let { Rect(it) }
        val startX = start?.x ?: selectionStartX
        val startY = start?.y ?: selectionStartY
        val nextCrossAlpha = crossAlpha.coerceIn(0f, 1f)
        val nextCrossArmDp = crossArmDp.coerceIn(4f, 16f)
        if (layerVisible == visible &&
            this.paused == paused &&
            this.regionalDragActive == regionalDragActive &&
            hasSelectionStart == nextHasStart &&
            (!nextHasStart || (selectionStartX == startX && selectionStartY == startY)) &&
            rectsEqual(this.previewBounds, bounds) &&
            pickAnchorX == pickAnchor.x &&
            pickAnchorY == pickAnchor.y &&
            this.crossVisible == crossVisible &&
            this.crossAlpha == nextCrossAlpha &&
            this.crossPaused == crossPaused &&
            this.crossArmDp == nextCrossArmDp &&
            this.hintMode == hintMode
        ) {
            return
        }
        layerVisible = visible
        this.paused = paused
        this.regionalDragActive = regionalDragActive
        hasSelectionStart = nextHasStart
        if (start != null) {
            selectionStartX = start.x
            selectionStartY = start.y
        }
        previewBounds = bounds
        pickAnchorX = pickAnchor.x
        pickAnchorY = pickAnchor.y
        this.crossVisible = crossVisible
        this.crossAlpha = nextCrossAlpha
        this.crossPaused = crossPaused
        this.crossArmDp = nextCrossArmDp
        this.hintMode = hintMode
        if (visible || crossVisible) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (layerVisible) {
            drawPreviewBounds(canvas)
        }
        if (crossVisible) {
            drawCross(canvas, pickAnchorX, pickAnchorY)
        }
        if (hintMode != HintMode.HIDDEN) {
            drawHint(canvas, pickAnchorX, pickAnchorY)
        }
    }

    private fun drawPreviewBounds(canvas: Canvas) {
        val bounds = previewBounds
        val useControlBounds = !regionalDragActive &&
            bounds != null &&
            paused &&
            hasSelectionStart
        if (useControlBounds) {
            val previewLeft = bounds.left.toFloat()
            val previewTop = bounds.top.toFloat()
            val previewRight = bounds.right.toFloat()
            val previewBottom = bounds.bottom.toFloat()
            if (previewRight > previewLeft && previewBottom > previewTop) {
                strokePaint.color = COLOR_PAUSED
                strokePaint.strokeWidth = strokePx
                canvas.drawRect(previewLeft, previewTop, previewRight, previewBottom, strokePaint)
            }
            return
        }

        if (!regionalDragActive && bounds != null && !hasSelectionStart) {
            val previewLeft = bounds.left.toFloat()
            val previewTop = bounds.top.toFloat()
            val previewRight = bounds.right.toFloat()
            val previewBottom = bounds.bottom.toFloat()
            if (previewRight > previewLeft && previewBottom > previewTop) {
                strokePaint.color = if (paused) COLOR_PAUSED else COLOR_ACTIVE
                strokePaint.strokeWidth = strokePx
                canvas.drawRect(previewLeft, previewTop, previewRight, previewBottom, strokePaint)
            }
            return
        }

        if (paused && hasSelectionStart && (regionalDragActive || bounds == null)) {
            val previewLeft = min(selectionStartX, pickAnchorX)
            val previewTop = min(selectionStartY, pickAnchorY)
            val previewRight = max(selectionStartX, pickAnchorX)
            val previewBottom = max(selectionStartY, pickAnchorY)
            if (previewRight > previewLeft && previewBottom > previewTop) {
                fillPaint.color = COLOR_REGIONAL_FILL
                canvas.drawRect(previewLeft, previewTop, previewRight, previewBottom, fillPaint)
                strokePaint.color = COLOR_PAUSED
                strokePaint.strokeWidth = strokePx
                canvas.drawRect(previewLeft, previewTop, previewRight, previewBottom, strokePaint)
            }
        }
    }

    private fun drawCross(canvas: Canvas, anchorX: Float, anchorY: Float) {
        val alphaByte = (crossAlpha * 255f).roundToInt().coerceIn(0, 255)
        crossPaint.alpha = alphaByte
        crossPaint.color = if (crossPaused) COLOR_PAUSED else COLOR_ACTIVE
        crossPaint.strokeWidth = crossStrokePx
        canvas.drawLine(anchorX - crossArmPx, anchorY, anchorX + crossArmPx, anchorY, crossPaint)
        canvas.drawLine(anchorX, anchorY - crossArmPx, anchorX, anchorY + crossArmPx, crossPaint)
    }

    private fun drawHint(canvas: Canvas, anchorX: Float, anchorY: Float) {
        val icon = when (hintMode) {
            HintMode.TEXT -> textHintIcon
            HintMode.SCREENSHOT -> screenshotHintIcon
            HintMode.HIDDEN -> return
        } ?: return

        val crossHalf = crossArmPx
        val hintScale = crossArmDp / REFERENCE_CROSS_HALF_DP
        val hintSize = HINT_WINDOW_DP * density
        val hintHalf = hintSize / 2f
        val centerX = anchorX - crossHalf + HINT_OFFSET_X_DP * density * hintScale + hintHalf
        val centerY = anchorY - crossHalf - HINT_OFFSET_Y_DP * density * hintScale + hintHalf

        canvas.drawCircle(centerX, centerY, hintHalf, hintFillPaint)

        val iconSize = (hintHalf * 1.25f).roundToInt()
        val half = iconSize / 2
        icon.setBounds(
            (centerX - half).roundToInt(),
            (centerY - half).roundToInt(),
            (centerX + half).roundToInt(),
            (centerY + half).roundToInt(),
        )
        icon.draw(canvas)
    }

    private fun rectsEqual(left: Rect?, right: Rect?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        return left == right
    }

    companion object {
        private const val PREVIEW_STROKE_DP = 2.5f
        private const val DEFAULT_CROSS_ARM_DP = 7.5f
        private const val REFERENCE_CROSS_HALF_DP = 7.5f
        private const val CROSS_STROKE_DP = 2.5f
        private const val HINT_WINDOW_DP = 20f
        private const val HINT_OFFSET_X_DP = 15f
        private const val HINT_OFFSET_Y_DP = 20f
        private const val COLOR_ACTIVE = 0xFFE53935.toInt()
        private const val COLOR_PAUSED = 0xFFFFC107.toInt()
        private const val COLOR_REGIONAL_FILL = 0x33FFC107
        private const val COLOR_HINT_BADGE = 0xFFFFC107.toInt()
    }
}
