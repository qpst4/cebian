package com.slideindex.app.overlay

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.BrightnessControlHelper
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.VolumeControlHelper

internal class AdjustPanelOverlayController(
    private val host: Host,
) {
    interface Host {
        val context: Context
        fun side(): PanelSide
        fun settings(): AppSettings
        fun actionExecutor(): ActionExecutor
        fun gestureSession(): GestureSession
        fun viewWidth(): Int
        fun viewHeight(): Int
        fun density(): Float
        fun screenWidthPx(): Int
        fun screenHeightPx(): Int
        fun viewLocationOnScreen(): IntArray
        fun anchorLocalY(rawY: Float): Float
        fun dp(value: Float): Float
        fun invalidate()
        fun post(action: () -> Unit)
        fun runAfterLayout(block: () -> Unit)
        fun hapticConfirmLaunch()
        fun onAdjustPanelDismiss()
        fun onSessionStart()
        fun notifyOverlayLayoutIfNeeded()
        fun notifyPresentationTouchRequirementChanged()
    }

    private data class AdjustIndicatorVisual(
        val mode: ContinuousAdjustController.Mode,
        val fraction: Float,
        val anchorRawY: Float,
    )

    var adjustPanelState: AdjustPanelState? = null
        private set

    private var adjustPanelDismissing = false
    private var wasAdjustMode = false
    private var adjustIndicatorReceding = false
    private var adjustPanelExpandedForGesture = false
    private var adjustPanelEntering = false
    private var adjustIndicatorProgress = 0f
    private var adjustIndicatorAnimator: ValueAnimator? = null
    private var adjustIndicatorLayout: AdjustLevelIndicator.Layout? = null
    private var adjustIndicatorHoldVisual: AdjustIndicatorVisual? = null
    private var adjustIndicatorFrozenLayout: AdjustLevelIndicator.Layout? = null
    private var volumeDragAnchorRawY = 0f
    private var volumeDragBaseline = 0f
    private var volumeChangeReceiver: BroadcastReceiver? = null
    private var brightnessSettingsObserver: ContentObserver? = null
    private val brightnessSettingsHandler = Handler(Looper.getMainLooper())

    fun hasAdjustPanel(): Boolean = adjustPanelState != null

    fun isDismissing(): Boolean = adjustPanelDismissing

    fun onSizeChanged() {
        adjustPanelState?.let { state ->
            if (!adjustPanelEntering) {
                updateAdjustIndicatorLayout(state.anchorRawY)
                host.invalidate()
            }
        }
    }

    fun onDetachedFromWindow() {
        stopAdjustPanelLevelSync()
    }

    fun forceRecover() {
        if (adjustPanelDismissing) return
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorProgress = 0f
        adjustPanelDismissing = false
        adjustPanelExpandedForGesture = false
        adjustPanelEntering = false
        clearAdjustIndicatorExitState()
        if (adjustPanelState != null) {
            stopAdjustPanelLevelSync()
            adjustPanelState = null
            host.onAdjustPanelDismiss()
        }
    }

    fun onSessionEnd() {
        adjustPanelState?.let {
            adjustIndicatorReceding = false
        }
        if (adjustPanelState == null) {
            adjustIndicatorAnimator?.cancel()
            adjustIndicatorProgress = 0f
            wasAdjustMode = false
            clearAdjustIndicatorExitState()
        }
    }

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (adjustPanelDismissing) return false
        val state = adjustPanelState ?: return false
        val density = host.density()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val layout = adjustIndicatorLayout ?: run {
                    dismissAdjustPanel()
                    return true
                }
                when (state.mode) {
                    ContinuousAdjustController.Mode.VOLUME -> when (
                        AdjustLevelIndicator.hitVolumeTarget(layout, host.side(), localX, localY, density)
                    ) {
                        VolumeHitTarget.DND -> {
                            if (!VolumeControlHelper.hasAccess(host.context)) {
                                PermissionHelper.requestNotificationPolicyAccess(host.context)
                            } else {
                                host.actionExecutor().toggleDnd()?.let { state.interruptionFilter = it }
                                host.hapticConfirmLaunch()
                            }
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.RINGER -> {
                            if (!VolumeControlHelper.hasAccess(host.context)) {
                                PermissionHelper.requestNotificationPolicyAccess(host.context)
                            } else {
                                host.actionExecutor().cycleRingerMode()?.let { state.ringerMode = it }
                                host.hapticConfirmLaunch()
                            }
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.EXPAND -> {
                            state.volumeExpanded = !state.volumeExpanded
                            if (state.volumeExpanded) {
                                refreshVolumePanelLevels(state)
                            }
                            updateAdjustIndicatorLayout(state.anchorRawY)
                            host.hapticConfirmLaunch()
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.MEDIA -> {
                            beginMainAdjustDrag(state, event.rawY)
                            return true
                        }
                        VolumeHitTarget.RING -> {
                            if (!state.volumeExpanded) return false
                            beginVolumeStreamDrag(state, VolumeDragTarget.RING, event.rawY)
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.NOTIFICATION -> {
                            if (!state.volumeExpanded) return false
                            beginVolumeStreamDrag(state, VolumeDragTarget.NOTIFICATION, event.rawY)
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.NONE -> {
                            dismissAdjustPanel()
                            return true
                        }
                    }
                    ContinuousAdjustController.Mode.BRIGHTNESS -> when (
                        AdjustLevelIndicator.hitBrightnessTarget(layout, host.side(), localX, localY, density)
                    ) {
                        BrightnessHitTarget.AUTO_BRIGHTNESS -> {
                            if (!BrightnessControlHelper.hasAccess(host.context)) {
                                PermissionHelper.requestWriteSettingsAccess(host.context)
                            } else {
                                host.actionExecutor().toggleAutoBrightness()?.let {
                                    state.autoBrightnessEnabled = it
                                    host.hapticConfirmLaunch()
                                }
                            }
                            host.invalidate()
                            return true
                        }
                        BrightnessHitTarget.DARK_MODE -> {
                            if (!BrightnessControlHelper.hasDarkModeAccess(host.context)) {
                                PermissionHelper.requestWriteSettingsAccess(host.context)
                            } else {
                                host.actionExecutor().toggleDarkMode()?.let {
                                    state.darkModeEnabled = it
                                    host.hapticConfirmLaunch()
                                }
                            }
                            host.invalidate()
                            return true
                        }
                        BrightnessHitTarget.BRIGHTNESS -> {
                            beginMainAdjustDrag(state, event.rawY)
                            return true
                        }
                        BrightnessHitTarget.NONE -> {
                            dismissAdjustPanel()
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state.dragTarget == null) return false
                when (state.dragTarget) {
                    VolumeDragTarget.MEDIA -> {
                        host.actionExecutor().updateContinuousAdjust(state.mode, event.rawY)
                        state.fraction = host.actionExecutor().adjustFraction()
                    }
                    VolumeDragTarget.RING, VolumeDragTarget.NOTIFICATION -> {
                        updateVolumeStreamDrag(state, event.rawY)
                    }
                    null -> return false
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state.dragTarget == null) return false
                when (state.dragTarget) {
                    VolumeDragTarget.MEDIA -> {
                        host.actionExecutor().endContinuousAdjust()
                        state.fraction = host.actionExecutor().readCurrentAdjustFraction(state.mode)
                    }
                    VolumeDragTarget.RING, VolumeDragTarget.NOTIFICATION -> Unit
                    null -> Unit
                }
                state.dragTarget = null
                state.dragging = false
                host.invalidate()
                return true
            }
        }
        return false
    }

    fun showAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
    ) {
        if (mode == ContinuousAdjustController.Mode.BRIGHTNESS) {
            host.actionExecutor().clearBrightnessPreview()
        }
        val executor = host.actionExecutor()
        adjustPanelState = if (mode == ContinuousAdjustController.Mode.VOLUME) {
            AdjustPanelState(
                mode = mode,
                fraction = fraction,
                anchorRawY = anchorRawY,
                ringFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.RING),
                notificationFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION),
                ringerMode = executor.readRingerMode(),
                interruptionFilter = executor.readInterruptionFilter(),
            )
        } else {
            AdjustPanelState(
                mode = mode,
                fraction = fraction,
                anchorRawY = anchorRawY,
                autoBrightnessEnabled = executor.readAutoBrightnessEnabled(),
                darkModeEnabled = executor.readDarkModeEnabled(),
            )
        }
        adjustPanelDismissing = false
        adjustIndicatorAnimator?.cancel()
        val fromContinuousGesture = host.gestureSession().isAdjustMode()
        adjustPanelExpandedForGesture = true
        host.onSessionStart()
        host.notifyPresentationTouchRequirementChanged()
        if (fromContinuousGesture && adjustIndicatorProgress >= 1f) {
            wasAdjustMode = true
            adjustPanelEntering = false
            adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY, forceFullScreenAnchor = true, mode = mode)
            host.invalidate()
        } else {
            host.runAfterLayout {
                if (adjustPanelState == null) return@runAfterLayout
                if (fromContinuousGesture && adjustIndicatorProgress > 0f) {
                    continueAdjustPanelEnterAnimation(anchorRawY)
                } else {
                    beginAdjustPanelEnterAnimation(anchorRawY)
                }
            }
        }
        startAdjustPanelLevelSync(mode)
        host.notifyPresentationTouchRequirementChanged()
    }

    fun dismissAdjustPanel(animated: Boolean = true) {
        if (adjustPanelState == null || adjustPanelDismissing) return
        stopAdjustPanelLevelSync()
        adjustIndicatorHoldVisual = captureAdjustIndicatorVisual()
        freezeAdjustIndicatorLayout(
            adjustIndicatorHoldVisual?.anchorRawY,
            adjustIndicatorHoldVisual?.mode,
        )
        if (!animated || adjustIndicatorProgress <= 0f) {
            finishDismissAdjustPanel()
            return
        }
        adjustPanelDismissing = true
        animateAdjustIndicatorTo(
            target = 0f,
            durationMs = ADJUST_INDICATOR_EXIT_MS,
            interpolator = AccelerateInterpolator(),
        ) {
            finishDismissAdjustPanel()
        }
    }

    fun drawVisibleIndicator(canvas: Canvas) {
        if (adjustIndicatorProgress <= 0f) return
        val visual = captureAdjustIndicatorVisual() ?: return
        adjustIndicatorHoldVisual = visual
        drawAdjustIndicator(
            canvas = canvas,
            mode = visual.mode,
            fraction = visual.fraction,
            anchorRawY = visual.anchorRawY,
        )
    }

    fun syncAdjustIndicatorAnimation() {
        val active = host.gestureSession().isAdjustMode() || adjustPanelState != null
        if (active == wasAdjustMode) return
        wasAdjustMode = active
        if (active) {
            if (adjustPanelState != null) {
                wasAdjustMode = true
                return
            }
            startAdjustIndicatorEnterAnimationIfNeeded()
        } else if (adjustPanelState == null && adjustIndicatorProgress > 0f) {
            adjustIndicatorHoldVisual = captureAdjustIndicatorVisual() ?: adjustIndicatorHoldVisual
            animateAdjustIndicatorTo(
                target = 0f,
                durationMs = ADJUST_INDICATOR_EXIT_MS,
                interpolator = AccelerateInterpolator(),
            ) {
                clearAdjustIndicatorExitState()
                adjustIndicatorProgress = 0f
                host.invalidate()
                host.notifyOverlayLayoutIfNeeded()
            }
        }
    }

    fun onSessionStartAdjustMode() {
        wasAdjustMode = true
        startAdjustIndicatorEnterAnimationIfNeeded()
    }

    private fun beginMainAdjustDrag(state: AdjustPanelState, rawY: Float) {
        state.dragTarget = VolumeDragTarget.MEDIA
        state.dragging = true
        host.actionExecutor().beginContinuousAdjust(state.mode, rawY)
        host.actionExecutor().updateContinuousAdjust(state.mode, rawY)
        host.invalidate()
    }

    private fun beginVolumeStreamDrag(state: AdjustPanelState, target: VolumeDragTarget, rawY: Float) {
        state.dragTarget = target
        state.dragging = true
        volumeDragAnchorRawY = rawY
        volumeDragBaseline = when (target) {
            VolumeDragTarget.RING -> state.ringFraction
            VolumeDragTarget.NOTIFICATION -> state.notificationFraction
            VolumeDragTarget.MEDIA -> state.fraction
        }
    }

    private fun updateVolumeStreamDrag(state: AdjustPanelState, rawY: Float) {
        val span = host.screenHeightPx().coerceAtLeast(1) * VOLUME_DRAG_SPAN_SCREEN_FRACTION
        val fraction = ((volumeDragBaseline + (volumeDragAnchorRawY - rawY) / span)).coerceIn(0f, 1f)
        when (state.dragTarget) {
            VolumeDragTarget.RING -> {
                state.ringFraction = fraction
                host.actionExecutor().setVolumeFraction(VolumeControlHelper.Stream.RING, fraction)
            }
            VolumeDragTarget.NOTIFICATION -> {
                state.notificationFraction = fraction
                host.actionExecutor().setVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION, fraction)
            }
            VolumeDragTarget.MEDIA, null -> Unit
        }
    }

    private fun refreshVolumePanelLevels(state: AdjustPanelState) {
        val executor = host.actionExecutor()
        state.fraction = executor.readCurrentAdjustFraction(ContinuousAdjustController.Mode.VOLUME)
        state.ringFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.RING)
        state.notificationFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION)
        state.ringerMode = executor.readRingerMode()
        state.interruptionFilter = executor.readInterruptionFilter()
    }

    private fun finishDismissAdjustPanel() {
        adjustPanelDismissing = false
        adjustPanelState = null
        adjustPanelExpandedForGesture = false
        adjustPanelEntering = false
        adjustIndicatorLayout = null
        adjustIndicatorFrozenLayout = null
        adjustIndicatorHoldVisual = null
        adjustIndicatorReceding = false
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorProgress = 0f
        wasAdjustMode = false
        if (host.gestureSession().panelMode() == OverlayPanelMode.NONE &&
            !host.gestureSession().isAdjustMode()
        ) {
            host.gestureSession().endSession()
        }
        host.onAdjustPanelDismiss()
        host.invalidate()
    }

    private fun freezeAdjustIndicatorLayout(anchorRawY: Float?, mode: ContinuousAdjustController.Mode? = null) {
        if (adjustIndicatorFrozenLayout != null) return
        anchorRawY?.let { updateAdjustIndicatorLayout(it, mode = mode) }
        adjustIndicatorFrozenLayout = adjustIndicatorLayout
    }

    private fun clearAdjustIndicatorExitState() {
        adjustIndicatorHoldVisual = null
        adjustIndicatorLayout = null
        adjustIndicatorFrozenLayout = null
        adjustIndicatorReceding = false
    }

    private fun continueAdjustPanelEnterAnimation(anchorRawY: Float) {
        adjustPanelEntering = true
        wasAdjustMode = true
        adjustIndicatorReceding = false
        updateAdjustIndicatorLayout(
            anchorRawY,
            forceFullScreenAnchor = true,
            mode = adjustPanelState?.mode,
        )
        adjustIndicatorFrozenLayout = adjustIndicatorLayout
        val remaining = (1f - adjustIndicatorProgress).coerceIn(0f, 1f)
        val durationMs = (ADJUST_INDICATOR_ENTER_MS * remaining).toLong().coerceAtLeast(1L)
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = durationMs,
            interpolator = DecelerateInterpolator(),
        ) {
            adjustPanelEntering = false
            adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY, mode = adjustPanelState?.mode)
            host.invalidate()
        }
    }

    private fun beginAdjustPanelEnterAnimation(anchorRawY: Float) {
        adjustPanelEntering = true
        wasAdjustMode = true
        adjustIndicatorAnimator?.cancel()
        adjustIndicatorReceding = false
        adjustIndicatorProgress = 0f
        adjustIndicatorFrozenLayout = null
        updateAdjustIndicatorLayout(
            anchorRawY,
            forceFullScreenAnchor = true,
            mode = adjustPanelState?.mode,
        )
        adjustIndicatorFrozenLayout = adjustIndicatorLayout
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = ADJUST_INDICATOR_ENTER_MS,
            interpolator = DecelerateInterpolator(),
        ) {
            adjustPanelEntering = false
            adjustIndicatorFrozenLayout = null
            updateAdjustIndicatorLayout(anchorRawY, mode = adjustPanelState?.mode)
            host.invalidate()
        }
    }

    private fun startAdjustPanelLevelSync(mode: ContinuousAdjustController.Mode) {
        when (mode) {
            ContinuousAdjustController.Mode.VOLUME -> startVolumeLevelSync()
            ContinuousAdjustController.Mode.BRIGHTNESS -> startBrightnessSettingsSync()
        }
    }

    private fun stopAdjustPanelLevelSync() {
        stopVolumeLevelSync()
        stopBrightnessSettingsSync()
    }

    private fun startVolumeLevelSync() {
        if (volumeChangeReceiver != null) return
        volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    VOLUME_CHANGED_ACTION -> {
                        val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                        if (
                            streamType == AudioManager.STREAM_MUSIC ||
                            streamType == AudioManager.STREAM_RING ||
                            streamType == AudioManager.STREAM_NOTIFICATION
                        ) {
                            syncAdjustPanelVolumeFromSystem()
                        }
                    }
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> syncAdjustPanelVolumeFromSystem()
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED ->
                        syncAdjustPanelVolumeFromSystem()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            host.context.registerReceiver(volumeChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            host.context.registerReceiver(volumeChangeReceiver, filter)
        }
    }

    private fun stopVolumeLevelSync() {
        volumeChangeReceiver?.let { receiver ->
            runCatching { host.context.unregisterReceiver(receiver) }
        }
        volumeChangeReceiver = null
    }

    private fun startBrightnessSettingsSync() {
        if (brightnessSettingsObserver != null) return
        val observer = object : ContentObserver(brightnessSettingsHandler) {
            override fun onChange(selfChange: Boolean) {
                syncAdjustPanelLevelFromSystem(ContinuousAdjustController.Mode.BRIGHTNESS)
            }
        }
        brightnessSettingsObserver = observer
        val resolver = host.context.contentResolver
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(BrightnessControlHelper.UI_NIGHT_MODE_KEY),
            false,
            observer,
        )
    }

    private fun stopBrightnessSettingsSync() {
        brightnessSettingsObserver?.let { observer ->
            runCatching { host.context.contentResolver.unregisterContentObserver(observer) }
        }
        brightnessSettingsObserver = null
    }

    private fun syncAdjustPanelVolumeFromSystem() {
        syncAdjustPanelLevelFromSystem(ContinuousAdjustController.Mode.VOLUME)
    }

    private fun syncAdjustPanelLevelFromSystem(mode: ContinuousAdjustController.Mode) {
        val state = adjustPanelState ?: return
        if (state.mode != mode) return
        val executor = host.actionExecutor()
        if (mode == ContinuousAdjustController.Mode.VOLUME) {
            if (state.dragTarget != VolumeDragTarget.MEDIA) {
                val mediaFraction = executor.readCurrentAdjustFraction(mode)
                if (kotlin.math.abs(state.fraction - mediaFraction) >= LEVEL_SYNC_EPSILON) {
                    state.fraction = mediaFraction
                }
            }
            if (state.dragTarget != VolumeDragTarget.RING) {
                val ringFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.RING)
                if (kotlin.math.abs(state.ringFraction - ringFraction) >= LEVEL_SYNC_EPSILON) {
                    state.ringFraction = ringFraction
                }
            }
            if (state.dragTarget != VolumeDragTarget.NOTIFICATION) {
                val notificationFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION)
                if (kotlin.math.abs(state.notificationFraction - notificationFraction) >= LEVEL_SYNC_EPSILON) {
                    state.notificationFraction = notificationFraction
                }
            }
            state.ringerMode = executor.readRingerMode()
            val interruptionFilter = executor.readInterruptionFilter()
            if (state.interruptionFilter != interruptionFilter) {
                state.interruptionFilter = interruptionFilter
            }
            host.invalidate()
            return
        }
        if (state.dragTarget != null) return
        val fraction = executor.readCurrentAdjustFraction(mode)
        if (kotlin.math.abs(state.fraction - fraction) < LEVEL_SYNC_EPSILON) {
            syncAdjustPanelBrightnessFlags(state)
            return
        }
        state.fraction = fraction
        syncAdjustPanelBrightnessFlags(state)
        host.invalidate()
    }

    private fun syncAdjustPanelBrightnessFlags(state: AdjustPanelState) {
        val executor = host.actionExecutor()
        val autoBrightnessEnabled = executor.readAutoBrightnessEnabled()
        val darkModeEnabled = executor.readDarkModeEnabled()
        var changed = false
        if (state.autoBrightnessEnabled != autoBrightnessEnabled) {
            state.autoBrightnessEnabled = autoBrightnessEnabled
            changed = true
        }
        if (state.darkModeEnabled != darkModeEnabled) {
            state.darkModeEnabled = darkModeEnabled
            changed = true
        }
        if (changed) host.invalidate()
    }

    private fun updateAdjustIndicatorLayout(
        anchorRawY: Float,
        forceFullScreenAnchor: Boolean = false,
        mode: ContinuousAdjustController.Mode? = null,
    ): AdjustLevelIndicator.Layout? {
        val density = host.density()
        val screenWidthPx = host.screenWidthPx()
        val loc = host.viewLocationOnScreen()
        val anchorLocalY = host.anchorLocalY(anchorRawY)
        val adjustMode = mode
            ?: adjustPanelState?.mode
            ?: host.gestureSession().adjustModeOrNull()
        adjustIndicatorLayout = AdjustLevelIndicator.layout(
            viewWidth = if (forceFullScreenAnchor) screenWidthPx else host.viewWidth().coerceAtLeast(1),
            viewHeight = host.viewHeight().coerceAtLeast(host.screenHeightPx()),
            side = host.side(),
            anchorY = anchorLocalY,
            density = density,
            viewScreenX = if (forceFullScreenAnchor) 0 else loc[0],
            screenWidthPx = screenWidthPx,
            chrome = when (adjustMode) {
                ContinuousAdjustController.Mode.VOLUME -> AdjustPanelChrome.VOLUME
                ContinuousAdjustController.Mode.BRIGHTNESS -> AdjustPanelChrome.BRIGHTNESS
                null -> AdjustPanelChrome.NONE
            },
            volumeExpanded = adjustPanelState?.volumeExpanded == true &&
                adjustMode == ContinuousAdjustController.Mode.VOLUME,
        )
        return adjustIndicatorLayout
    }

    private fun captureAdjustIndicatorVisual(): AdjustIndicatorVisual? {
        adjustPanelState?.let { state ->
            val fraction = when (state.dragTarget) {
                VolumeDragTarget.MEDIA -> host.actionExecutor().adjustFraction()
                else -> state.fraction
            }
            return AdjustIndicatorVisual(state.mode, fraction, state.anchorRawY)
        }
        host.gestureSession().adjustModeOrNull()?.let { mode ->
            return AdjustIndicatorVisual(
                mode = mode,
                fraction = host.actionExecutor().adjustFraction(),
                anchorRawY = host.gestureSession().adjustAnchorRawY(),
            )
        }
        return adjustIndicatorHoldVisual
    }

    private fun animateAdjustIndicatorTo(
        target: Float,
        durationMs: Long,
        interpolator: Interpolator = DecelerateInterpolator(),
        onEnd: (() -> Unit)? = null,
    ) {
        adjustIndicatorAnimator?.cancel()
        val receding = target == 0f && adjustIndicatorProgress > 0f
        if (receding) {
            adjustIndicatorReceding = true
            freezeAdjustIndicatorLayout(
                adjustIndicatorHoldVisual?.anchorRawY ?: adjustPanelState?.anchorRawY
                    ?: host.gestureSession().adjustAnchorRawY(),
                adjustIndicatorHoldVisual?.mode
                    ?: adjustPanelState?.mode
                    ?: host.gestureSession().adjustModeOrNull(),
            )
        } else if (target >= 1f) {
            adjustIndicatorReceding = false
            if (!adjustPanelEntering) {
                adjustIndicatorFrozenLayout = null
            }
        }
        if (durationMs <= 0L || adjustIndicatorProgress == target) {
            adjustIndicatorProgress = target
            if (target >= 1f) {
                adjustIndicatorReceding = false
                if (!adjustPanelEntering) {
                    adjustIndicatorFrozenLayout = null
                }
            }
            host.invalidate()
            onEnd?.invoke()
            return
        }
        adjustIndicatorAnimator = ValueAnimator.ofFloat(adjustIndicatorProgress, target).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { animator ->
                adjustIndicatorProgress = animator.animatedValue as Float
                host.invalidate()
            }
            if (onEnd != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onEnd()
                    }
                })
            }
            start()
        }
    }

    private fun resolveBrightnessPanelVisual(): BrightnessPanelVisual? {
        adjustPanelState?.takeIf { it.mode == ContinuousAdjustController.Mode.BRIGHTNESS }?.let { state ->
            return BrightnessPanelVisual(
                autoBrightnessEnabled = state.autoBrightnessEnabled,
                darkModeEnabled = state.darkModeEnabled,
            )
        }
        if (host.gestureSession().adjustModeOrNull() != ContinuousAdjustController.Mode.BRIGHTNESS) return null
        val executor = host.actionExecutor()
        return BrightnessPanelVisual(
            autoBrightnessEnabled = executor.readAutoBrightnessEnabled(),
            darkModeEnabled = executor.readDarkModeEnabled(),
        )
    }

    private fun resolveVolumePanelVisual(): VolumePanelVisual? {
        adjustPanelState?.takeIf { it.mode == ContinuousAdjustController.Mode.VOLUME }?.let { state ->
            return VolumePanelVisual(
                expanded = state.volumeExpanded,
                ringFraction = state.ringFraction,
                notificationFraction = state.notificationFraction,
                ringerMode = state.ringerMode,
                interruptionFilter = state.interruptionFilter,
            )
        }
        if (host.gestureSession().adjustModeOrNull() != ContinuousAdjustController.Mode.VOLUME) return null
        val executor = host.actionExecutor()
        return VolumePanelVisual(
            expanded = false,
            ringFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.RING),
            notificationFraction = executor.readVolumeFraction(VolumeControlHelper.Stream.NOTIFICATION),
            ringerMode = executor.readRingerMode(),
            interruptionFilter = executor.readInterruptionFilter(),
        )
    }

    private fun drawAdjustIndicator(
        canvas: Canvas,
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
    ) {
        val layout = if (adjustIndicatorReceding || adjustPanelEntering) {
            adjustIndicatorFrozenLayout ?: run {
                freezeAdjustIndicatorLayout(anchorRawY, mode)
                adjustIndicatorFrozenLayout
            }
        } else {
            adjustIndicatorFrozenLayout = null
            val panelVisible = adjustPanelState != null && !adjustPanelDismissing
            if (panelVisible && adjustIndicatorLayout != null) {
                adjustIndicatorLayout
            } else {
                updateAdjustIndicatorLayout(anchorRawY, mode = mode)
                adjustIndicatorLayout
            }
        } ?: return
        val volumePanel = resolveVolumePanelVisual()
        val brightnessPanel = resolveBrightnessPanelVisual()
        AdjustLevelIndicator.draw(
            canvas = canvas,
            layout = layout,
            mode = mode,
            fraction = fraction,
            enterProgress = adjustIndicatorProgress,
            density = host.density(),
            side = host.side(),
            recede = adjustIndicatorReceding,
            volumePanel = volumePanel,
            brightnessPanel = brightnessPanel,
            context = host.context,
        )
    }

    private fun startAdjustIndicatorEnterAnimationIfNeeded() {
        if (adjustPanelState != null) return
        if (adjustIndicatorProgress >= 1f) return
        if (adjustIndicatorAnimator?.isRunning == true) return
        animateAdjustIndicatorTo(
            target = 1f,
            durationMs = ADJUST_INDICATOR_ENTER_MS,
            interpolator = DecelerateInterpolator(),
        )
    }

    companion object {
        private const val ADJUST_INDICATOR_ENTER_MS = 220L
        private const val ADJUST_INDICATOR_EXIT_MS = 160L
        private const val LEVEL_SYNC_EPSILON = 0.002f
        private const val VOLUME_DRAG_SPAN_SCREEN_FRACTION = 0.5f
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
