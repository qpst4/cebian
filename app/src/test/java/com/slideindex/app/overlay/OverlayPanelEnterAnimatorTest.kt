package com.slideindex.app.overlay

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OverlayPanelEnterAnimatorTest {

    @Test
    fun startEnter_updatesProgressAndCompletes() {
        var invalidateCount = 0
        val animator = OverlayPanelEnterAnimator(PanelSide.LEFT, { it * 2f }) { invalidateCount++ }

        animator.startEnter(
            panelMode = OverlayPanelMode.INDEX,
            onShellEnterEnded = {},
            onQuickLauncherEnterEnded = {},
        )

        assertEquals(0f, animator.progress)
        assertTrue(invalidateCount > 0)
    }

    @Test
    fun adjustedX_returnsOriginalWhenComplete() {
        val animator = OverlayPanelEnterAnimator(PanelSide.LEFT, { it }) {}
        animator.resetToComplete()
        val panel = RectF(10f, 0f, 110f, 100f)

        assertEquals(50f, animator.adjustedX(50f, panel))
    }

    @Test
    fun enterOffsetX_slidesFromLeftEdge() {
        val animator = OverlayPanelEnterAnimator(PanelSide.LEFT, { it }) {}
        animator.resetToHidden()
        val panel = RectF(0f, 0f, 100f, 100f)

        val offset = animator.enterOffsetX(panel)

        assertTrue(offset < 0f)
    }

    @Test
    fun startExit_invokesCallbackWhenAlreadyHidden() {
        var ended = false
        val animator = OverlayPanelEnterAnimator(PanelSide.RIGHT, { it }) {}
        animator.resetToHidden()

        animator.startExit(OverlayPanelMode.INDEX) { ended = true }

        assertTrue(ended)
        assertEquals(0f, animator.progress)
    }
}
