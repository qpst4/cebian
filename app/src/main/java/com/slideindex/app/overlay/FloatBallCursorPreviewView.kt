package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * FV p1.o1-style preview bounds: native [onDraw] on a fullscreen WM layer.
 * Only [invalidate] when preview geometry changes — no Compose on MOVE.
 */
internal class FloatBallCursorPreviewView(context: Context) : View(context) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var layerVisible = false
    private var paused = false
    private var hasSelectionStart = false
    private var selectionStartX = 0f
    private var selectionStartY = 0f
    private var previewBounds: Rect? = null
    private var pickAnchorX = 0f
    private var pickAnchorY = 0f

    private val density: Float
        get() = resources.displayMetrics.density

    private val strokePx: Float
        get() = PREVIEW_STROKE_DP * density

    private val minRegionalPx: Float
        get() = RECT_MIN_SIDE_DP * density

    fun setPreviewState(
        visible: Boolean,
        paused: Boolean,
        selectionStart: Offset?,
        selectionPreviewBounds: Rect?,
        pickAnchor: Offset,
    ) {
        val start = selectionStart
        val nextHasStart = start != null
        val bounds = selectionPreviewBounds?.let { Rect(it) }
        val startX = start?.x ?: selectionStartX
        val startY = start?.y ?: selectionStartY
        val pickMatters = pickAnchorAffectsDrawing(
            paused = paused,
            hasSelectionStart = nextHasStart,
            bounds = bounds,
            pickX = pickAnchor.x,
            pickY = pickAnchor.y,
            startX = startX,
            startY = startY,
        )
        val pickSame = !pickMatters ||
            (pickAnchorX == pickAnchor.x && pickAnchorY == pickAnchor.y)
        if (layerVisible == visible &&
            this.paused == paused &&
            hasSelectionStart == nextHasStart &&
            (!nextHasStart || (selectionStartX == startX && selectionStartY == startY)) &&
            rectsEqual(this.previewBounds, bounds) &&
            pickSame
        ) {
            return
        }
        layerVisible = visible
        this.paused = paused
        hasSelectionStart = nextHasStart
        if (start != null) {
            selectionStartX = start.x
            selectionStartY = start.y
        }
        previewBounds = bounds
        if (pickMatters) {
            pickAnchorX = pickAnchor.x
            pickAnchorY = pickAnchor.y
        }
        if (visible) {
            invalidate()
        }
    }

    /** Pick position only affects regional-rect mode; normal drag draws cached control bounds only. */
    private fun pickAnchorAffectsDrawing(
        paused: Boolean,
        hasSelectionStart: Boolean,
        bounds: Rect?,
        pickX: Float,
        pickY: Float,
        startX: Float,
        startY: Float,
    ): Boolean {
        if (!hasSelectionStart) return false
        val withinRegional = abs(pickX - startX) < minRegionalPx && abs(pickY - startY) < minRegionalPx
        if (bounds != null && (paused || withinRegional)) {
            return false
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (!layerVisible) return

        val bounds = previewBounds
        val useControlBounds = bounds != null &&
            (!hasSelectionStart || paused ||
                (abs(pickAnchorX - selectionStartX) < minRegionalPx &&
                    abs(pickAnchorY - selectionStartY) < minRegionalPx))
        if (useControlBounds) {
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

        if (paused && hasSelectionStart) {
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

    private fun rectsEqual(left: Rect?, right: Rect?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        return left == right
    }

    companion object {
        private const val PREVIEW_STROKE_DP = 2.5f
        private const val RECT_MIN_SIDE_DP = 48f
        private const val COLOR_ACTIVE = 0xFFE53935.toInt()
        private const val COLOR_PAUSED = 0xFFFFC107.toInt()
        private const val COLOR_REGIONAL_FILL = 0x33FFC107
    }
}
