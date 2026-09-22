package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], instrumentedPackages = ["com.slideindex.app.gesture"])
class SwipePathRecognizerTest {

    private val leftStrip = RectF(0f, 0f, 20f, 2000f)
    private val rightStrip = RectF(180f, 0f, 200f, 2000f)

    private val defaultOptions = SwipePathRecognizer.ClassifyOptions.DEFAULT

    private fun SwipePathRecognizer.beginHoverGesture(
        x: Float,
        y: Float,
        strip: RectF = leftStrip,
        options: SwipePathRecognizer.ClassifyOptions = hoverConfiguredOptions(),
    ) {
        onTouchDown(x, y, strip)
        applyCompoundGestureGate(options)
        applyHoverSettings(durationMs = 250L)
    }

    private fun SwipePathRecognizer.beginGesture(x: Float, y: Float, strip: RectF = leftStrip) {
        onTouchDown(x, y, strip)
        applyCompoundGestureGate(defaultOptions)
        applyHoverSettings(durationMs = 250L)
    }

    private fun SwipePathRecognizer.holdInwardAt(x: Float, y: Float) {
        onTouchMove(x, y)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        onTouchMove(x, y)
    }

    private fun hoverConfiguredOptions(): SwipePathRecognizer.ClassifyOptions =
        SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger.isHoverSwipe || trigger == GestureTriggerType.SHORT_SWIPE_IN
            },
        )

    @Test
    fun classifyOnUp_leftPanelInwardSwipe_returnsShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        val result = recognizer.classifyOnUp(90f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_rightPanelInwardSwipe_returnsShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.RIGHT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(195f, 100f, rightStrip)
        val result = recognizer.classifyOnUp(130f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_topPanelLenientTapWithSlightInwardMove_returnsSingleTap() {
        val topStrip = RectF(400f, 0f, 600f, 66f)
        val recognizer = SwipePathRecognizer(PanelSide.TOP, density = 3f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(500f, 10f, topStrip)
        val result = recognizer.classifyOnUp(
            500f,
            55f,
            SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP,
        )

        assertEquals(GestureTriggerType.SHORT_SINGLE_TAP, result?.trigger)
    }

    @Test
    fun classifyOnUp_fastSwipeInAndReturn_withLenientTap_doesNotTriggerSingleTap() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f) // Swiped 80dp into screen
        recognizer.onTouchMove(10f, 100f) // Returned near edge (10dp from start)
        val result = recognizer.classifyOnUp(
            10f,
            100f,
            SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP,
        )

        assertNotEquals(GestureTriggerType.SHORT_SINGLE_TAP, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenUp_returnsShortSwipeInUp() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f) // Swiped inward first (inward >= 60dp)
        recognizer.holdInwardAt(80f, 100f)
        recognizer.onTouchMove(80f, 60f) // Then turned upward (dy = -40dp, |dy| >= 32dp)
        val result = recognizer.classifyOnUp(80f, 55f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_UP, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenDown_returnsShortSwipeInDown() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f) // Swiped inward first
        recognizer.holdInwardAt(80f, 100f)
        recognizer.onTouchMove(80f, 140f) // Then turned downward
        val result = recognizer.classifyOnUp(80f, 170f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_DOWN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelDiagonalUpRight_returnsShortSwipeUpRight() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(40f, 60f) // Direct diagonal
        val result = recognizer.classifyOnUp(80f, 20f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP_RIGHT, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenUpUnconfigured_fallsBackToUpRight() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(80f, 100f) // Swiped inward first
        recognizer.onTouchMove(80f, 60f) // Then turned upward (dy = -40dp)

        val unconfiguredOptions = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger != GestureTriggerType.SHORT_SWIPE_IN_UP &&
                    trigger != GestureTriggerType.LONG_SWIPE_IN_UP
            },
        )
        val result = recognizer.classifyOnUp(80f, 55f, unconfiguredOptions)

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP_RIGHT, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenDownUnconfigured_fallsBackToDownRight() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(80f, 100f) // Swiped inward first
        recognizer.onTouchMove(60f, 180f) // Then turned downward (dx = 60dp, dy = +80dp)

        val unconfiguredOptions = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger != GestureTriggerType.SHORT_SWIPE_IN_DOWN &&
                    trigger != GestureTriggerType.LONG_SWIPE_IN_DOWN
            },
        )
        val result = recognizer.classifyOnUp(60f, 180f, unconfiguredOptions)

        assertEquals(GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelSwipeInAndBack_returnsShortSwipeInAndBack() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f) // Swiped inward (peak = 80dp >= 60dp)
        recognizer.holdInwardAt(80f, 100f)
        recognizer.onTouchMove(40f, 100f) // Retracted back by 40dp (>= 16dp)
        val result = recognizer.classifyOnUp(30f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_AND_BACK, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelSwipeInAndBackUnconfigured_fallsBackToNullOrStraight() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(80f, 100f) // Swiped inward (peak = 80dp)
        recognizer.onTouchMove(10f, 100f) // Retracted all the way back near edge (dx = 10dp < 60dp)

        val unconfiguredOptions = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger != GestureTriggerType.SHORT_SWIPE_IN_AND_BACK
            },
        )
        val result = recognizer.classifyOnUp(10f, 100f, unconfiguredOptions)

        // Since current inward distance 10dp is below short threshold 60dp, straight swipe is not triggered -> null (canceled)
        assertEquals(null, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardWithSlightDownDrift_staysShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(40f, 120f)
        val result = recognizer.classifyOnUp(80f, 140f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardWithDownDriftExceedingTurnSlop_staysShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        val result = recognizer.classifyOnUp(100f, 135f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenDownWhileOverallStillIn_returnsShortSwipeInDown() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(80f, 100f)
        val result = recognizer.classifyOnUp(80f, 140f)

        // 免悬停后由第二段单独判向：纯下移 40dp >= TURN_SLOP，整体仍偏内滑也不再阻止组合。
        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_DOWN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelHorizontalThenDownWithoutLeavingInSector_returnsShortSwipeInDown() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(90f, 100f)
        val result = recognizer.classifyOnUp(100f, 135f)

        // 内滑 90dp 后下移 35dp（沿边为主）即组合，整体仍在内滑扇区不再阻止。
        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_DOWN, result?.trigger)
    }

    @Test
    fun classifyPartial_leftPanelSwipeInAndBack_returnsShortSwipeInAndBackEvenNearEdge() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f) // Inward peak = 80dp >= 60dp
        recognizer.holdInwardAt(80f, 100f)
        recognizer.onTouchMove(20f, 100f) // Finger moves back to 20dp (below short distance 60dp)

        val partialResult = recognizer.classifyPartial(20f, 100f)
        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_AND_BACK, partialResult?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardHold_returnsShortSwipeInHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        val result = recognizer.classifyOnUp(80f, 100f, hoverConfiguredOptions())

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_HOVER, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardHoldThenLongSwipe_cancelsHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        recognizer.onTouchMove(80f, 100f)
        recognizer.onTouchMove(130f, 100f)
        val result = recognizer.classifyOnUp(130f, 100f)

        assertEquals(GestureTriggerType.LONG_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardHoverThenUp_returnsShortSwipeInUp() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.applyHoverSettings(durationMs = 250L)

        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        recognizer.onTouchMove(80f, 100f)
        recognizer.onTouchMove(80f, 60f)
        val result = recognizer.classifyOnUp(80f, 55f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_UP, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardHoldWithMovement_cancelsHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.applyHoverSettings(durationMs = 250L)

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(80f, 100f)
        recognizer.onTouchMove(95f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        recognizer.onTouchMove(95f, 100f)
        val result = recognizer.classifyOnUp(95f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelDiagonalHold_returnsShortSwipeUpRightHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(80f, 20f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        val result = recognizer.classifyOnUp(80f, 20f, hoverConfiguredOptions())

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP_RIGHT_HOVER, result?.trigger)
    }

    @Test
    fun classifyPartial_leftPanelInwardHoldWithHoverConfigured_defersShortSwipe() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)

        val partial = recognizer.classifyPartial(80f, 100f, hoverConfiguredOptions())
        assertEquals(null, partial?.trigger)
    }

    @Test
    fun classifyOnUp_continuousInwardSwipeThenHold_returnsShortSwipeInHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(40f, 100f)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        val result = recognizer.classifyOnUp(80f, 100f, hoverConfiguredOptions())

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_HOVER, result?.trigger)
    }

    @Test
    fun classifyPartial_continuousInwardSwipeWithHoverConfigured_defersShortSwipe() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.applyHoverSettings(durationMs = 250L)

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(40f, 100f)
        recognizer.onTouchMove(80f, 100f)

        val partial = recognizer.classifyPartial(80f, 100f, hoverConfiguredOptions())
        assertEquals(null, partial?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardHoldWithHoverConfigured_returnsHoverNotShortSwipe() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        val result = recognizer.classifyOnUp(80f, 100f, hoverConfiguredOptions())

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_HOVER, result?.trigger)
    }

    @Test
    fun classifyOnUp_hoverHoldWithSmallDirectionJitter_stillReturnsHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(200L, TimeUnit.MILLISECONDS)
        recognizer.onTouchMove(84f, 100f)
        ShadowSystemClock.advanceBy(100L, TimeUnit.MILLISECONDS)
        val result = recognizer.classifyOnUp(84f, 100f, hoverConfiguredOptions())

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_HOVER, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenUpWithoutHover_returnsShortSwipeInUp() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f) // 内滑越过短距阈值即就绪，不再要求悬停
        recognizer.onTouchMove(80f, 60f) // 直接转向
        val result = recognizer.classifyOnUp(80f, 55f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_UP, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelLongInwardHoverThenUp_doesNotReturnCompoundInUp() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(150f, 100f)
        recognizer.holdInwardAt(150f, 100f)
        recognizer.onTouchMove(150f, 60f)
        val result = recognizer.classifyOnUp(150f, 55f)

        assertEquals(GestureTriggerType.LONG_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_hoverHoldWithLargeDirectionProgress_resetsTimer() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(200L, TimeUnit.MILLISECONDS)
        recognizer.onTouchMove(95f, 100f)
        ShadowSystemClock.advanceBy(200L, TimeUnit.MILLISECONDS)
        val result = recognizer.classifyOnUp(95f, 100f, hoverConfiguredOptions())

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_inwardHoldWithHoverAndLConfigured_returnsHoverNotShortIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger == GestureTriggerType.SHORT_SWIPE_IN_HOVER ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN_UP ||
                    trigger == GestureTriggerType.LONG_SWIPE_IN_UP
            },
        )

        recognizer.beginHoverGesture(0f, 100f, options = options)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        val result = recognizer.classifyOnUp(80f, 100f, options)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_HOVER, result?.trigger)
    }

    @Test
    fun classifyPartial_continuousLongInwardWithLConfigured_returnsLongSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger == GestureTriggerType.LONG_SWIPE_IN ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN_UP ||
                    trigger == GestureTriggerType.LONG_SWIPE_IN_UP
            },
        )

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(80f, 100f)
        recognizer.onTouchMove(150f, 100f)

        val partial = recognizer.classifyPartial(150f, 100f, options)

        assertEquals(GestureTriggerType.LONG_SWIPE_IN, partial?.trigger)
    }

    @Test
    fun classifyPartial_inwardHoldWithLConfigured_returnsShortSwipeInUntilTurned() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger == GestureTriggerType.LONG_SWIPE_IN ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN_UP
            },
        )

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(80f, 100f)

        val partial = recognizer.classifyPartial(80f, 100f, options)

        // 免悬停后不再为组合压住基础短滑：还没转向就是普通内滑短滑。
        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, partial?.trigger)
    }

    @Test
    fun classifyOnUp_continuousLongInwardWithHoverAndLConfigured_returnsLongNotHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger == GestureTriggerType.SHORT_SWIPE_IN_HOVER ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN ||
                    trigger == GestureTriggerType.LONG_SWIPE_IN ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN_UP
            },
        )

        recognizer.beginHoverGesture(0f, 100f, options = options)
        recognizer.onTouchMove(150f, 100f)
        val result = recognizer.classifyOnUp(150f, 100f, options)

        assertEquals(GestureTriggerType.LONG_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenBackWithoutHover_returnsShortSwipeInAndBack() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(90f, 100f) // 越过短距阈值即就绪，不再要求悬停
        recognizer.onTouchMove(50f, 100f) // 回缩 40dp >= 折返门槛 28dp
        val result = recognizer.classifyOnUp(50f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_AND_BACK, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenSmallRetract_staysShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(90f, 100f)
        recognizer.onTouchMove(64f, 100f) // 回缩 26dp < 折返门槛 28dp
        val result = recognizer.classifyOnUp(64f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenShortUpJitter_staysShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        recognizer.onTouchMove(82f, 78f) // 第二段仅 22dp < TURN_SLOP(32dp)
        val result = recognizer.classifyOnUp(82f, 78f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_hoverSlotSurvivesPastCompoundArmingAnchor() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger ->
                trigger == GestureTriggerType.SHORT_SWIPE_IN_HOVER ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN ||
                    trigger == GestureTriggerType.SHORT_SWIPE_IN_UP
            },
        )
        recognizer.beginHoverGesture(0f, 100f, options = options)
        recognizer.onTouchMove(70f, 100f) // 组合锚点落在 70dp
        recognizer.onTouchMove(100f, 100f)
        recognizer.onTouchMove(100f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        recognizer.onTouchMove(100f, 100f)
        val result = recognizer.classifyOnUp(100f, 100f, options)

        // 组合锚点不再作废悬停：停在 100dp 静止后松手仍走悬停槽位。
        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_HOVER, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelInwardThenBackToStart_cancelsGesture() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(90f, 100f) // 内滑越过短距阈值
        recognizer.onTouchMove(8f, 100f) // 回缩到起手位置附近（<= 起手内距 + 12dp）
        val result = recognizer.classifyOnUp(8f, 100f)

        // 滑回边缘/起点视为撤销：既不判折返，也不判内滑短滑。
        assertEquals(null, result?.trigger)
    }

    @Test
    fun classifyOnUp_hoverDurationZero_satisfiesHoverImmediately() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.applyHoverSettings(durationMs = 0L)
        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(hoverConfiguredOptions())
        recognizer.onTouchMove(80f, 100f) // 0ms：越过短距阈值即视为悬停成立，无需等待
        val result = recognizer.classifyOnUp(80f, 100f, hoverConfiguredOptions())

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN_HOVER, result?.trigger)
    }

    @Test
    fun classifyOnUp_hoverThenDriftBeyondSlop_isNotHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginHoverGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        ShadowSystemClock.advanceBy(300L, TimeUnit.MILLISECONDS)
        recognizer.onTouchMove(80f, 100f) // 悬停成立，锚点 (80,100)
        recognizer.onTouchMove(87f, 100f) // 偏移 7dp，超过 6dp 静止容差
        val result = recognizer.classifyOnUp(87f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_continuousSlideWithRepeatedSamples_isNotHover() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginHoverGesture(0f, 100f)

        // 持续滑动：每次前进 4dp，并在每步夹杂一帧位置不变的重复采样。
        // 锚点固定 + 位移容差后，计时会被不断重启，攒不到设定时长。
        var x = 60f
        repeat(8) {
            recognizer.onTouchMove(x, 100f)
            ShadowSystemClock.advanceBy(80L, TimeUnit.MILLISECONDS)
            recognizer.onTouchMove(x, 100f)
            ShadowSystemClock.advanceBy(40L, TimeUnit.MILLISECONDS)
            x += 4f
        }
        val result = recognizer.classifyOnUp(x - 4f, 100f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    private fun alongOptions(vararg triggers: GestureTriggerType): SwipePathRecognizer.ClassifyOptions =
        SwipePathRecognizer.ClassifyOptions(
            isTriggerConfigured = { trigger -> trigger in triggers },
        )

    @Test
    fun classifyOnUp_leftPanelUpThenInward_returnsShortSwipeUpIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = alongOptions(
            GestureTriggerType.SHORT_SWIPE_UP,
            GestureTriggerType.SHORT_SWIPE_UP_IN,
        )
        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(0f, 40f) // 沿边上滑 60dp 越过短距阈值
        recognizer.onTouchMove(40f, 40f) // 第二段转入内滑 40dp（>= TURN_SLOP）
        val result = recognizer.classifyOnUp(40f, 40f, options)

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelDownThenInwardUnconfigured_staysDownRight() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = alongOptions(GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT)
        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(0f, 160f) // 沿边下滑 60dp
        recognizer.onTouchMove(40f, 160f) // 转入内滑
        val result = recognizer.classifyOnUp(40f, 160f, options)

        // 未配置「先下滑再向内」时照旧走斜下滑。
        assertEquals(GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelUpThenBack_returnsShortSwipeUpAndBack() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = alongOptions(
            GestureTriggerType.SHORT_SWIPE_UP,
            GestureTriggerType.SHORT_SWIPE_UP_AND_BACK,
        )
        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(0f, 10f) // 上滑 90dp
        recognizer.onTouchMove(0f, 50f) // 回缩 40dp（>= 折返门槛 28dp）
        val result = recognizer.classifyOnUp(0f, 50f, options)

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP_AND_BACK, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelUpThenSmallRetract_staysShortSwipeUp() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = alongOptions(
            GestureTriggerType.SHORT_SWIPE_UP,
            GestureTriggerType.SHORT_SWIPE_UP_AND_BACK,
        )
        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(0f, 10f) // 上滑 90dp
        recognizer.onTouchMove(0f, 36f) // 只回缩 26dp < 28dp
        val result = recognizer.classifyOnUp(0f, 36f, options)

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelUpThenBackToStart_cancelsGesture() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = alongOptions(
            GestureTriggerType.SHORT_SWIPE_UP,
            GestureTriggerType.SHORT_SWIPE_UP_AND_BACK,
        )
        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(0f, 10f) // 上滑 90dp
        recognizer.onTouchMove(0f, 108f) // 一路滑回起点附近
        val result = recognizer.classifyOnUp(0f, 108f, options)

        assertEquals(null, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelDiagramSwipeUpRight_isNotStolenByUpIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = alongOptions(
            GestureTriggerType.SHORT_SWIPE_UP_RIGHT,
            GestureTriggerType.SHORT_SWIPE_UP_IN,
        )
        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(20f, 60f) // 平缓斜上（未进入沿边扇区）
        recognizer.onTouchMove(40f, 20f)
        val result = recognizer.classifyOnUp(40f, 20f, options)

        // 直线/平缓斜滑不会被「先上滑再向内」抢走。
        assertEquals(GestureTriggerType.SHORT_SWIPE_UP_RIGHT, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelLongUpThenInward_staysLongSwipeUp() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = alongOptions(
            GestureTriggerType.LONG_SWIPE_UP,
            GestureTriggerType.SHORT_SWIPE_UP_IN,
        )
        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(0f, -30f) // 上滑 130dp，越过长距阈值
        recognizer.onTouchMove(40f, -30f)
        val result = recognizer.classifyOnUp(40f, -30f, options)

        assertEquals(GestureTriggerType.LONG_SWIPE_UP, result?.trigger)
    }

    @Test
    fun classifyOnUp_bottomPanelLeftThenInward_returnsShortSwipeUpIn() {
        val bottomStrip = RectF(0f, 1980f, 1080f, 2000f)
        val recognizer = SwipePathRecognizer(PanelSide.BOTTOM, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        val options = alongOptions(
            GestureTriggerType.SHORT_SWIPE_UP,
            GestureTriggerType.SHORT_SWIPE_UP_IN,
        )
        recognizer.onTouchDown(500f, 1990f, bottomStrip)
        recognizer.applyCompoundGestureGate(options)
        recognizer.onTouchMove(440f, 1990f) // 沿边向左滑 60dp（底边「UP 家族」）
        recognizer.onTouchMove(440f, 1950f) // 转入内滑（底边向上）40dp
        val result = recognizer.classifyOnUp(440f, 1950f, options)

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP_IN, result?.trigger)
    }
}
