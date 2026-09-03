package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], instrumentedPackages = ["com.slideindex.app.gesture"])
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
        applyHoverSettings(durationMs = 250L, inwardCompoundEnabled = true)
    }

    private fun SwipePathRecognizer.beginGesture(x: Float, y: Float, strip: RectF = leftStrip) {
        onTouchDown(x, y, strip)
        applyCompoundGestureGate(defaultOptions)
        applyHoverSettings(durationMs = 250L, inwardCompoundEnabled = true)
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
    fun classifyOnUp_leftPanelInwardThenDownWhileOverallStillIn_staysShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(80f, 100f)
        val result = recognizer.classifyOnUp(80f, 140f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
    }

    @Test
    fun classifyOnUp_leftPanelHorizontalThenDownWithoutLeavingInSector_staysShortSwipeIn() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())

        recognizer.onTouchDown(0f, 100f, leftStrip)
        recognizer.onTouchMove(90f, 100f)
        val result = recognizer.classifyOnUp(100f, 135f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_IN, result?.trigger)
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
        recognizer.applyHoverSettings(durationMs = 250L, inwardCompoundEnabled = true)

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
        recognizer.applyHoverSettings(durationMs = 250L, inwardCompoundEnabled = false)

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
        recognizer.applyHoverSettings(durationMs = 250L, inwardCompoundEnabled = true)

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
    fun classifyOnUp_leftPanelInwardThenUpWithoutHover_doesNotReturnInUp() {
        val recognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f)
        recognizer.applyDistances(shortDp = 60f, longDp = 120f)
        recognizer.applyAngles(GestureAngles())
        recognizer.beginGesture(0f, 100f)
        recognizer.onTouchMove(80f, 100f)
        recognizer.onTouchMove(80f, 60f)
        val result = recognizer.classifyOnUp(80f, 55f)

        assertEquals(GestureTriggerType.SHORT_SWIPE_UP_RIGHT, result?.trigger)
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
    fun classifyPartial_inwardHoldWithLConfigured_defersWhileHoldingAtShortZone() {
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

        assertEquals(null, partial?.trigger)
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
}
