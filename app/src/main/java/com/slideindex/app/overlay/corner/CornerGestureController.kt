package com.slideindex.app.overlay.corner

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.data.AppRepository
import com.slideindex.app.overlay.EdgeSystemGestureExclusionView
import com.slideindex.app.overlay.OverlayPassthrough
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.OverlaySuppression
import com.slideindex.app.util.OverlaySuppressionScope
import kotlinx.coroutines.CoroutineScope

internal class CornerGestureController(
    private val context: Context,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val zoneLayout = CornerZoneLayout()
    private var settings = AppSettings()
    private var density = context.resources.displayMetrics.density

    private data class CaptureSlot(
        val anchor: CornerAnchor,
        val strip: CornerZoneStrip,
        val host: FrameLayout,
        val params: WindowManager.LayoutParams,
    )

    private data class ExclusionSlot(
        val anchor: CornerAnchor,
        val strip: CornerZoneStrip,
        val view: EdgeSystemGestureExclusionView,
        val params: WindowManager.LayoutParams,
    )

    private val captureSlots = mutableListOf<CaptureSlot>()
    private val exclusionSlots = mutableListOf<ExclusionSlot>()
    private var overlayRoot: FrameLayout? = null
    private var overlayView: CornerGestureOverlayView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayAttached = false
    private var capturesAttached = false
    private var expandedCaptureAnchor: CornerAnchor? = null
    private var expandedCaptureStrip: CornerZoneStrip? = null
    private var zonePreviewActive = false
    private var previewRoot: FrameLayout? = null
    private var previewView: CornerZonePreviewView? = null
    private var previewParams: WindowManager.LayoutParams? = null
    private var previewAttached = false

    fun setZonePreviewActive(active: Boolean) {
        zonePreviewActive = active
        if (active) {
            ensurePreviewView()
            syncPreviewWindow()
        } else {
            detachPreview()
        }
    }

    fun applyZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) {
        if (!zonePreviewActive) return
        val corner = settings.cornerGestureSettings.copy(
            verticalEdgeWidthDp = CornerGestureSettings.clampVerticalEdgeWidthDp(verticalEdgeWidthDp),
            verticalEdgeHeightDp = CornerGestureSettings.clampVerticalEdgeHeightDp(verticalEdgeHeightDp),
            horizontalEdgeWidthDp = CornerGestureSettings.clampHorizontalEdgeWidthDp(horizontalEdgeWidthDp),
            horizontalEdgeHeightDp = CornerGestureSettings.clampHorizontalEdgeHeightDp(horizontalEdgeHeightDp),
        )
        syncZoneLayout(corner)
        previewView?.update(zoneLayout, corner, density)
    }

    fun applySettings(settings: AppSettings) {
        this.settings = settings
        this.density = context.resources.displayMetrics.density
        val corner = settings.cornerGestureSettings
        syncZoneLayout(corner)
        if (zonePreviewActive) {
            ensurePreviewView()
            previewView?.update(zoneLayout, corner, density)
            syncPreviewWindow()
        }
        if (!settings.serviceEnabled || !corner.enabled) {
            detachPreview()
            detachGestureLayers()
            return
        }
        if (isCornerWheelSuppressed(settings)) {
            detachPreview()
            detachGestureLayers()
            return
        }
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!corner.isActiveInCurrentOrientation(landscape)) {
            detachCaptures()
            detachOverlay()
            return
        }
        ensureViews()
        syncCaptureWindows()
        if (!overlayView!!.isOverlayVisible()) {
            detachOverlay()
        }
    }

    fun onConfigurationChanged() {
        applySettings(settings)
    }

    fun refreshSuppression() {
        applySettings(settings)
    }

    private fun isCornerWheelSuppressed(settings: AppSettings): Boolean =
        OverlaySuppression.shouldSuppress(
            settings = settings,
            context = context,
            foregroundPackage = OverlayService.foregroundPackage,
            scope = OverlaySuppressionScope.CORNER_WHEEL,
        )

    fun destroy() {
        detachPreview()
        detachGestureLayers()
        overlayView = null
        overlayRoot = null
        captureSlots.clear()
        exclusionSlots.clear()
        previewView = null
        previewRoot = null
    }

    private fun detachGestureLayers() {
        detachOverlay()
        detachCaptures()
        detachExclusions()
    }

    private fun ensurePreviewView() {
        if (previewView == null) {
            previewView = CornerZonePreviewView(context)
            previewRoot = FrameLayout(context).apply {
                addView(
                    previewView,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
            previewParams = OverlayWindowTypes.createPresentationParams(context).apply {
                OverlayWindowTypes.applyFullScreen(this)
            }
        }
        previewView?.update(zoneLayout, settings.cornerGestureSettings, density)
    }

    private fun syncPreviewWindow() {
        if (!zonePreviewActive) {
            detachPreview()
            return
        }
        val corner = settings.cornerGestureSettings
        if (!settings.serviceEnabled || !corner.enabled) {
            detachPreview()
            return
        }
        val root = previewRoot ?: return
        val params = previewParams ?: return
        OverlayWindowTypes.applyFullScreen(params)
        OverlayWindowTypes.applyPresentationPassthroughFlags(params)
        if (!previewAttached) {
            runCatching { windowManager.addView(root, params) }
                .onSuccess { previewAttached = true }
                .onFailure { Log.e(TAG, "Failed to attach corner zone preview", it) }
        } else {
            previewView?.update(zoneLayout, corner, density)
        }
    }

    private fun detachPreview() {
        zonePreviewActive = false
        if (!previewAttached) return
        previewRoot?.let { runCatching { windowManager.removeView(it) } }
        previewAttached = false
    }

    private fun ensureViews() {
        if (overlayView == null) {
            overlayView = CornerGestureOverlayView(
                context = context,
                appRepository = appRepository,
                onSessionEnd = {
                    restoreCaptureSize()
                    detachOverlay()
                },
                onReleaseCapture = ::restoreCaptureSize,
                onShellCommandsPersist = onShellCommandsPersist,
            )
            overlayRoot = FrameLayout(context).apply {
                addView(
                    overlayView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            overlayParams = OverlayWindowTypes.createPresentationParams(context).apply {
                OverlayWindowTypes.applyFullScreen(this)
            }
        }
        ensureCaptureSlots()
        overlayView?.applySettings(settings, density)
    }

    private fun ensureCaptureSlots() {
        CornerAnchor.entries.forEach { anchor ->
            CornerZoneStrip.entries.forEach { strip ->
                if (captureSlots.none { it.anchor == anchor && it.strip == strip }) {
                    val host = FrameLayout(context).apply {
                        addView(
                            CornerTouchCaptureView(context) { event ->
                                handleCaptureTouch(anchor, strip, event)
                            },
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    }
                    captureSlots += CaptureSlot(
                        anchor = anchor,
                        strip = strip,
                        host = host,
                        params = OverlayWindowTypes.createCaptureParams(context),
                    )
                }
            }
        }
    }

    private fun syncCaptureWindows() {
        val corner = settings.cornerGestureSettings
        attachCapturesIfNeeded()
        captureSlots.forEach { slot ->
            val anchorEnabled = when (slot.anchor) {
                CornerAnchor.LEFT -> corner.leftEnabled
                CornerAnchor.RIGHT -> corner.rightEnabled
            }
            val enabled = anchorEnabled &&
                corner.hasActiveTriggerZone() &&
                stripEnabled(corner, slot.strip)
            syncCaptureSlot(slot, enabled)
        }
        syncExclusionWindows(corner)
    }

    private fun syncCaptureSlot(slot: CaptureSlot, enabled: Boolean) {
        if (!enabled) {
            if (slot.host.parent != null) {
                runCatching { windowManager.removeView(slot.host) }
            }
            return
        }
        val params = slot.params
        if (expandedCaptureAnchor == slot.anchor) {
            if (expandedCaptureStrip == slot.strip) {
                applyFullScreenCapture(params)
            } else {
                if (slot.host.parent != null) {
                    runCatching { windowManager.removeView(slot.host) }
                }
                return
            }
        } else {
            val rect = zoneLayout.stripRect(slot.anchor, slot.strip) ?: run {
                if (slot.host.parent != null) {
                    runCatching { windowManager.removeView(slot.host) }
                }
                return
            }
            applyStripCaptureLayout(params, rect)
        }
        if (slot.host.parent == null) {
            runCatching { windowManager.addView(slot.host, params) }
                .onFailure { Log.e(TAG, "Failed to attach corner capture", it) }
        } else {
            runCatching { windowManager.updateViewLayout(slot.host, params) }
                .onFailure { Log.e(TAG, "Failed to update corner capture", it) }
        }
    }

    private fun expandCaptureForSession(anchor: CornerAnchor, strip: CornerZoneStrip) {
        expandedCaptureAnchor = anchor
        expandedCaptureStrip = strip
        syncCaptureWindows()
    }

    private fun restoreCaptureSize() {
        if (expandedCaptureAnchor == null) return
        expandedCaptureAnchor = null
        expandedCaptureStrip = null
        syncCaptureWindows()
    }

    private fun applyStripCaptureLayout(params: WindowManager.LayoutParams, rect: RectF) {
        params.width = rect.width().toInt().coerceAtLeast(1)
        params.height = rect.height().toInt().coerceAtLeast(1)
        params.x = rect.left.toInt()
        params.y = rect.top.toInt()
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun applyFullScreenCapture(params: WindowManager.LayoutParams) {
        val (screenW, screenH) = OverlayScreenMetrics.sizePx(context)
        params.width = screenW
        params.height = screenH
        params.x = 0
        params.y = 0
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun syncZoneLayout(corner: CornerGestureSettings) {
        val (screenW, screenH) = OverlayScreenMetrics.sizePx(context)
        zoneLayout.update(screenW, screenH, density, corner)
    }

    private fun stripEnabled(corner: CornerGestureSettings, strip: CornerZoneStrip): Boolean =
        when (strip) {
            CornerZoneStrip.VERTICAL ->
                corner.verticalEdgeWidthDp > 0f && corner.verticalEdgeHeightDp > 0f
            CornerZoneStrip.HORIZONTAL ->
                corner.horizontalEdgeWidthDp > 0f && corner.horizontalEdgeHeightDp > 0f
        }

    private fun attachCapturesIfNeeded() {
        capturesAttached = true
    }

    private fun detachCaptures() {
        captureSlots.forEach { slot ->
            runCatching { windowManager.removeView(slot.host) }
        }
        detachExclusions()
        capturesAttached = false
    }

    private fun ensureExclusionSlots() {
        CornerAnchor.entries.forEach { anchor ->
            CornerZoneStrip.entries.forEach { strip ->
                if (exclusionSlots.none { it.anchor == anchor && it.strip == strip }) {
                    exclusionSlots += ExclusionSlot(
                        anchor = anchor,
                        strip = strip,
                        view = EdgeSystemGestureExclusionView(context),
                        params = OverlayWindowTypes.createCaptureParams(context).apply {
                            OverlayWindowTypes.applyExclusionPassthroughFlags(this)
                        },
                    )
                }
            }
        }
    }

    private fun syncExclusionWindows(corner: CornerGestureSettings) {
        if (!corner.overrideSystemNav) {
            detachExclusions()
            return
        }
        ensureExclusionSlots()
        exclusionSlots.forEach { slot ->
            val anchorEnabled = when (slot.anchor) {
                CornerAnchor.LEFT -> corner.leftEnabled
                CornerAnchor.RIGHT -> corner.rightEnabled
            }
            val enabled = anchorEnabled &&
                corner.hasActiveTriggerZone() &&
                stripEnabled(corner, slot.strip)
            syncExclusionSlot(slot, enabled)
        }
    }

    private fun syncExclusionSlot(slot: ExclusionSlot, enabled: Boolean) {
        if (!enabled) {
            if (slot.view.parent != null) {
                runCatching { windowManager.removeView(slot.view) }
            }
            return
        }
        val rect = zoneLayout.stripRect(slot.anchor, slot.strip) ?: run {
            if (slot.view.parent != null) {
                runCatching { windowManager.removeView(slot.view) }
            }
            return
        }
        applyStripCaptureLayout(slot.params, rect)
        if (slot.view.parent == null) {
            runCatching { windowManager.addView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to attach corner exclusion", it) }
        } else {
            runCatching { windowManager.updateViewLayout(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to update corner exclusion", it) }
        }
    }

    private fun detachExclusions() {
        exclusionSlots.forEach { slot ->
            runCatching { windowManager.removeView(slot.view) }
        }
    }

    private fun ensureOverlayAttached() {
        if (overlayAttached) return
        val root = overlayRoot ?: return
        val params = overlayParams ?: return
        val view = overlayView ?: return
        OverlayWindowTypes.applyFullScreen(params)
        OverlayWindowTypes.applyPresentationInteractiveFlags(params)
        runCatching { windowManager.addView(root, params) }
            .onSuccess {
                overlayAttached = true
                view.applySettings(settings, density)
            }
            .onFailure { Log.e(TAG, "Failed to attach corner overlay", it) }
    }

    private fun detachOverlay() {
        if (!overlayAttached) return
        overlayView?.cancelSession()
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        overlayAttached = false
    }

    private fun handleCaptureTouch(
        anchor: CornerAnchor,
        strip: CornerZoneStrip,
        event: MotionEvent,
    ): Boolean {
        if (!settings.cornerGestureSettings.enabled) return false
        val view = overlayView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (view.isWheelPinned()) {
                    ensureOverlayAttached()
                    return view.handlePinnedTouchEvent(event)
                }
                if (!zoneLayout.contains(anchor, event.rawX, event.rawY)) return false
                ensureOverlayAttached()
                expandCaptureForSession(anchor, strip)
                view.beginSession(anchor, event)
                return view.handleTouch(event)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (view.isWheelPinned()) {
                    ensureOverlayAttached()
                    return view.handlePinnedTouchEvent(event)
                }
                if (!view.isSessionActive()) return false
                val passthrough = event.actionMasked == MotionEvent.ACTION_UP &&
                    view.shouldPassthroughTap()
                val consumed = view.handleTouch(event)
                if (passthrough) {
                    performTapPassthrough(event.rawX, event.rawY)
                }
                return consumed
            }
        }
        return false
    }

    private fun performTapPassthrough(rawX: Float, rawY: Float) {
        OverlayPassthrough.run(
            hideTriggers = {
                restoreCaptureSize()
                detachOverlay()
                detachCaptures()
                detachExclusions()
            },
            showTriggers = { syncCaptureWindows() },
            rawX = rawX,
            rawY = rawY,
            onComplete = {},
        )
    }

    companion object {
        private const val TAG = "CornerGestureController"
    }
}
