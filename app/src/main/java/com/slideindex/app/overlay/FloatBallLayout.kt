package com.slideindex.app.overlay

import android.graphics.Rect
import android.util.DisplayMetrics
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallPositionFractions
import com.slideindex.app.settings.FloatBallSide
import kotlin.math.max
import kotlin.math.roundToInt

internal object FloatBallLayout {
    private const val EDGE_MARGIN_DP = 8f
    private const val LINE_VISUAL_WIDTH_DP = 4f
    private const val MIN_CAPTURE_WIDTH_DP = 24f
    private const val MIN_STRIP_HEIGHT_DP = 48f
    private const val MIN_POSITION_Y_FRACTION = FloatBallPositionFractions.MIN_Y
    private const val MAX_POSITION_Y_FRACTION = FloatBallPositionFractions.MAX_Y

    fun coerceVisibleFraction(fraction: Float): Float =
        FloatBallPositionFractions.coerceVisible(fraction)

    fun coerceCustomCenterXFraction(fraction: Float): Float =
        FloatBallPositionFractions.coerceCustomCenterX(fraction)

    fun coercePositionYFraction(fraction: Float): Float =
        FloatBallPositionFractions.coerceY(fraction)

    fun resolvedActiveSide(settings: AppSettings): FloatBallSide =
        when (settings.floatBallPositionMode) {
            FloatBallPositionMode.LEFT -> FloatBallSide.LEFT
            FloatBallPositionMode.RIGHT -> FloatBallSide.RIGHT
            FloatBallPositionMode.BOTH_EDGES -> settings.floatBallActiveSide
            FloatBallPositionMode.CUSTOM -> {
                if (settings.floatBallCustomCenterXFraction < 0.5f) FloatBallSide.LEFT else FloatBallSide.RIGHT
            }
        }

    fun panelSideFor(settings: AppSettings): PanelSide = panelSideFor(FloatBallLayout.resolvedActiveSide(settings))

    /** 双贴边模式下，线条在悬浮球对侧，面板应从线条所在侧弹出。 */
    fun panelSideForLineStrip(settings: AppSettings): PanelSide =
        panelSideFor(FloatBallSide.opposite(resolvedActiveSide(settings)))

    private fun panelSideFor(side: FloatBallSide): PanelSide = when (side) {
        FloatBallSide.LEFT -> PanelSide.LEFT
        FloatBallSide.RIGHT -> PanelSide.RIGHT
    }

    fun shouldShowLine(settings: AppSettings): Boolean =
        settings.floatBallPositionMode == FloatBallPositionMode.BOTH_EDGES

    fun dockCenterY(settings: AppSettings, screenHeight: Int): Float =
        coercePositionYFraction(settings.floatBallPositionYFraction) * screenHeight

    fun ballSizePx(settings: AppSettings, density: Float): Int =
        (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt()

    fun marginPx(density: Float): Int = (EDGE_MARGIN_DP * density).roundToInt()

    fun lineTriggerWidthPx(settings: AppSettings, screenWidth: Int, density: Float): Int {
        val fractionWidth = (screenWidth * settings.floatBallLineWidthFraction.coerceIn(0.01f, 0.50f)).roundToInt()
        val minWidth = (MIN_CAPTURE_WIDTH_DP * density).roundToInt()
        return max(fractionWidth, minWidth)
    }

    fun lineTriggerHeightPx(settings: AppSettings, screenHeight: Int, density: Float): Int {
        val fractionHeight = (screenHeight * settings.floatBallLineHeightFraction.coerceIn(0.04f, 0.4f)).roundToInt()
        val minHeight = (MIN_STRIP_HEIGHT_DP * density).roundToInt()
        return max(fractionHeight, minHeight)
    }

    fun lineVisualWidthPx(density: Float): Int =
        max((LINE_VISUAL_WIDTH_DP * density).roundToInt(), 1)

    fun ballTopLeft(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Pair<Int, Int> {
        val density = metrics.density
        val ballSizePx = ballSizePx(settings, density)
        val marginPx = marginPx(density)
        val centerY = dockCenterY(settings, screenHeightPx)
        val left = when (settings.floatBallPositionMode) {
            FloatBallPositionMode.CUSTOM -> {
                val centerX = coerceCustomCenterXFraction(settings.floatBallCustomCenterXFraction) * screenWidthPx
                (centerX - ballSizePx / 2f).roundToInt()
            }
            else -> dockedBallLeftPx(
                activeSide = activeSide,
                ballSizePx = ballSizePx,
                screenWidth = screenWidthPx,
                visibleFraction = coerceVisibleFraction(settings.floatBallVisibleFraction),
            )
        }
        val top = (centerY - ballSizePx / 2f).roundToInt()
            .coerceIn(marginPx, screenHeightPx - ballSizePx - marginPx)
        return left to top
    }

    fun ballCenterPx(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Pair<Float, Float> {
        val ballSizePx = ballSizePx(settings, metrics.density)
        val (left, top) = ballTopLeft(settings, metrics, activeSide, screenWidthPx, screenHeightPx)
        return (left + ballSizePx / 2f) to (top + ballSizePx / 2f)
    }

    fun ballWindowBounds(
        settings: AppSettings,
        metrics: DisplayMetrics,
        side: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Rect {
        val ballSizePx = ballSizePx(settings, metrics.density)
        val (left, top) = ballTopLeft(settings, metrics, side, screenWidthPx, screenHeightPx)
        return Rect(left, top, left + ballSizePx, top + ballSizePx)
    }

    fun lineStripBounds(
        settings: AppSettings,
        metrics: DisplayMetrics,
        side: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Rect {
        val density = metrics.density
        val width = lineTriggerWidthPx(settings, screenWidthPx, density)
        val height = lineTriggerHeightPx(settings, screenHeightPx, density)
        val centerY = dockCenterY(settings, screenHeightPx).roundToInt()
        val top = (centerY - height / 2).coerceIn(0, screenHeightPx - height)
        val left = when (side) {
            FloatBallSide.LEFT -> 0
            FloatBallSide.RIGHT -> screenWidthPx - width
        }
        return Rect(left, top, left + width, top + height)
    }

    /** After dragging out from the inactive-side line, the ball docks on that side. */
    fun activeSideAfterLineDragSwap(settings: AppSettings): FloatBallSide =
        FloatBallSide.opposite(resolvedActiveSide(settings))

    /** Ball window origin so the docked ball visual tracks [ballCenterX]/[ballCenterY]. */
    fun stripWindowOriginForBallCenter(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        ballCenterX: Float,
        ballCenterY: Float,
        screenHeightPx: Int = metrics.heightPixels,
    ): Pair<Int, Int> {
        val ballSizePx = ballSizePx(settings, metrics.density)
        val windowX = (ballCenterX - ballSizePx / 2f).roundToInt()
        val windowY = (ballCenterY - ballSizePx / 2f).roundToInt()
        return windowX to windowY.coerceIn(0, screenHeightPx - ballSizePx)
    }

    /**
     * Docked ball window X for [side].
     * [visibleFraction] is how much of the ball width stays on screen (1 = fully visible, flush to edge).
     */
    fun dockedBallLeftPx(
        activeSide: FloatBallSide,
        ballSizePx: Int,
        screenWidth: Int,
        visibleFraction: Float,
    ): Int = when (activeSide) {
        FloatBallSide.LEFT -> (-ballSizePx * (1f - visibleFraction)).roundToInt()
        FloatBallSide.RIGHT -> (screenWidth - ballSizePx * visibleFraction).roundToInt()
    }
}
