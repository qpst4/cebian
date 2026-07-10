package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.PanelSide.LEFT
import com.slideindex.app.overlay.PanelSide.RIGHT
import com.slideindex.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class QuickLauncherPanelLayoutEngineTest {

    @Test
    fun gridLayoutInfo_clampsColumnsAndRows() {
        val settings = AppSettings(quickLauncherColumnsPerPage = 10, quickLauncherRowsPerPage = 20)
        val info = QuickLauncherPanelLayoutEngine.gridLayoutInfo(settings, cellWidth = 100f, gridPadding = 8f)
        assertEquals(5, info.panelColumns)
        assertEquals(6, info.rows)
        assertEquals(5 * 100f + 16f, info.panelWidth, 0.01f)
    }

    @Test
    fun contentHeight_includesHeaderAndPadding() {
        val height = QuickLauncherPanelLayoutEngine.contentHeight(
            rows = 4,
            cellHeight = 120f,
            gridPadding = 8f,
            headerHeight = 48f,
        )
        assertEquals(4 * 120f + 16f + 48f, height, 0.01f)
    }

    @Test
    fun anchoredPanelRect_placesPanelToRightOfLeftTrigger() {
        val host = fakeHost(
            side = LEFT,
            trigger = RectF(0f, 400f, 24f, 800f),
            anchorY = 600f,
        )
        val rect = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = 300f,
            contentHeight = 400f,
            anchorLocalY = 600f,
        )
        assertEquals(24f + host.dp(8f), rect.left, 0.01f)
        assertEquals(400f, rect.height(), 0.01f)
        assertEquals(600f - 200f, rect.top, 0.01f)
    }

    @Test
    fun anchoredPanelRect_placesPanelToLeftOfRightTrigger() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 1080,
            trigger = RectF(1056f, 400f, 1080f, 800f),
            anchorY = 600f,
        )
        val rect = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = 300f,
            contentHeight = 400f,
            anchorLocalY = 600f,
        )
        assertEquals(1056f - host.dp(8f) - 300f, rect.left, 0.01f)
    }

    @Test
    fun offsetForToolbar_shiftsRightPanelAwayFromToolbarReserve() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 400,
            trigger = RectF(376f, 0f, 400f, 200f),
        )
        val base = RectF(20f, 100f, 320f, 500f)
        val adjusted = QuickLauncherPanelLayoutEngine.offsetForToolbar(host, base, reserveWidth = 120f)
        assertEquals(84f, adjusted.left, 0.01f)
        assertEquals(384f, adjusted.right, 0.01f)
    }

    private fun fakeHost(
        side: PanelSide,
        viewWidth: Int = 1080,
        viewHeight: Int = 1920,
        trigger: RectF = RectF(0f, 400f, 24f, 800f),
        anchorY: Float = 600f,
    ): QuickLauncherOverlayController.Host = object : QuickLauncherOverlayController.Host {
        override val context: Context = RuntimeEnvironment.getApplication()
        override fun settings(): AppSettings = AppSettings()
        override fun side(): PanelSide = side
        override fun apps(): List<AppInfo> = emptyList()
        override fun gestureSession(): GestureSession = error("unused")
        override fun zoneLayout(): GestureZoneLayout = error("unused")
        override fun pathRecognizer(): SwipePathRecognizer = error("unused")
        override fun actionExecutor(): ActionExecutor = error("unused")
        override fun panelGridSession(): PanelGridSession = PanelGridSession()
        override fun panelEnterProgress(): Float = 0f
        override fun panelEnterAdjustedX(localX: Float, panel: RectF): Float = localX
        override fun panelEnterOffsetX(panel: RectF): Float = 0f
        override fun panelContentRect(): RectF = RectF()
        override fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit) = Unit
        override fun activeTriggerZoneRect(): RectF = trigger
        override fun viewWidth(): Int = viewWidth
        override fun viewHeight(): Int = viewHeight
        override fun dp(value: Float): Float = value
        override fun sp(value: Float): Float = value
        override fun viewLocationOnScreen(): IntArray = intArrayOf(0, 0)
        override fun invalidate() = Unit
        override fun invalidatePartial(left: Int, top: Int, right: Int, bottom: Int) = Unit
        override fun post(action: () -> Unit) = Unit
        override fun postDelayed(runnable: Runnable, delayMs: Long) = Unit
        override fun removeCallbacks(runnable: Runnable) = Unit
        override fun hapticTick() = Unit
        override fun hapticLongThreshold() = Unit
        override fun hapticConfirmLaunch() = Unit
        override fun startPanelExitAnimation(onEnd: () -> Unit) = Unit
        override fun notifyPresentationTouchRequirementChanged() = Unit
        override fun onQuickLauncherItemsPersist(items: List<QuickLauncherItem>) = Unit
        override fun onOverlayWindowSuspend() = Unit
        override fun onOverlayWindowResume() = Unit
    }
}
