package com.slideindex.app.gesture

import android.graphics.Rect
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.KeyboardTriggerBehavior
import kotlin.math.abs
import kotlin.math.roundToInt

object KeyboardTriggerBoundsAdjuster {
    const val DEFAULT_NARROW_SCALE = 0.5f
    private const val MOVE_UP_MARGIN_DP = 8f
    private const val MIN_NARROW_WIDTH_PX = 1
    private const val IME_TOP_DEBOUNCE_PX = 8

    fun shouldRelayout(previousTop: Int?, visible: Boolean, top: Int?): Boolean {
        if (previousTop == null && !visible) return false
        if (visible != (previousTop != null)) return true
        if (!visible || top == null) return false
        return previousTop == null || abs(top - previousTop) > IME_TOP_DEBOUNCE_PX
    }

    fun overlapsKeyboard(bottomPx: Int, imeTop: Int): Boolean = bottomPx > imeTop

    fun adjustCollapsedBounds(
        bounds: List<CollapsedWindowBounds>,
        side: PanelSide,
        behavior: KeyboardTriggerBehavior,
        imeTop: Int?,
        density: Float,
        narrowScale: Float = DEFAULT_NARROW_SCALE,
    ): List<CollapsedWindowBounds> {
        if (imeTop == null || behavior == KeyboardTriggerBehavior.OVERLAY || side.isVerticalEdge) {
            return bounds
        }
        return bounds.map { bound ->
            adjustCollapsedBound(bound, behavior, imeTop, density, narrowScale)
        }
    }

    fun adjustRect(
        rect: Rect,
        behavior: KeyboardTriggerBehavior,
        imeTop: Int?,
        density: Float,
        narrowScale: Float = DEFAULT_NARROW_SCALE,
    ): Rect {
        if (imeTop == null || behavior == KeyboardTriggerBehavior.OVERLAY) {
            return rect.copyBounds()
        }
        val copy = rect.copyBounds()
        when (behavior) {
            KeyboardTriggerBehavior.DISABLE -> {
                copy.right = copy.left
            }
            KeyboardTriggerBehavior.MOVE_UP -> {
                if (!overlapsKeyboard(copy.bottom, imeTop)) {
                    return copy
                }
                val marginPx = (MOVE_UP_MARGIN_DP * density).roundToInt()
                val overflow = copy.bottom - imeTop + marginPx
                copy.top -= overflow
                copy.bottom -= overflow
                if (copy.top < 0) {
                    copy.bottom -= copy.top
                    copy.top = 0
                }
            }
            KeyboardTriggerBehavior.NARROW -> {
                val currentWidth = copy.right - copy.left
                val newWidth = (currentWidth * narrowScale)
                    .roundToInt()
                    .coerceIn(MIN_NARROW_WIDTH_PX, currentWidth)
                copy.right = copy.left + newWidth
            }
            KeyboardTriggerBehavior.OVERLAY -> Unit
        }
        return copy
    }

    private fun adjustCollapsedBound(
        bound: CollapsedWindowBounds,
        behavior: KeyboardTriggerBehavior,
        imeTop: Int,
        density: Float,
        narrowScale: Float,
    ): CollapsedWindowBounds {
        val rect = Rect(
            bound.xPx,
            bound.yPx,
            bound.xPx + bound.widthPx,
            bound.yPx + bound.heightPx,
        )
        val adjusted = adjustRect(
            rect = rect,
            behavior = behavior,
            imeTop = imeTop,
            density = density,
            narrowScale = narrowScale,
        )
        return CollapsedWindowBounds(
            widthPx = adjusted.width().coerceAtLeast(0),
            heightPx = adjusted.height().coerceAtLeast(0),
            yPx = adjusted.top,
            xPx = adjusted.left,
        )
    }

    private fun Rect.copyBounds(): Rect = Rect().also { copy ->
        copy.left = left
        copy.top = top
        copy.right = right
        copy.bottom = bottom
    }
}
