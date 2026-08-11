package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.IndexSessionHost
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.GestureActionIconBitmap
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.OverlayBrightnessControl

/**
 * 边缘手势 Overlay 编排层：触摸分发、会话生命周期与各面板 Controller 协调。
 */
@SuppressLint("ViewConstructor") // Programmatically created overlay; not inflated from XML
class EdgeGestureOverlayView(
    context: Context,
    private val side: PanelSide,
    private val appRepository: AppRepository,
    private val onSessionStartCallback: () -> Unit,
    private val onSessionEndCallback: () -> Unit,
    private val onGestureTrackingStartCallback: () -> Unit = {},
    private val onAdjustPanelLayoutCallback: (Float) -> Unit = {},
    private val onAdjustPanelDismissCallback: () -> Unit = {},
    private val onClickPassthroughCallback: (Float, Float, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit = {},
    private val onQuickLauncherPanelItemsPersist: (String, List<QuickLauncherItem>) -> Unit = { _, _ -> },
    private val onShellPanelFocusChange: (Boolean) -> Unit = {},
    private val onOverlayWindowSuspend: () -> Unit = {},
    private val onOverlayWindowResume: () -> Unit = {},
    private val onOverlayPresentationSuspend: () -> Unit = {},
    private val onOverlayPresentationResume: () -> Unit = {},
    private val onShellPanelAuxiliaryPrepare: () -> Unit = {},
    private val onShellPanelAuxiliaryDismiss: () -> Unit = {},
    overlayBrightness: OverlayBrightnessControl? = null,
) : View(context), IndexSessionHost {

    private val gestureCallbacks = GestureSessionCallbackBridge()

    private var settings = AppSettings()
    private var apps: List<AppInfo> = emptyList()
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null

    private val zoneLayout = GestureZoneLayout(side)
    private val indexSession = SlideAlongRailSession(side, zoneLayout, this)
    private val panelGridSession = PanelGridSession()
    private val actionExecutor = ActionExecutor(
        context = context,
        appRepository = appRepository,
        clickPassthroughHandler = onClickPassthroughCallback,
        overlayBrightness = overlayBrightness,
        side = side,
        onShellCommandsPersist = onShellCommandsPersist,
    )
    private val pathRecognizer = SwipePathRecognizer(side, resources.displayMetrics.density)
    private val panelContentRect = RectF()
    private val panelEnterAnimator = OverlayPanelEnterAnimator(side, ::dp) { invalidate() }
    private var edgeCaptureTouchActive = false
    private val iconSizePx: Float get() = dp(44f)

    private val gestureSession = GestureSession(
        side = side,
        zoneLayout = zoneLayout,
        indexSession = indexSession,
        pathRecognizer = pathRecognizer,
        actionExecutor = actionExecutor,
        callbacks = gestureCallbacks,
    )

    private val gestureAnimationCoordinator = GestureAnimationCoordinator(
        side = side,
        gestureSessionProvider = { gestureSession },
        pathRecognizerProvider = { pathRecognizer },
        settingsProvider = { settings },
        post = { action -> post(action) },
    )

    private val overlayHosts = EdgeGestureOverlayHosts(
        view = this,
        side = side,
        appRepository = appRepository,
        zoneLayout = zoneLayout,
        indexSession = indexSession,
        panelGridSession = panelGridSession,
        actionExecutor = actionExecutor,
        pathRecognizer = pathRecognizer,
        gestureSession = gestureSession,
        panelEnterAnimator = panelEnterAnimator,
        panelContentRect = panelContentRect,
        settingsProvider = { settings },
        appsProvider = { apps },
        iconForFn = { app -> iconFor(app) },
        dpFn = ::dp,
        spFn = ::sp,
        runAfterLayoutFn = ::runAfterLayout,
        activeTriggerZoneRectFn = ::activeTriggerZoneRect,
        clearEdgeCaptureTouchActiveFn = { edgeCaptureTouchActive = false },
        onInitiatingEdgeGestureReleasedFn = {
            edgeCaptureTouchActive = false
            gestureAnimationCoordinator.onTouchCanceled()
            notifyPresentationTouchRequirementChanged()
        },
        notifyPresentationTouchRequirementChangedFn = ::notifyPresentationTouchRequirementChanged,
        notifyOverlayLayoutIfNeededFn = ::notifyOverlayLayoutIfNeeded,
        onAdjustPanelDismissFn = onAdjustPanelDismissCallback,
        onSessionStartFn = onSessionStartCallback,
        onShellCommandsPersistFn = onShellCommandsPersist,
        onQuickLauncherPanelItemsPersistFn = onQuickLauncherPanelItemsPersist,
        onShellPanelFocusChangeFn = onShellPanelFocusChange,
        onOverlayWindowSuspendFn = onOverlayWindowSuspend,
        onOverlayWindowResumeFn = onOverlayWindowResume,
        onShellPanelAuxiliaryPrepareFn = onShellPanelAuxiliaryPrepare,
        onShellPanelAuxiliaryDismissFn = onShellPanelAuxiliaryDismiss,
    )

    private val shellCoordinator: ShellPanelOverlayController = ShellPanelOverlayController(overlayHosts)
    private val quickLauncherController: QuickLauncherOverlayController = QuickLauncherOverlayController(overlayHosts)
    private val indexPanelRenderer: IndexPanelRenderer = IndexPanelRenderer(overlayHosts).also {
        overlayHosts.indexPanelContentRectProvider = { it.indexPanelContentRect() }
    }
    private val adjustPanelController: AdjustPanelOverlayController = AdjustPanelOverlayController(overlayHosts)
    private val taskSwitcherController: TaskSwitcherOverlayController = TaskSwitcherOverlayController(overlayHosts)

    private var overlayAccessibilityDelegate: OverlayAccessibilityDelegate? = null
    private var lastAccessibilityFingerprint: Int = 0

    private val layoutCoordinator = EdgeGestureLayoutCoordinator(
        context = context,
        resources = resources,
        zoneLayout = zoneLayout,
        gestureSession = gestureSession,
        adjustPanelController = adjustPanelController,
        quickLauncherController = quickLauncherController,
        shellCoordinator = shellCoordinator,
        settingsProvider = { settings },
        previewModeProvider = { previewMode },
        viewSizeProvider = { width to height },
        onSessionEnd = onSessionEndCallback,
    )

    private val sessionCoordinator = EdgeGestureSessionCoordinator(
        view = this,
        gestureSession = gestureSession,
        panelGridSession = panelGridSession,
        panelEnterAnimator = panelEnterAnimator,
        adjustPanelController = adjustPanelController,
        taskSwitcherController = taskSwitcherController,
        quickLauncherController = quickLauncherController,
        shellCoordinator = shellCoordinator,
        gestureAnimationCoordinator = gestureAnimationCoordinator,
        layoutCoordinator = layoutCoordinator,
        actionExecutor = actionExecutor,
        settingsProvider = { settings },
        runAfterLayout = ::runAfterLayout,
        onSessionStartCallback = onSessionStartCallback,
        onAdjustPanelLayoutCallback = onAdjustPanelLayoutCallback,
        notifyPresentationTouchRequirementChanged = ::notifyPresentationTouchRequirementChanged,
        requestInvalidate = ::invalidate,
        indexPanelContentRect = { indexPanelRenderer.indexPanelContentRect() },
        onIndexSessionStart = ::warmIndexLaunchIcons,
        notifyAccessibilityStructure = ::notifyOverlayAccessibilityStructureIfNeeded,
    )

    init {
        gestureCallbacks.delegate = sessionCoordinator
    }

    private val panelRenderer = EdgeGesturePanelRenderer(
        side = side,
        gestureSession = gestureSession,
        indexSession = indexSession,
        adjustPanelController = adjustPanelController,
        indexPanelRenderer = indexPanelRenderer,
        quickLauncherController = quickLauncherController,
        taskSwitcherController = taskSwitcherController,
        shellCoordinator = shellCoordinator,
        panelEnterAnimator = panelEnterAnimator,
        panelContentRect = panelContentRect,
        zoneLayout = zoneLayout,
        settingsProvider = { settings },
        previewModeProvider = { previewMode },
        previewContentProvider = { previewContent },
        previewFocusProvider = { previewFocus },
        densityProvider = { resources.displayMetrics.density },
        dpFn = ::dp,
        syncZoneLayout = { layoutCoordinator.syncZoneLayout() },
    )

    private val touchDispatcher = EdgeGestureTouchDispatcher(
        gestureSession = gestureSession,
        adjustPanelController = adjustPanelController,
        quickLauncherController = quickLauncherController,
        shellCoordinator = shellCoordinator,
        taskSwitcherController = taskSwitcherController,
        indexPanelRenderer = indexPanelRenderer,
        gestureAnimationCoordinator = gestureAnimationCoordinator,
        rawToLocal = ::rawToLocal,
        forEachGesturePoint = ::forEachGesturePoint,
        onGestureTrackingStart = onGestureTrackingStartCallback,
        onSyncZoneLayout = { layoutCoordinator.syncZoneLayout() },
        onForceRecoverInteractionState = ::forceRecoverInteractionState,
        edgeCaptureTouchActive = { edgeCaptureTouchActive },
        setEdgeCaptureTouchActive = { edgeCaptureTouchActive = it },
        composeOverlayDialogShowing = layoutCoordinator::composeOverlayDialogShowing,
    )

    init {
        isClickable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        overlayAccessibilityDelegate = installEdgeGestureOverlayAccessibility(this) {
            buildOverlayAccessibilitySnapshot()
        }
        setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }
            when {
                shellCoordinator.handleBackPress() -> true
                quickLauncherController.handleBackPress() -> true
                taskSwitcherController.handleBackPress() -> true
                else -> false
            }
        }
    }

    fun gestureAnimationCoordinator(): GestureAnimationCoordinator = gestureAnimationCoordinator

    fun applySettings(newSettings: AppSettings, screenWidth: Int) {
        settings = newSettings
        shellCoordinator.syncSettings(newSettings)
        quickLauncherController.syncSettings(newSettings)
        indexPanelRenderer.syncSettings(newSettings)
        gestureSession.applySettings(newSettings)
        quickLauncherController.invalidateDerivedCaches()
        gestureAnimationCoordinator.applySettings(newSettings)
        layoutCoordinator.syncZoneLayout()
        invalidate()
    }

    private fun activeTriggerZoneRect(): RectF = layoutCoordinator.activeTriggerZoneRect()

    private fun notifyOverlayLayoutIfNeeded() = layoutCoordinator.notifyOverlayLayoutIfNeeded()

    fun dispatchExternalAction(action: GestureAction, anchorRawY: Float): Boolean {
        layoutCoordinator.applyExpandedOverlayLayout()
        runAfterLayout {
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
            val viewHeight = if (height > 0) height.toFloat() else screenHeight
            val localY = (anchorRawY - loc[1]).coerceIn(0f, viewHeight)
            val screenWidth = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val localX = if (width > 0) width / 2f else screenWidth / 2f
            val anchorRawX = if (width > 0) loc[0] + localX else screenWidth / 2f
            when (action) {
                is GestureAction.TaskSwitcher -> taskSwitcherController.setExternalAnchor(anchorRawY)
                is GestureAction.QuickLauncher -> quickLauncherController.setAnchorRawY(anchorRawY)
                else -> Unit
            }
            gestureSession.openDiscretePanel(action, localX, localY, anchorRawX, anchorRawY)
            notifyPresentationTouchRequirementChanged()
            invalidate()
        }
        return true
    }

    fun isSessionActive(): Boolean = gestureSession.isActive()

    fun panelMode(): OverlayPanelMode = gestureSession.panelMode()

    fun applyCollapsedTriggerLayout(bounds: CollapsedWindowBounds) {
        layoutCoordinator.applyExpandedOverlayLayout()
    }

    fun applyExpandedOverlayLayout() = layoutCoordinator.applyExpandedOverlayLayout()

    fun needsPresentationDirectTouch(): Boolean = layoutCoordinator.needsPresentationDirectTouch()

    fun presentationShouldPassthroughTouches(): Boolean =
        layoutCoordinator.presentationShouldPassthroughTouches()

    fun syncOverlayDialogZOrder() {
        quickLauncherController.syncOverlayDialogZOrder()
    }

    var onPresentationTouchRequirementChanged: (() -> Unit)? = null

    private fun notifyPresentationTouchRequirementChanged() {
        onPresentationTouchRequirementChanged?.invoke()
    }

    fun handleOverlayTouch(event: MotionEvent): Boolean = touchDispatcher.handleTouch(event)

    @SuppressLint("ClickableViewAccessibility") // Overlay gesture surface; not a clickable control
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (layoutCoordinator.composeOverlayDialogShowing()) return false
        if (!needsPresentationDirectTouch()) return false
        return handleOverlayTouch(event)
    }

    fun applyGestureTrackingLayout(bounds: CollapsedWindowBounds) =
        layoutCoordinator.applyGestureTrackingLayout(bounds)

    fun applyAdjustPanelOverlayLayout() = layoutCoordinator.applyAdjustPanelOverlayLayout()

    fun hasAdjustPanel(): Boolean = adjustPanelController.hasAdjustPanel()

    fun keepsOverlayExpanded(): Boolean = layoutCoordinator.keepsOverlayExpanded()

    fun forceRecoverInteractionState() {
        if (adjustPanelController.isDismissing()) return
        // Activity 版 Shell 面板不随 edge recover 关闭（触钮点按收回会走到这里）。
        shellCoordinator.clearShellContinuousPick()
        gestureAnimationCoordinator.hide()
        edgeCaptureTouchActive = false
        adjustPanelController.forceRecover()
        gestureSession.forceReset(notifySessionEnd = false)
        notifyPresentationTouchRequirementChanged()
        layoutCoordinator.syncZoneLayout()
        invalidate()
    }

    fun isPreviewMode(): Boolean = previewMode

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        previewMode = enabled
        previewContent = content
        previewFocus = focus
        layoutCoordinator.syncZoneLayout()
        invalidate()
    }

    fun setApps(newApps: List<AppInfo>) {
        if (appsContentEqual(apps, newApps)) return
        apps = newApps
        indexSession.setApps(newApps)
        quickLauncherController.setApps(newApps)
        warmIndexLaunchIcons()
        invalidate()
    }

    private fun appsContentEqual(old: List<AppInfo>, new: List<AppInfo>): Boolean {
        if (old.size != new.size) return false
        return old.indices.all { i ->
            val a = old[i]
            val b = new[i]
            a.packageName == b.packageName && a.label == b.label && a.letter == b.letter
        }
    }

    fun warmIndexLaunchIcons() {
        val size = iconSizePx.toInt().coerceAtLeast(1)
        appRepository.warmLaunchIconBitmapsAsync(apps.map { it.packageName }, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        quickLauncherController.onSizeChanged()
        layoutCoordinator.syncZoneLayout()
        adjustPanelController.onSizeChanged()
    }

    override fun onDetachedFromWindow() {
        gestureAnimationCoordinator.hide()
        adjustPanelController.onDetachedFromWindow()
        GestureActionIconBitmap.clear()
        super.onDetachedFromWindow()
    }

    fun showAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        @Suppress("UNUSED_PARAMETER") deferWindowLayout: Boolean = false,
    ) {
        onAdjustPanelLayoutCallback(anchorRawY)
        adjustPanelController.showAdjustPanel(mode, fraction, anchorRawY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        panelRenderer.draw(canvas, width, height)
    }

    private fun buildOverlayAccessibilitySnapshot(): OverlayAccessibilitySnapshot =
        EdgeGestureOverlayAccessibilityCollector.collect(
            context = context,
            side = side,
            panelMode = gestureSession.panelMode(),
            zoneLayout = zoneLayout,
            indexSession = indexSession,
            panelGridSession = panelGridSession,
            quickLauncherController = quickLauncherController,
            taskSwitcherController = taskSwitcherController,
            shellPanelController = shellCoordinator.shellCommandPanelController(),
            appsByPackage = quickLauncherController.quickLauncherAppsByPackage,
        )

    private fun notifyOverlayAccessibilityIfChanged() {
        val fingerprint = buildOverlayAccessibilitySnapshot().nodes.hashCode() +
            gestureSession.panelMode().ordinal * 31
        if (fingerprint != lastAccessibilityFingerprint) {
            lastAccessibilityFingerprint = fingerprint
            overlayAccessibilityDelegate?.notifyStructureChanged()
        }
    }

    fun notifyOverlayAccessibilityStructureIfNeeded() {
        if (!isAttachedToWindow) return
        post { notifyOverlayAccessibilityIfChanged() }
    }

    override fun invalidate() {
        super.invalidate()
        if (!isAttachedToWindow) return
        if (gestureSession.isActive() ||
            (width > 0 && height > 0 && !edgeCaptureTouchActive)
        ) {
            notifyOverlayAccessibilityStructureIfNeeded()
        }
    }

    override fun hapticLetterTick() = sessionCoordinator.hapticLetterTick()
    override fun hapticAppTick() = sessionCoordinator.hapticAppTick()
    override fun hapticConfirmLaunch() = sessionCoordinator.hapticConfirmLaunch()
    override fun scheduleDelayed(runnable: Runnable, delayMs: Long) =
        sessionCoordinator.scheduleDelayed(runnable, delayMs)
    override fun cancelDelayed(runnable: Runnable) = sessionCoordinator.cancelDelayed(runnable)
    override fun requestInvalidate() {
        if (gestureSession.panelMode() == OverlayPanelMode.INDEX) {
            invalidateIndexPanel()
        } else {
            sessionCoordinator.requestInvalidateThrottled()
        }
    }

    @Suppress("DEPRECATION")
    private fun invalidateIndexPanel() {
        val rect = indexPanelRenderer.indexPanelContentRect()
        val pad = dp(4f).toInt()
        invalidate(
            (rect.left - pad).toInt().coerceAtLeast(0),
            (rect.top - pad).toInt().coerceAtLeast(0),
            (rect.right + pad).toInt().coerceAtMost(width.coerceAtLeast(1)),
            (rect.bottom + pad).toInt().coerceAtMost(height.coerceAtLeast(1)),
        )
        notifyOverlayAccessibilityStructureIfNeeded()
    }

    private fun runAfterLayout(block: () -> Unit) {
        if (isAttachedToWindow && width > 0 && height > 0) {
            block()
            return
        }
        val observer = viewTreeObserver
        if (!observer.isAlive) {
            post { runAfterLayout(block) }
            return
        }
        observer.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (width <= 0 || height <= 0) return
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    block()
                }
            },
        )
        requestLayout()
    }

    private fun forEachGesturePoint(
        event: MotionEvent,
        localX: Float,
        localY: Float,
        includeHistory: Boolean,
        block: (rawX: Float, rawY: Float, localX: Float, localY: Float) -> Unit,
    ) {
        if (includeHistory) {
            val rawOffsetX = event.rawX - event.x
            val rawOffsetY = event.rawY - event.y
            for (i in 0 until event.historySize) {
                val rawX = event.getHistoricalX(i) + rawOffsetX
                val rawY = event.getHistoricalY(i) + rawOffsetY
                val (lx, ly) = rawToLocal(rawX, rawY)
                block(rawX, rawY, lx, ly)
            }
        }
        block(event.rawX, event.rawY, localX, localY)
    }

    private fun rawToLocal(rawX: Float, rawY: Float): Pair<Float, Float> {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return rawX - loc[0] to rawY - loc[1]
    }

    private fun iconFor(app: AppInfo): Bitmap =
        appRepository.launchIconBitmap(app.packageName, iconSizePx.toInt().coerceAtLeast(1))

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
