package com.slideindex.app.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import com.slideindex.app.message.MessageOverlayCorner
import com.slideindex.app.message.MessagePlacementFractions
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.SideBubbleHorizontalEdge
import kotlin.math.roundToInt

internal object MessageOverlayLayout {
    const val FLOAT_ICON_EDGE_MARGIN_DP = FloatIconOverlayWindow.EDGE_MARGIN_DP
    const val SIDE_BUBBLE_EDGE_MARGIN_DP = SideBubbleOverlayWindow.EDGE_MARGIN_DP.toFloat()

    fun floatIconTopLeft(
        settings: MessageSettings,
        screenWidthPx: Int,
        screenHeightPx: Int,
        density: Float,
    ): Pair<Int, Int> {
        val iconPx = (settings.floatIconSizeDp.coerceIn(32f, 64f) * density).roundToInt()
        val marginPx = (FLOAT_ICON_EDGE_MARGIN_DP * density).roundToInt()
        val centerY = (MessagePlacementFractions.coerceY(settings.floatIconYFraction) * screenHeightPx).roundToInt()
        val centerX = when (settings.floatIconCorner.horizontalEdge()) {
            SideBubbleHorizontalEdge.Left -> marginPx + iconPx / 2
            SideBubbleHorizontalEdge.Right -> screenWidthPx - marginPx - iconPx / 2
        }
        val left = (centerX - iconPx / 2).coerceIn(marginPx, screenWidthPx - iconPx - marginPx)
        val top = (centerY - iconPx / 2).coerceIn(marginPx, screenHeightPx - iconPx - marginPx)
        return left to top
    }

    fun buildFloatIconLayoutParams(
        context: android.content.Context,
        settings: MessageSettings,
    ): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val (left, top) = floatIconTopLeft(
            settings = settings,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            density = metrics.density,
        )
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left
            y = top
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun buildSideBubbleLayoutParams(
        context: android.content.Context,
        settings: MessageSettings,
    ): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val edgeMarginPx = (SIDE_BUBBLE_EDGE_MARGIN_DP * density).roundToInt()
        val horizontalGravity = when (settings.sideBubbleHorizontalEdge) {
            SideBubbleHorizontalEdge.Left -> Gravity.START
            SideBubbleHorizontalEdge.Right -> Gravity.END
        }
        val yOffsetPx = (MessagePlacementFractions.coerceY(settings.sideBubbleYFraction) * metrics.heightPixels)
            .roundToInt()
            .coerceIn(edgeMarginPx, metrics.heightPixels - edgeMarginPx)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or horizontalGravity
            x = edgeMarginPx
            y = yOffsetPx
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
