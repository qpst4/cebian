package com.slideindex.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.message.MessageReminderOrchestrator
import com.slideindex.app.overlay.EdgeOverlayHost
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@dagger.hilt.android.AndroidEntryPoint
class SlideIndexAccessibilityService : AccessibilityService() {

    @javax.inject.Inject lateinit var deps: AppDependencies
    @javax.inject.Inject lateinit var messageReminderOrchestrator: MessageReminderOrchestrator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var edgeOverlayHost: EdgeOverlayHost? = null

    private lateinit var otpCoordinator: SlideIndexAccessibilityOtpCoordinator
    private lateinit var foregroundTracker: SlideIndexAccessibilityForegroundTracker
    private lateinit var watchdog: SlideIndexAccessibilityWatchdog
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

    @SuppressLint("SwitchIntDef") // Only handle the event types this service cares about
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> foregroundTracker.handleWindowStateChanged(event)
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> foregroundTracker.handleWindowsChanged()
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> otpCoordinator.maybeAutoFillOtp()
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: SlideIndexAccessibilityService? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        fun dispatchExternalGestureAction(action: GestureAction, anchorRawY: Float): Boolean {
            val service = instance ?: return false
            return service.dispatchExternalGestureAction(action, anchorRawY)
        }

        fun isConnected(): Boolean = instance != null

        fun perform(action: GestureAction): Boolean =
            SlideIndexAccessibilityGestureInjector.perform(action) { instance }

        fun dispatchPointerTap(rawX: Float, rawY: Float, onFinished: (Boolean) -> Unit) =
            SlideIndexAccessibilityGestureInjector.dispatchPointerTap(instance, rawX, rawY, onFinished)

        fun dispatchTap(
            rawX: Float,
            rawY: Float,
            onFinished: (Boolean) -> Unit,
            durationMs: Long = TAP_DURATION_MS,
        ) = SlideIndexAccessibilityGestureInjector.dispatchTap(instance, rawX, rawY, onFinished, durationMs)

        fun dispatchPointerSwipe(
            startX: Float,
            startY: Float,
            config: PointerSwipeConfig,
            onFinished: (Boolean) -> Unit = {},
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerSwipe(instance, startX, startY, config, onFinished)

        fun dispatchPointerSwipePath(
            startX: Float,
            startY: Float,
            path: Path,
            durationMs: Long,
            maxDurationMs: Long = SlideIndexAccessibilityGestureInjector.DEFAULT_SWIPE_MAX_DURATION_MS,
            onFinished: (Boolean) -> Unit = {},
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerSwipePath(
            instance,
            startX,
            startY,
            path,
            durationMs,
            maxDurationMs,
            onFinished,
        )

        fun dispatchPointerHold(
            rawX: Float,
            rawY: Float,
            durationMs: Long,
            onFinished: (Boolean) -> Unit = {},
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerHold(instance, rawX, rawY, durationMs, onFinished)


        fun dispatchTapSync(rawX: Float, rawY: Float): Boolean =
            SlideIndexAccessibilityGestureInjector.dispatchTapSync(instance, rawX, rawY)

        const val TAP_DURATION_MS = SlideIndexAccessibilityGestureInjector.TAP_DURATION_MS
        const val POINTER_TAP_DURATION_MS = SlideIndexAccessibilityGestureInjector.POINTER_TAP_DURATION_MS
        const val POINTER_TAP_CHAIN_GAP_MS = SlideIndexAccessibilityGestureInjector.POINTER_TAP_CHAIN_GAP_MS

        fun reloadApps() {
            instance?.edgeOverlayHost?.reloadApps()
        }

        fun setPreviewMode(
            enabled: Boolean,
            content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
            focus: LayoutPreviewFocus? = null,
        ) {
            instance?.edgeOverlayHost?.setPreviewMode(enabled, content, focus)
        }

        fun recoverOverlaysIfIdle() {
            instance?.edgeOverlayHost?.recoverOverlaysIfIdle()
        }

        fun refreshTriggerVisuals() {
            instance?.edgeOverlayHost?.refreshTriggerVisuals()
        }

        fun overlayHostContext(): Context? = instance

        fun currentForegroundPackage(): String? = instance?.foregroundPackageName()

        fun scheduleOtpAutoFill() {
            instance?.scheduleOtpAutoFillInternal()
        }

        private const val TAG = "SlideIndexA11y"
        private const val SCROLL_BOTTOM_STROKE_COUNT = 10
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        watchdog = SlideIndexAccessibilityWatchdog(this) { edgeOverlayHost }
        otpCoordinator = SlideIndexAccessibilityOtpCoordinator(this, deps)
        foregroundTracker = SlideIndexAccessibilityForegroundTracker(
            service = this,
            overlayHost = { edgeOverlayHost },
            onMaybeOtp = { otpCoordinator.maybeAutoFillOtp() },
            onSyncLockScreen = { watchdog.syncLockScreenState() },
        )
        watchdog.syncLockScreenState()
        edgeOverlayHost = EdgeOverlayHost(this, serviceScope, deps).also { it.start() }
        watchdog.registerScreenLockReceiver()
        otpCoordinator.registerReceiver()
        lastOrientation = resources.configuration.orientation
        Log.i(TAG, "onServiceConnected: edge overlays attached")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newOrientation = newConfig.orientation
        if (lastOrientation != Configuration.ORIENTATION_UNDEFINED &&
            newOrientation != lastOrientation &&
            FloatingPointerOverlayWindow.isShowing
        ) {
            FloatingPointerOverlayWindow.dismiss()
        }
        lastOrientation = newOrientation
        messageReminderOrchestrator.onConfigurationChanged(this, newConfig)
        edgeOverlayHost?.refreshTriggerVisibility()
    }

    fun dispatchExternalGestureAction(action: GestureAction, anchorRawY: Float): Boolean =
        edgeOverlayHost?.dispatchExternalGestureAction(action, anchorRawY) == true

    override fun onDestroy() {
        otpCoordinator.unregisterReceiver()
        watchdog.unregisterScreenLockReceiver()
        edgeOverlayHost?.stop()
        edgeOverlayHost = null
        serviceScope.cancel()
        watchdog.releaseWakeLock()
        instance = null
        super.onDestroy()
    }

    internal fun launchPreviousApp(): Boolean = foregroundTracker.launchPreviousApp()

    internal fun foregroundPackageName(): String? =
        if (::foregroundTracker.isInitialized) foregroundTracker.currPackageName else null

    internal fun scheduleOtpAutoFillInternal() {
        if (::otpCoordinator.isInitialized) otpCoordinator.scheduleAutoFill()
    }

    internal fun toggleKeepScreenOn(): Boolean = watchdog.toggleKeepScreenOn()

    internal fun takeScreenshotDelayed() = watchdog.takeScreenshotDelayed(mainHandler)

    internal fun fastVerticalScroll(toTop: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        val builder = GestureDescription.Builder()
        if (toTop) {
            val path = Path().apply {
                moveTo(centerX, centerY)
                lineTo(centerX, centerY + Int.MAX_VALUE)
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 120))
        } else {
            val strokeCount = SCROLL_BOTTOM_STROKE_COUNT
                .coerceAtMost(GestureDescription.getMaxStrokeCount())
            repeat(strokeCount) { index ->
                val path = Path().apply {
                    moveTo(centerX, centerY)
                    lineTo(centerX, 0f)
                }
                builder.addStroke(
                    GestureDescription.StrokeDescription(path, index * 80L, 12),
                )
            }
        }
        val accepted = dispatchGesture(builder.build(), null, null)
        if (!accepted) {
            Log.w(TAG, "fastVerticalScroll(toTop=$toTop) rejected")
        }
        return accepted
    }
}
