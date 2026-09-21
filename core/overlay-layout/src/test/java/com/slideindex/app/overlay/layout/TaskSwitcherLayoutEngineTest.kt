package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.PanelSide.LEFT
import com.slideindex.app.overlay.PanelSide.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TaskSwitcherLayoutEngineTest {

    @Test
    fun compute_placesPanelToRightOfLeftTrigger() {
        val host = fakeHost(
            side = LEFT,
            trigger = RectF(0f, 400f, 24f, 800f),
        )
        val rows = listOf(
            TaskSwitcherRowEntry("com.example.a", 1),
            TaskSwitcherRowEntry("com.example.b", 2),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = rows,
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(24f + host.dp(10f), layout.panelRect.left, 0.01f)
        assertEquals(2, layout.rows.size)
        assertEquals("com.example.a", layout.rows[0].entry.packageName)
    }

    @Test
    fun compute_placesPanelToLeftOfRightTrigger() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 1080,
            trigger = RectF(1056f, 400f, 1080f, 800f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a")),
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(1056f - host.dp(10f) - layout.panelRect.width(), layout.panelRect.left, 0.01f)
    }

    @Test
    fun compute_clampsScrollOffset() {
        val host = fakeHost(
            side = LEFT,
            viewHeight = 1920,
            trigger = RectF(0f, 0f, 24f, 400f),
        )
        val rowHeight = host.dp(42f)
        val rows = (1..20).map { TaskSwitcherRowEntry("com.example.$it", it) }
        val (layout, offset) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = rows,
            scrollOffset = 9999f,
            anchorLocalY = 200f,
        )
        val peek = TaskSwitcherLayoutEngine.scrollPeekHeight(host, rowHeight)
        val expectedVisible = rowHeight * TaskSwitcherLayoutEngine.MAX_VISIBLE_ROWS + peek
        assertEquals(expectedVisible, layout.listRect.height(), 0.01f)
        assertEquals(peek, layout.listScrollPeekHeight, 0.01f)
        assertEquals(20 * rowHeight - expectedVisible, layout.maxScrollOffset, 0.01f)
        assertEquals(layout.maxScrollOffset, offset, 0.01f)
    }

    @Test
    fun compute_atMostNineRows_noScrollPeek() {
        val host = fakeHost(side = LEFT)
        val rowHeight = host.dp(42f)
        val rows = (1..9).map { TaskSwitcherRowEntry("com.example.$it", it) }
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = rows,
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(0f, layout.listScrollPeekHeight, 0.01f)
        assertEquals(9 * rowHeight, layout.listRect.height(), 0.01f)
        assertEquals(0f, layout.maxScrollOffset, 0.01f)
    }

    private fun fakeHost(
        side: PanelSide,
        viewWidth: Int = 1080,
        viewHeight: Int = 1920,
        trigger: RectF = RectF(0f, 400f, 24f, 800f),
    ): TaskSwitcherLayoutHost = object : TaskSwitcherLayoutHost {
        override fun side(): PanelSide = side
        override fun activeTriggerZoneRect(): RectF = trigger
        override fun viewWidth(): Int = viewWidth
        override fun viewHeight(): Int = viewHeight
        override fun dp(value: Float): Float = value
    }
}
