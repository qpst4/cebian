package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], instrumentedPackages = ["com.slideindex.app.gesture"])
class GestureSessionThresholdTrackerTest {
    private lateinit var pathRecognizer: SwipePathRecognizer
    private val leftStrip = RectF(0f, 0f, 20f, 2000f)
    private var gestureStartCount = 0
    private var longThresholdCount = 0
    private var cancelLongPressCount = 0
    private lateinit var tracker: GestureSessionThresholdTracker

    @Before
    fun setUp() {
        pathRecognizer = SwipePathRecognizer(PanelSide.LEFT, density = 1f).apply {
            applyDistances(shortDp = 60f, longDp = 120f)
            applyAngles(GestureAngles())
            onTouchDown(0f, 0f, leftStrip)
        }
        gestureStartCount = 0
        longThresholdCount = 0
        cancelLongPressCount = 0
        tracker = GestureSessionThresholdTracker(
            pathRecognizer = pathRecognizer,
            callbacks = object : GestureSession.Callbacks {
                override fun onSessionStart(mode: com.slideindex.app.overlay.OverlayPanelMode) = Unit
                override fun onOpenShellCommandPanel(continuousPick: Boolean) = Unit
                override fun onShellCommandPanelContinuousRelease() = Unit
                override fun onShowAdjustPanel(
                    mode: com.slideindex.app.util.ContinuousAdjustController.Mode,
                    fraction: Float,
                    anchorRawY: Float,
                    deferWindowLayout: Boolean,
                ) = Unit
                override fun onSessionEnd() = Unit
                override fun onRequestInvalidate() = Unit
                override fun hapticGestureStart() {
                    gestureStartCount++
                }
                override fun hapticLongThreshold() {
                    longThresholdCount++
                }
                override fun hapticConfirmLaunch() = Unit
                override fun scheduleDelayed(runnable: Runnable, delayMs: Long) = Unit
                override fun cancelDelayed(runnable: Runnable) = Unit
            },
            cancelLongPressCheck = { cancelLongPressCount++ },
        )
    }

    @Test
    fun trackDistanceHaptics_firesStartOnceWhenCrossingShortThreshold() {
        tracker.trackDistanceHaptics(30f, 0f)
        assertEquals(0, gestureStartCount)
        assertEquals(0, cancelLongPressCount)

        tracker.trackDistanceHaptics(70f, 0f)
        assertEquals(1, gestureStartCount)
        assertEquals(1, cancelLongPressCount)

        tracker.trackDistanceHaptics(80f, 0f)
        assertEquals(1, gestureStartCount)
    }

    @Test
    fun trackDistanceHaptics_firesLongThresholdOnceWhenCrossingLongThreshold() {
        tracker.trackDistanceHaptics(70f, 0f)
        assertEquals(0, longThresholdCount)

        tracker.trackDistanceHaptics(130f, 0f)
        assertEquals(1, longThresholdCount)

        tracker.trackDistanceHaptics(140f, 0f)
        assertEquals(1, longThresholdCount)
    }

    @Test
    fun maybeHapticLongPress_firesOnceWhenLongPressArmed() {
        pathRecognizer.onTouchDown(0f, 0f, leftStrip)
        ShadowSystemClock.advanceBy(SwipePathRecognizer.LONG_PRESS_MS + 50L, TimeUnit.MILLISECONDS)

        tracker.maybeHapticLongPress(0f, 0f)
        assertEquals(1, longThresholdCount)

        tracker.maybeHapticLongPress(0f, 0f)
        assertEquals(1, longThresholdCount)
    }

    @Test
    fun reset_allowsShortThresholdHapticAgain() {
        tracker.trackDistanceHaptics(70f, 0f)
        tracker.reset()
        pathRecognizer.onTouchDown(0f, 0f, leftStrip)

        tracker.trackDistanceHaptics(70f, 0f)

        assertEquals(2, gestureStartCount)
    }
}
