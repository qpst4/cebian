package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView

/**
 * Display 层根容器：线条仍走 Compose，球体与准星由原生 View 直接 layout，避免拖拽时整层重组。
 */
internal class FloatBallDisplayHost(
    context: Context,
    private val lineChromeOwner: OverlayComposeOwner,
    val ballIconView: FloatBallIconView,
    val cursorPreviewView: FloatBallCursorPreviewView,
) : FrameLayout(context) {

    val lineChromeComposeView: ComposeView

    private var lastBallLeft = 0
    private var lastBallTop = 0
    private var lastBallSize = 0
    private var hasBallLayout = false

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipChildren = false
        OverlayCompose.bindOwners(this, lineChromeOwner)

        lineChromeComposeView = OverlayCompose.createComposeView(context, lineChromeOwner)
        addView(lineChromeComposeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        ballIconView.layoutParams = LayoutParams(0, 0)
        addView(ballIconView)

        cursorPreviewView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(cursorPreviewView)
    }

    /** 原生定位球体；热路径直接 layout，不触发整树 requestLayout。 */
    fun layoutBall(left: Int, top: Int, sizePx: Int) {
        if (hasBallLayout && left == lastBallLeft && top == lastBallTop && sizePx == lastBallSize) return
        val sizeChanged = sizePx != lastBallSize
        hasBallLayout = sizePx > 0
        lastBallLeft = left
        lastBallTop = top
        lastBallSize = sizePx

        if (width <= 0 || height <= 0) {
            requestLayout()
            return
        }
        if (sizeChanged) {
            val spec = MeasureSpec.makeMeasureSpec(sizePx, MeasureSpec.EXACTLY)
            ballIconView.measure(spec, spec)
        }
        ballIconView.layout(left, top, left + sizePx, top + sizePx)
    }

    fun setBallVisible(visible: Boolean) {
        ballIconView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun ballLayoutRect(): Rect = Rect(
        lastBallLeft,
        lastBallTop,
        lastBallLeft + lastBallSize,
        lastBallTop + lastBallSize,
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val fullWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val fullHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        lineChromeComposeView.measure(fullWidthSpec, fullHeightSpec)
        cursorPreviewView.measure(fullWidthSpec, fullHeightSpec)
        if (hasBallLayout && lastBallSize > 0) {
            val ballSpec = MeasureSpec.makeMeasureSpec(lastBallSize, MeasureSpec.EXACTLY)
            ballIconView.measure(ballSpec, ballSpec)
        } else {
            ballIconView.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        lineChromeComposeView.layout(0, 0, width, height)
        cursorPreviewView.layout(0, 0, width, height)
        if (hasBallLayout && lastBallSize > 0) {
            ballIconView.layout(
                lastBallLeft,
                lastBallTop,
                lastBallLeft + lastBallSize,
                lastBallTop + lastBallSize,
            )
        } else {
            ballIconView.layout(0, 0, 0, 0)
        }
    }

    fun dispose() {
        OverlayCompose.teardownOverlayCompose(lineChromeComposeView, lineChromeOwner)
    }
}
