package com.slideindex.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.copy.UniversalCopyOverlay
import com.slideindex.app.backtap.BackTapGestureHost
import com.slideindex.app.translate.overlay.ScreenTranslationController
import com.slideindex.app.screensearch.ScreenSearchFloating
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.clipboard.monitor.ClipboardMonitorStartup
import com.slideindex.app.clipboardfloat.ClipboardFloatImeCoordinator
import com.slideindex.app.overlay.KeyboardTriggerImeCoordinator
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.message.MessageReminderOrchestrator
import com.slideindex.app.overlay.EdgeOverlayHost
import com.slideindex.app.overlay.FloatBallOcrRegions
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.FloatBallTextPickCoordinator
import com.slideindex.app.overlay.FloatBallPickResult
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.corner.CornerAnchor
import com.slideindex.app.overlay.corner.CornerGestureHost
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.otp.OtpAutoFillController
import com.slideindex.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@dagger.hilt.android.AndroidEntryPoint
class SlideIndexAccessibilityService : AccessibilityService() {

    @javax.inject.Inject lateinit var deps: AppDependencies
    @javax.inject.Inject lateinit var messageReminderOrchestrator: MessageReminderOrchestrator
    @javax.inject.Inject lateinit var backTapGestureHost: BackTapGestureHost

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var edgeOverlayHost: EdgeOverlayHost? = null

    /** 当前由输入层接管并转发过来的目标（SIDE_* / TARGET_*），用于会话结束时精确取消。 */
    private var activeForwardedTarget: Int = NO_FORWARDED_TARGET

    private lateinit var foregroundTracker: SlideIndexAccessibilityForegroundTracker
    private lateinit var watchdog: SlideIndexAccessibilityWatchdog
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

    @SuppressLint("SwitchIntDef") // Only handle the event types this service cares about
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                foregroundTracker.handleWindowStateChanged(event)
                ClipboardFloatImeCoordinator.onWindowsChanged(this)
                KeyboardTriggerImeCoordinator.onWindowsChanged(this)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                foregroundTracker.handleWindowsChanged()
                ClipboardFloatImeCoordinator.onWindowsChanged(this)
                KeyboardTriggerImeCoordinator.onWindowsChanged(this)
            }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: SlideIndexAccessibilityService? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        fun dispatchExternalGestureAction(
            action: GestureAction,
            anchorRawY: Float,
            panelSide: com.slideindex.app.overlay.PanelSide? = null
        ): Boolean {
            val service = instance ?: return false
            return service.dispatchExternalGestureAction(action, anchorRawY, panelSide)
        }

        fun isConnected(): Boolean = instance != null

        /** overlay 宿主是否就绪；未就绪时模块不应在输入层吞掉事件。 */
        fun isOverlayReady(): Boolean = instance?.edgeOverlayHost != null

        /**
         * 该边当前是否真的能处理输入层转发的触摸。
         *
         * 只读 OverlayManager 维护的可能力掩码（volatile），可在 binder 线程安全调用。
         */
        fun canHandleForwardedSide(sideId: Int): Boolean {
            val host = instance?.edgeOverlayHost ?: return false
            val side = sideId.toPanelSideOrNull() ?: return false
            return host.isForwardingCapable(side)
        }

        /**
         * 触钮之外的接管目标（角轮盘；悬浮球线条待下一步）的实时命中复核。
         *
         * 由 system_server 的 binder 线程调用，因此只读各宿主维护的 volatile 快照，
         * 不触碰 Compose/UI 状态；命中区随设置或旋转变化时，宿主会刷新该快照。
         */
        fun canHandleForwardedTargetAt(target: Int, x: Float, y: Float): Boolean {
            instance ?: return false
            return when (target) {
                ModuleHookBridgeContract.TARGET_FLOAT_BALL,
                ModuleHookBridgeContract.TARGET_FLOAT_LINE,
                -> com.slideindex.app.overlay.FloatBallOverlay.canAcceptForwardedTouchAt(target, x, y)
                ModuleHookBridgeContract.TARGET_CORNER_LEFT ->
                    CornerGestureHost.canAcceptForwardedTouchAt(CornerAnchor.LEFT, x, y)
                ModuleHookBridgeContract.TARGET_CORNER_RIGHT ->
                    CornerGestureHost.canAcceptForwardedTouchAt(CornerAnchor.RIGHT, x, y)
                else -> canHandleForwardedSide(target)
            }
        }

        /**
         * 接收 system_server 模块在输入层接管后转发来的触摸事件。
         *
         * 返回 false 表示当前 app 无法处理（服务未就绪/边不可用），模块据此放行事件。
         */
        fun handleModuleGestureTouch(
            sessionId: Long,
            sideId: Int,
            action: Int,
            x: Float,
            y: Float,
            eventTime: Long,
            downTime: Long,
            metaState: Int,
        ): Boolean {
            val service = instance ?: return false
            val side = sideId.toPanelSideOrNull()
            val cornerAnchor = if (side == null) sideId.toCornerAnchorOrNull() else null
            val cornerHost = if (cornerAnchor != null) CornerGestureHost.instanceOrNull() else null
            val floatBallTarget = sideId.isFloatBallTarget()
            if (side != null) {
                service.edgeOverlayHost ?: return false
            } else if (cornerHost == null && !floatBallTarget) {
                return false
            }
            com.slideindex.app.overlay.ModuleForwardedTouchGate.markForwarded()
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                service.activeForwardedTarget = sideId
            }
            mainHandler.post {
                val event = android.view.MotionEvent.obtain(downTime, eventTime, action, x, y, metaState)
                try {
                    if (side != null) {
                        service.edgeOverlayHost?.handleForwardedTouch(side, event)
                    } else if (cornerAnchor != null) {
                        cornerHost?.handleForwardedTouch(cornerAnchor, event)
                    } else if (floatBallTarget) {
                        com.slideindex.app.overlay.FloatBallOverlay.handleForwardedTouch(sideId, event)
                    }
                } finally {
                    event.recycle()
                }
            }
            return true
        }

        fun handleModuleGestureSessionEnd(sessionId: Long, reason: Int) {
            val service = instance ?: return
            com.slideindex.app.overlay.ModuleForwardedTouchGate.markForwarded()
            val target = service.activeForwardedTarget
            service.activeForwardedTarget = NO_FORWARDED_TARGET
            if (target == NO_FORWARDED_TARGET) return
            val side = target.toPanelSideOrNull()
            val cornerAnchor = if (side == null) target.toCornerAnchorOrNull() else null
            mainHandler.post {
                if (side != null) {
                    service.edgeOverlayHost?.cancelForwardedTouch(side)
                } else if (cornerAnchor != null) {
                    CornerGestureHost.instanceOrNull()?.cancelForwardedTouch(cornerAnchor)
                } else if (target.isFloatBallTarget()) {
                    com.slideindex.app.overlay.FloatBallOverlay.cancelForwardedTouch(target)
                }
            }
        }

        private fun Int.toPanelSideOrNull(): PanelSide? = when (this) {
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_LEFT -> PanelSide.LEFT
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_RIGHT -> PanelSide.RIGHT
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_BOTTOM -> PanelSide.BOTTOM
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_TOP -> PanelSide.TOP
            else -> null
        }

        private fun Int.toCornerAnchorOrNull(): CornerAnchor? = when (this) {
            ModuleHookBridgeContract.TARGET_CORNER_LEFT -> CornerAnchor.LEFT
            ModuleHookBridgeContract.TARGET_CORNER_RIGHT -> CornerAnchor.RIGHT
            else -> null
        }

        private fun Int.isFloatBallTarget(): Boolean =
            this == ModuleHookBridgeContract.TARGET_FLOAT_BALL ||
                this == ModuleHookBridgeContract.TARGET_FLOAT_LINE

        private const val NO_FORWARDED_TARGET = -1

        fun applyServiceEnabledImmediate(enabled: Boolean) {
            val service = instance
            if (service == null) {
                Log.w(TAG, "applyServiceEnabledImmediate: a11y not connected, enabled=$enabled")
                return
            }
            val hostReady = service.edgeOverlayHost != null
            Log.i(TAG, "applyServiceEnabledImmediate: enabled=$enabled hostReady=$hostReady")
            if (Looper.myLooper() == Looper.getMainLooper()) {
                service.edgeOverlayHost?.applyServiceEnabledImmediate(enabled)
            } else {
                mainHandler.post {
                    instance?.edgeOverlayHost?.applyServiceEnabledImmediate(enabled)
                }
            }
        }

        fun accessibilityInstance(): SlideIndexAccessibilityService? = instance

        fun currentForegroundPackageName(): String? =
            instance?.foregroundTracker?.currPackageName

        fun currentForegroundClassName(): String? =
            instance?.foregroundTracker?.currClassName

        fun perform(action: GestureAction): Boolean =
            SlideIndexAccessibilityGestureInjector.perform(action) { instance }

        fun performUniversalCopy(): Boolean {
            val service = instance ?: return false
            if (UniversalCopyOverlay.isShowing) {
                UniversalCopyOverlay.dismiss()
                return true
            }
            UniversalCopyOverlay.collectAndShow(service)
            return true
        }

        fun performScreenTranslate(): Boolean {
            val service = instance ?: return false
            ScreenTranslationController.toggle(service)
            return true
        }

        fun performScreenSearch(): Boolean {
            val service = instance ?: return false
            val run: () -> Unit = {
                runCatching {
                    ScreenSearchFloating.get(service).togglePanel()
                }.onFailure { error ->
                    Log.e(TAG, "performScreenSearch failed", error)
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                run()
            } else {
                mainHandler.post(run)
            }
            return true
        }

        fun dispatchPointerTap(
            rawX: Float,
            rawY: Float,
            onFinished: (Boolean) -> Unit,
            preferNodeClick: Boolean = false
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerTap(
            instance,
            rawX,
            rawY,
            onFinished,
            preferNodeClick
        )

        fun dispatchTap(
            rawX: Float,
            rawY: Float,
            onFinished: (Boolean) -> Unit,
            durationMs: Long = TAP_DURATION_MS
        ) = SlideIndexAccessibilityGestureInjector.dispatchTap(instance, rawX, rawY, onFinished, durationMs)

        fun dispatchPointerSwipe(
            startX: Float,
            startY: Float,
            config: PointerSwipeConfig,
            onFinished: (Boolean) -> Unit = {}
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerSwipe(instance, startX, startY, config, onFinished)

        fun dispatchPointerSwipePath(
            startX: Float,
            startY: Float,
            path: Path,
            durationMs: Long,
            maxDurationMs: Long = SlideIndexAccessibilityGestureInjector.DEFAULT_SWIPE_MAX_DURATION_MS,
            onFinished: (Boolean) -> Unit = {}
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerSwipePath(
            instance,
            startX,
            startY,
            path,
            durationMs,
            maxDurationMs,
            onFinished
        )

        fun dispatchPointerDragNoFling(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            dragDurationMs: Long = 420L,
            holdDurationMs: Long = 140L,
            onFinished: (Boolean) -> Unit = {}
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerDragNoFling(
            instance,
            startX,
            startY,
            endX,
            endY,
            dragDurationMs,
            holdDurationMs,
            onFinished
        )

        fun dispatchPointerHold(
            rawX: Float,
            rawY: Float,
            durationMs: Long,
            onFinished: (Boolean) -> Unit = {}
        ) = SlideIndexAccessibilityGestureInjector.dispatchPointerHold(instance, rawX, rawY, durationMs, onFinished)


        fun dispatchTapSync(rawX: Float, rawY: Float): Boolean =
            SlideIndexAccessibilityGestureInjector.dispatchTapSync(instance, rawX, rawY)

        const val TAP_DURATION_MS = SlideIndexAccessibilityGestureInjector.TAP_DURATION_MS
        const val POINTER_TAP_DURATION_MS = SlideIndexAccessibilityGestureInjector.POINTER_TAP_DURATION_MS
        const val POINTER_TAP_CHAIN_GAP_MS = SlideIndexAccessibilityGestureInjector.POINTER_TAP_CHAIN_GAP_MS

        fun reloadApps() {
            instance?.edgeOverlayHost?.reloadApps()
        }

        fun setFloatBallStripZonePreview(active: Boolean) {
            com.slideindex.app.overlay.FloatBallOverlay.setStripZonePreviewActive(active)
        }

        fun previewFloatBallPositionYFraction(fraction: Float) {
            com.slideindex.app.overlay.FloatBallOverlay.previewPositionYFraction(fraction)
        }

        fun endFloatBallPositionYPreview(restoreIfNeeded: Boolean) {
            com.slideindex.app.overlay.FloatBallOverlay.endPositionYPreview(restoreIfNeeded)
        }

        fun clearFloatBallPositionYPreviewRestore() {
            com.slideindex.app.overlay.FloatBallOverlay.clearPositionYPreviewRestore()
        }

        fun previewFloatBallAppearance(
            sizeDp: Float? = null,
            opacity: Float? = null,
            visibleFraction: Float? = null,
            lineHeightFraction: Float? = null,
            lineWidthFraction: Float? = null,
            lineOpacity: Float? = null
        ) {
            com.slideindex.app.overlay.FloatBallOverlay.previewAppearance(
                sizeDp = sizeDp,
                opacity = opacity,
                visibleFraction = visibleFraction,
                lineHeightFraction = lineHeightFraction,
                lineWidthFraction = lineWidthFraction,
                lineOpacity = lineOpacity
            )
        }

        fun endFloatBallAppearancePreview(restoreIfNeeded: Boolean) {
            com.slideindex.app.overlay.FloatBallOverlay.endAppearancePreview(restoreIfNeeded)
        }

        fun clearFloatBallAppearancePreviewRestore() {
            com.slideindex.app.overlay.FloatBallOverlay.clearAppearancePreviewRestore()
        }

        fun setCornerZonePreviewActive(active: Boolean) {
            instance?.edgeOverlayHost?.setCornerZonePreviewActive(active)
        }

        fun applyCornerZonePreviewDimensions(
            verticalEdgeWidthDp: Float,
            verticalEdgeHeightDp: Float,
            horizontalEdgeWidthDp: Float,
            horizontalEdgeHeightDp: Float
        ) {
            instance?.edgeOverlayHost?.applyCornerZonePreviewDimensions(
                verticalEdgeWidthDp,
                verticalEdgeHeightDp,
                horizontalEdgeWidthDp,
                horizontalEdgeHeightDp
            )
        }

        fun previewIndexHeightFraction(fraction: Float) {
            instance?.edgeOverlayHost?.previewIndexHeightFraction(fraction)
        }

        fun clearIndexHeightPreview() {
            instance?.edgeOverlayHost?.clearIndexHeightPreview()
        }

        fun mergeTriggerHandleLayoutPreview(
            side: com.slideindex.app.overlay.PanelSide,
            handleId: String,
            edgeWidthDp: Float? = null,
            topFraction: Float? = null,
            bottomFraction: Float? = null,
            shortSwipeDistanceDp: Float? = null,
            longSwipeDistanceDp: Float? = null,
            design: com.slideindex.app.gesture.TriggerHandleDesign? = null
        ) {
            instance?.edgeOverlayHost?.mergeTriggerHandleLayoutPreview(
                side = side,
                handleId = handleId,
                edgeWidthDp = edgeWidthDp,
                topFraction = topFraction,
                bottomFraction = bottomFraction,
                shortSwipeDistanceDp = shortSwipeDistanceDp,
                longSwipeDistanceDp = longSwipeDistanceDp,
                design = design
            )
        }

        fun clearTriggerHandleLayoutPreview() {
            instance?.edgeOverlayHost?.clearTriggerHandleLayoutPreview()
        }

        fun clearOverlayLayoutPreview() {
            instance?.edgeOverlayHost?.clearOverlayLayoutPreview()
        }

        fun setPreviewMode(
            enabled: Boolean,
            content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
            focus: LayoutPreviewFocus? = null
        ) {
            instance?.edgeOverlayHost?.setPreviewMode(enabled, content, focus)
        }

        fun setGestureAnglesPreview(angles: com.slideindex.app.gesture.GestureAngles?) {
            instance?.edgeOverlayHost?.setGestureAnglesPreview(angles)
        }

        fun recoverOverlaysIfIdle() {
            instance?.edgeOverlayHost?.recoverOverlaysIfIdle()
        }

        fun refreshOverlaySuppression() {
            instance?.edgeOverlayHost?.refreshOverlaySuppression()
        }

        fun recoverTriggerInteraction(forceReAddChrome: Boolean = false) {
            instance?.edgeOverlayHost?.recoverTriggerInteraction(forceReAddChrome)
        }

        fun onKeyboardImeChanged(visibilityChanged: Boolean = false) {
            instance?.edgeOverlayHost?.onKeyboardImeChanged(visibilityChanged)
        }

        fun refreshTriggerVisuals() {
            instance?.edgeOverlayHost?.refreshTriggerVisuals()
        }

        fun bringEdgeChromeAbovePanels(forceReAdd: Boolean = true, sides: Set<PanelSide>? = null) {
            instance?.edgeOverlayHost?.bringEdgeChromeAbovePanels(forceReAdd, sides)
        }

        fun edgePresentationNeedsChromeRaise(): Boolean =
            instance?.edgeOverlayHost?.edgePresentationNeedsChromeRaise() == true

        fun notifyEdgeChromeBelowPanel() {
            instance?.edgeOverlayHost?.notifyEdgeChromeBelowPanel()
        }

        fun suspendAllEdgeOverlays() {
            instance?.edgeOverlayHost?.suspendAllEdgeOverlays()
        }

        fun resumeAllEdgeOverlays() {
            instance?.edgeOverlayHost?.resumeAllEdgeOverlays()
        }

        fun suspendEdgeCapturesForPassthrough() {
            instance?.edgeOverlayHost?.suspendEdgeCapturesForPassthrough()
        }

        fun resumeEdgeCapturesAfterPassthrough() {
            instance?.edgeOverlayHost?.resumeEdgeCapturesAfterPassthrough()
        }

        fun overlayHostContext(): Context? = instance

        fun collectTextAt(rawX: Float, rawY: Float): String? {
            val service = instance ?: return null
            return AccessibilityTextExtractor.collectTextAt(service, rawX, rawY)
        }

        fun findControlBoundsAt(
            rawX: Float,
            rawY: Float,
            activeWindowOnly: Boolean = false,
            maxNodes: Int = AccessibilityTextExtractor.DEFAULT_MAX_TRAVERSAL_NODES
        ): Rect? {
            val service = instance ?: return null
            return AccessibilityTextExtractor.findControlBoundsAt(
                service,
                rawX,
                rawY,
                activeWindowOnly,
                maxNodes
            )
        }

        fun collectTextInRect(rect: Rect): String {
            val service = instance ?: return ""
            return AccessibilityTextExtractor.collectTextInRect(service, rect)
        }

        fun pickFloatBallTextInRect(
            context: Context,
            rect: Rect,
            ocrFallbackEnabled: Boolean,
            ocrModelId: String,
            previewBoundsPick: Boolean = false,
            onResult: (FloatBallPickResult) -> Unit
        ) {
            val service = instance ?: run {
                onResult(
                    FloatBallPickResult(
                        a11yText = null,
                        ocrText = null,
                        screenshot = null,
                        screenRect = null
                    )
                )
                return
            }
            FloatBallTextPickCoordinator.pickInRect(
                service,
                context,
                rect,
                ocrFallbackEnabled,
                ocrModelId,
                previewBoundsPick,
                onResult
            )
        }

        fun pickFullscreen(
            context: Context,
            ocrFallbackEnabled: Boolean,
            ocrModelId: String
        ): Boolean {
            val (screenWidth, screenHeight) = FloatBallOcrRegions.accessibilityScreenSizePx(context)
            if (screenWidth <= 0 || screenHeight <= 0) return false
            val panelAnchorX = screenWidth / 2f
            val panelAnchorY = screenHeight.toFloat()
            FloatBallPickResultPanel.showLoading(
                context,
                panelAnchorX,
                panelAnchorY,
                PickResultTextSource.OCR
            )
            pickFloatBallOnRelease(
                context = context,
                startX = 0f,
                startY = 0f,
                endX = screenWidth.toFloat(),
                endY = screenHeight.toFloat(),
                regionalRect = true,
                ocrFallbackEnabled = ocrFallbackEnabled,
                ocrModelId = ocrModelId
            ) { result ->
                FloatBallPickResultPanel.showResult(context, panelAnchorX, panelAnchorY, result)
            }
            return true
        }

        fun pickFloatBallOnRelease(
            context: Context,
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            regionalRect: Boolean,
            ocrFallbackEnabled: Boolean,
            ocrModelId: String,
            onResult: (FloatBallPickResult) -> Unit
        ) {
            val service = instance ?: run {
                onResult(
                    FloatBallPickResult(
                        a11yText = null,
                        ocrText = null,
                        screenshot = null,
                        screenRect = null
                    )
                )
                return
            }
            FloatBallTextPickCoordinator.pickOnRelease(
                service,
                context,
                startX,
                startY,
                endX,
                endY,
                regionalRect,
                ocrFallbackEnabled,
                ocrModelId,
                onResult
            )
        }

        fun currentForegroundPackage(): String? = instance?.foregroundPackageName()

        /**
         * 编排在注入失败/被关闭后调用：同步做一次无障碍填充。
         *
         * 返回 null 表示无障碍服务没连着；返回结果里的 success/strategy/reason 直接用于记账。
         * 必须由主线程调用（编排的 handler 就是主线程）。
         */
        fun fillOtpNow(code: String, settings: AppSettings): OtpAutoFillController.FillOutcome? {
            val service = instance ?: return null
            return OtpAutoFillController.fillNow(service, settings, code)
        }

        private const val TAG = "SlideIndexA11y"
        private const val CONFIG_CHANGE_SUPPRESSION_RETRY_MS = 400L
        private const val SCROLL_BOTTOM_STROKE_COUNT = 10
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        com.slideindex.app.overlay.OverlayStatePort.publish(this, "onServiceConnected")
        watchdog = SlideIndexAccessibilityWatchdog(this) { edgeOverlayHost }
        foregroundTracker = SlideIndexAccessibilityForegroundTracker(
            service = this,
            overlayHost = { edgeOverlayHost },
            onMaybeOtp = {},
            onSyncLockScreen = { watchdog.syncLockScreenState() },
            excludedPackageProvider = {
                deps.settingsRepository.readSnapshot().previousAppExcludedPackages
            }
        )
        watchdog.syncLockScreenState()
        edgeOverlayHost = EdgeOverlayHost(this, serviceScope, deps).also { it.start() }
        watchdog.registerScreenLockReceiver()
        lastOrientation = resources.configuration.orientation
        syncMonitoring()
        backTapGestureHost.start(serviceScope)
        GestureToggleTileWarmup.requestListening(this, "a11yConnected")
        notifyModuleHostState(ready = true)
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
        syncForegroundPackageForOverlaySuppression()
        edgeOverlayHost?.onConfigurationChanged()
        scheduleOverlaySuppressionAfterConfigurationChange()
    }

    private val configChangeSuppressionRunnable = Runnable {
        syncForegroundPackageForOverlaySuppression()
        edgeOverlayHost?.recoverTriggerInteraction(forceReAddChrome = false)
        edgeOverlayHost?.refreshOverlaySuppression()
    }

    private val configChangeFinalSettleRunnable = Runnable {
        syncForegroundPackageForOverlaySuppression()
        edgeOverlayHost?.recoverTriggerInteraction(forceReAddChrome = false)
        edgeOverlayHost?.refreshOverlaySuppression()
    }

    private fun scheduleOverlaySuppressionAfterConfigurationChange() {
        mainHandler.removeCallbacks(configChangeSuppressionRunnable)
        mainHandler.removeCallbacks(configChangeFinalSettleRunnable)
        mainHandler.postDelayed(configChangeSuppressionRunnable, CONFIG_CHANGE_SUPPRESSION_RETRY_MS)
        mainHandler.postDelayed(configChangeFinalSettleRunnable, CONFIG_CHANGE_SUPPRESSION_RETRY_MS * 2)
    }

    private fun syncForegroundPackageForOverlaySuppression() {
        val resolved = com.slideindex.app.util.AccessibilityForegroundResolver.resolveHostPackage(this)
        if (resolved != null) {
            edgeOverlayHost?.updateForegroundPackage(resolved)
            return
        }
        val selfPackage = applicationContext.packageName
        val activePkg = if (::foregroundTracker.isInitialized) {
            foregroundTracker.currPackageName?.takeIf { it.isNotBlank() && it != selfPackage }
        } else {
            null
        }
        activePkg?.let { edgeOverlayHost?.updateForegroundPackage(it) }
            ?: edgeOverlayHost?.refreshOverlaySuppression()
    }

    fun dispatchExternalGestureAction(
        action: GestureAction,
        anchorRawY: Float,
        panelSide: com.slideindex.app.overlay.PanelSide? = null
    ): Boolean =
        edgeOverlayHost?.dispatchExternalGestureAction(action, anchorRawY, panelSide) == true

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "onUnbind: accessibility service unbound by system")
        edgeOverlayHost?.stop()
        edgeOverlayHost = null
        if (::watchdog.isInitialized) {
            watchdog.unregisterScreenLockReceiver()
            watchdog.releaseWakeLock()
        }
        ScreenSearchFloating.destroy()
        if (::backTapGestureHost.isInitialized) backTapGestureHost.stop()
        instance = null
        com.slideindex.app.overlay.OverlayStatePort.publish(this, "onUnbind")
        notifyModuleHostState(ready = false)
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.i(TAG, "onRebind: accessibility service rebound by system")
        instance = this
        com.slideindex.app.overlay.OverlayStatePort.publish(this, "onRebind")
        if (edgeOverlayHost == null) {
            edgeOverlayHost = EdgeOverlayHost(this, serviceScope, deps).also { it.start() }
        }
        if (::watchdog.isInitialized) {
            watchdog.registerScreenLockReceiver()
            watchdog.syncLockScreenState()
        }
        if (::backTapGestureHost.isInitialized) backTapGestureHost.start(serviceScope)
        syncMonitoring()
        GestureToggleTileWarmup.requestListening(this, "a11yRebound")
        notifyModuleHostState(ready = true)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(configChangeSuppressionRunnable)
        mainHandler.removeCallbacks(configChangeFinalSettleRunnable)
        ClipboardAccess.repository?.stopListening()
        if (::watchdog.isInitialized) {
            watchdog.unregisterScreenLockReceiver()
            watchdog.releaseWakeLock()
        }
        edgeOverlayHost?.stop()
        edgeOverlayHost = null
        ScreenSearchFloating.destroy()
        if (::backTapGestureHost.isInitialized) backTapGestureHost.stop()
        serviceScope.cancel()
        instance = null
        com.slideindex.app.overlay.OverlayStatePort.publish(this, "onDestroy")
        notifyModuleHostState(ready = false)
        super.onDestroy()
    }

    /**
     * 通知 system_server 里的 LSPosed 模块：app 侧边缘 overlay 宿主是否就绪。
     *
     * 模块收到 ready 会立刻重绑事件桥（不必等 2.5s 冷却 + 下一次触摸/状态探测），
     * 收到失活会立刻收掉可能存在的吞流会话。广播内容只有一个布尔值，
     * 不携带任何用户数据（与配置下发一样是自定义 action 的普通广播）。
     */
    private fun notifyModuleHostState(ready: Boolean) {
        runCatching {
            sendBroadcast(
                Intent(ModuleHookBridgeContract.ACTION_HOST_STATE_CHANGED).apply {
                    putExtra(ModuleHookBridgeContract.EXTRA_HOST_READY, ready)
                },
            )
        }.onFailure { Log.w(TAG, "notifyModuleHostState($ready) failed: ${it.message}") }
    }

    internal fun launchPreviousApp(): Boolean = foregroundTracker.launchPreviousApp()

    internal fun foregroundPackageName(): String? =
        if (::foregroundTracker.isInitialized) foregroundTracker.currPackageName else null

    internal fun toggleKeepScreenOn(): Boolean = watchdog.toggleKeepScreenOn()

    internal fun syncMonitoring() {
        syncClipboardMonitoring()
        syncScreenshotMonitoring()
    }

    internal fun syncClipboardMonitoring() {
        val repository = ClipboardAccess.repository ?: return
        ClipboardMonitorStartup.runOnMainWhenReady {
            repository.syncClipboardMonitoringFromSettings()
        }
    }

    internal fun syncScreenshotMonitoring() {
        val repository = ClipboardAccess.repository ?: return
        val settings = deps.settingsRepository.readSnapshot()
        if (settings.clipboardScreenshotMonitoring &&
            ClipboardPermissionHelper.hasMediaReadPermission(this)
        ) {
            repository.startScreenshotMonitoring()
        } else {
            repository.stopScreenshotMonitoring()
        }
    }

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
                    GestureDescription.StrokeDescription(path, index * 80L, 12)
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
