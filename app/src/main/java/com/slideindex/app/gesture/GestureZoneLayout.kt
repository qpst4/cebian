package com.slideindex.app.gesture

import android.graphics.RectF
import android.os.Build
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.edgeTriggerWidthDp
import com.slideindex.app.settings.interceptWindowWidthDp
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.util.coerceSafe

data class CollapsedWindowBounds(
    val widthPx: Int,
    val heightPx: Int,
    val yPx: Int,
)

class GestureZoneLayout(
    private val side: PanelSide,
    private var settings: AppSettings = AppSettings(),
    private var viewWidth: Int = 0,
    private var viewHeight: Int = 0,
    private var density: Float = 1f,
    private var sessionActive: Boolean = false,
    private var previewMode: Boolean = false,
    private var layoutHeight: Int = 0,
    private var windowOffsetY: Float = 0f,
    private var screenWidthPx: Int = 0,
    private var screenHeightPx: Int = 0,
) {
    fun update(
        settings: AppSettings,
        viewWidth: Int,
        viewHeight: Int,
        density: Float,
        sessionActive: Boolean,
        previewMode: Boolean,
        layoutHeight: Int = viewHeight,
        windowOffsetY: Float = 0f,
        screenWidthPx: Int = 0,
        screenHeightPx: Int = 0,
    ) {
        this.settings = settings
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        this.density = density
        this.sessionActive = sessionActive
        this.previewMode = previewMode
        this.layoutHeight = layoutHeight
        this.windowOffsetY = windowOffsetY
        if (screenWidthPx > 0) this.screenWidthPx = screenWidthPx
        if (screenHeightPx > 0) this.screenHeightPx = screenHeightPx
    }

    fun triggerZoneRects(): List<Pair<String, RectF>> =
        settings.triggerHandles(side).map { handle ->
            handle.id to rectForHandle(handle)
        }

    fun triggerZoneRect(handleId: String? = null): RectF {
        if (handleId != null) {
            return settings.triggerHandle(side, handleId)?.let { rectForHandle(it) } ?: triggerZoneUnionRect()
        }
        return triggerZoneUnionRect()
    }

    fun triggerZoneUnionRect(): RectF {
        val rects = triggerZoneRects().map { it.second }
        if (rects.isEmpty()) return RectF()
        var union = RectF(rects.first())
        rects.drop(1).forEach { union.union(it) }
        return union
    }

    fun findTriggerHandleAt(localX: Float, localY: Float): String? {
        // Later handles (e.g. a second trigger pair) win when zones overlap.
        return settings.triggerHandles(side).asReversed().firstOrNull { handle ->
            hitTestRectForHandle(handle).contains(localX, localY)
        }?.id
    }

    fun indexRailRect(): RectF {
        val refHeight = referenceHeight()
        if (viewWidth <= 0 || refHeight <= 0) return RectF()
        val w = railVisualWidth().coerceAtMost(edgeWidthPx().toFloat())
        val indexH = refHeight * settings.indexHeightFraction
        var top = (refHeight - indexH) / 2f - windowOffsetY
        top = top.coerceSafe(dp(8f) - windowOffsetY, refHeight - indexH - dp(8f) - windowOffsetY)
        return when (side) {
            PanelSide.LEFT -> RectF(0f, top, w, top + indexH)
            PanelSide.RIGHT -> RectF(viewWidth - w, top, viewWidth.toFloat(), top + indexH)
        }
    }

    fun edgeWidthPx(): Int {
        return (settings.edgeTriggerWidthDp(side) * density)
            .toInt()
            .coerceAtLeast(dp(16f).toInt())
    }

    /** Edge strip width wide enough to render the full halo glow (preview / capture windows). */
    fun glowAwareEdgeWidthPx(): Int = computeGlowAwareEdgeWidthPx(
        edgeTriggerWidthDp = settings.edgeTriggerWidthDp(side),
        handles = settings.triggerHandles(side),
        density = density,
    )

    fun interceptZoneRect(): RectF {
        if (!settings.interceptSystemBackGesture) return triggerZoneUnionRect()
        val trigger = triggerZoneUnionRect()
        val interceptWidth = settings.interceptWindowWidthDp(side) * density
        return when (side) {
            PanelSide.LEFT -> RectF(0f, trigger.top, interceptWidth, trigger.bottom)
            PanelSide.RIGHT -> RectF(viewWidth - interceptWidth, trigger.top, viewWidth.toFloat(), trigger.bottom)
        }
    }

    fun containsInterceptZone(localX: Float, localY: Float): Boolean =
        interceptZoneRect().contains(localX, localY)

    fun containsInterceptZoneAtScreen(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {
        if (!settings.interceptSystemBackGesture) {
            return containsTriggerAtScreen(rawX, rawY, localX, localY)
        }
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            val interceptWidth = settings.interceptWindowWidthDp(side) * density
            val trigger = settings.triggerHandles(side)
            if (trigger.isEmpty()) return false
            var top = screenHeightPx.toFloat()
            var bottom = 0f
            trigger.forEach { handle ->
                top = minOf(top, screenHeightPx * handle.topFraction)
                bottom = maxOf(bottom, screenHeightPx * (handle.topFraction + handle.heightFraction))
            }
            val rect = when (side) {
                PanelSide.LEFT -> RectF(0f, top, interceptWidth, bottom)
                PanelSide.RIGHT -> RectF(
                    screenWidthPx - interceptWidth,
                    top,
                    screenWidthPx.toFloat(),
                    bottom,
                )
            }
            return rect.contains(rawX, rawY)
        }
        return containsInterceptZone(localX, localY)
    }

    fun railVisualWidth(): Float = dp(22f)

    fun containsTrigger(localX: Float, localY: Float): Boolean =
        findTriggerHandleAt(localX, localY) != null

    /** Hit-test in screen coordinates so trigger detection stays correct while the overlay window moves/resizes. */
    fun containsTriggerAtScreen(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            return findTriggerHandleAtScreen(rawX, rawY) != null
        }
        return containsTrigger(localX, localY)
    }

    fun findTriggerHandleAtScreen(rawX: Float, rawY: Float): String? {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return null
        val w = edgeWidthPx().toFloat()
        return settings.triggerHandles(side).asReversed().firstOrNull { handle ->
            val (top, bottom) = verticalSpanPx(handle, screenHeightPx.toFloat(), forHitTest = true)
            val rect = when (side) {
                PanelSide.LEFT -> RectF(0f, top, w, bottom)
                PanelSide.RIGHT -> RectF(
                    screenWidthPx - w,
                    top,
                    screenWidthPx.toFloat(),
                    bottom,
                )
            }
            rect.contains(rawX, rawY)
        }?.id
    }

    fun isInRailZone(localX: Float): Boolean {
        val rail = indexRailRect()
        return when (side) {
            PanelSide.LEFT -> localX <= rail.right + dp(10f)
            PanelSide.RIGHT -> localX >= rail.left - dp(10f)
        }
    }

    private fun rectForHandle(handle: TriggerHandle): RectF {
        if (viewWidth <= 0 || viewHeight <= 0) return RectF()
        val refHeight = referenceHeight()
        val (topOnScreen, bottomOnScreen) = verticalSpanPx(handle, refHeight.toFloat(), forHitTest = false)
        val top = topOnScreen - windowOffsetY
        val zoneHeight = bottomOnScreen - topOnScreen
        val w = edgeWidthPx().toFloat()
        return when (side) {
            PanelSide.LEFT -> RectF(0f, top, w, top + zoneHeight)
            PanelSide.RIGHT -> RectF(viewWidth - w, top, viewWidth.toFloat(), top + zoneHeight)
        }
    }

    private fun verticalSpanPx(
        handle: TriggerHandle,
        refHeight: Float,
        forHitTest: Boolean,
    ): Pair<Float, Float> {
        val minSpanPx = if (forHitTest) dp(12f) else 0f
        var top = refHeight * handle.topFraction
        var bottom = top + refHeight * handle.heightFraction
        if (bottom - top < minSpanPx) {
            val center = (top + bottom) / 2f
            top = center - minSpanPx / 2f
            bottom = center + minSpanPx / 2f
        }
        return top to bottom
    }

    private fun hitTestRectForHandle(handle: TriggerHandle): RectF {
        if (viewWidth <= 0 || viewHeight <= 0) return RectF()
        val refHeight = referenceHeight().toFloat()
        val (topOnScreen, bottomOnScreen) = verticalSpanPx(handle, refHeight, forHitTest = true)
        val top = topOnScreen - windowOffsetY
        val zoneHeight = bottomOnScreen - topOnScreen
        val w = edgeWidthPx().toFloat()
        return when (side) {
            PanelSide.LEFT -> RectF(0f, top, w, top + zoneHeight)
            PanelSide.RIGHT -> RectF(viewWidth - w, top, viewWidth.toFloat(), top + zoneHeight)
        }
    }

    private fun referenceHeight(): Int =
        layoutHeight.coerceAtLeast(viewHeight).coerceAtLeast(1)

    private fun dp(value: Float): Float = value * density

    companion object {
        private fun computeGlowAwareEdgeWidthPx(
            edgeTriggerWidthDp: Float,
            handles: List<TriggerHandle>,
            density: Float,
        ): Int {
            val edgeWidthPx = (edgeTriggerWidthDp * density)
                .toInt()
                .coerceAtLeast((16f * density).toInt().coerceAtLeast(1))
            val maxHaloWidthPx = handles.maxOfOrNull { handle ->
                (handle.design.haloSizeDp * 2f * density).toInt()
            } ?: 0
            return edgeWidthPx.coerceAtLeast(maxHaloWidthPx)
        }

        private fun captureWidthPx(settings: AppSettings, side: PanelSide, density: Float): Int =
            computeGlowAwareEdgeWidthPx(
                edgeTriggerWidthDp = settings.edgeTriggerWidthDp(side),
                handles = settings.triggerHandles(side),
                density = density,
            )

        private fun exclusionWidthPx(settings: AppSettings, side: PanelSide, density: Float): Int =
            (settings.interceptWindowWidthDp(side) * density)
                .toInt()
                .coerceAtLeast(captureWidthPx(settings, side, density))

        /** One capture window per enabled trigger handle so gaps along the edge stay interactive. */
        fun computeCaptureWindowBounds(
            settings: AppSettings,
            side: PanelSide,
            screenHeightPx: Int,
            density: Float,
        ): List<CollapsedWindowBounds> = computeVerticalStripBounds(
            widthPx = captureWidthPx(settings, side, density),
            settings = settings,
            side = side,
            screenHeightPx = screenHeightPx,
            density = density,
        )

        /**
         * Wide, non-touchable strips that opt out of the system back-gesture region without
         * blocking taps/scrolls in the app below (SideGesture-style exclusion rects).
         */
        fun computeSystemGestureExclusionBounds(
            settings: AppSettings,
            side: PanelSide,
            screenHeightPx: Int,
            density: Float,
        ): List<CollapsedWindowBounds> {
            if (!settings.interceptSystemBackGesture) return emptyList()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
            return computeVerticalStripBounds(
                widthPx = exclusionWidthPx(settings, side, density),
                settings = settings,
                side = side,
                screenHeightPx = screenHeightPx,
                density = density,
            )
        }

        private fun computeVerticalStripBounds(
            widthPx: Int,
            settings: AppSettings,
            side: PanelSide,
            screenHeightPx: Int,
            density: Float,
        ): List<CollapsedWindowBounds> {
            if (screenHeightPx <= 0) {
                return listOf(CollapsedWindowBounds(widthPx = widthPx, heightPx = 1, yPx = 0))
            }
            val handles = settings.triggerHandles(side)
            if (handles.isEmpty()) {
                return listOf(CollapsedWindowBounds(widthPx = widthPx, heightPx = 1, yPx = 0))
            }
            val padPx = (4f * density).toInt().coerceAtLeast(1)
            return handles.map { handle ->
                val topPx = (screenHeightPx * handle.topFraction).toInt().coerceIn(0, screenHeightPx - 1)
                val bottomPx = (screenHeightPx * handle.bottomFraction).toInt()
                    .coerceIn(topPx + 1, screenHeightPx)
                val top = (topPx - padPx).coerceAtLeast(0)
                val bottom = (bottomPx + padPx).coerceAtMost(screenHeightPx)
                CollapsedWindowBounds(
                    widthPx = widthPx,
                    heightPx = (bottom - top).coerceAtLeast(1),
                    yPx = top,
                )
            }
        }

        fun computeCollapsedWindowBounds(
            settings: AppSettings,
            side: PanelSide,
            screenHeightPx: Int,
            density: Float,
        ): CollapsedWindowBounds {
            val bounds = computeCaptureWindowBounds(settings, side, screenHeightPx, density)
            if (bounds.size == 1) return bounds.first()
            val widthPx = captureWidthPx(settings, side, density)
            if (screenHeightPx <= 0) {
                return CollapsedWindowBounds(widthPx = widthPx, heightPx = 1, yPx = 0)
            }
            var top = screenHeightPx
            var bottom = 0
            bounds.forEach { bound ->
                top = minOf(top, bound.yPx)
                bottom = maxOf(bottom, bound.yPx + bound.heightPx)
            }
            return CollapsedWindowBounds(
                widthPx = widthPx,
                heightPx = (bottom - top).coerceAtLeast(1),
                yPx = top.coerceAtLeast(0),
            )
        }
    }
}
