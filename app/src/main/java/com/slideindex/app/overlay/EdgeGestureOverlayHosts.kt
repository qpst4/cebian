package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.HapticHelper

internal class EdgeGestureOverlayHosts(
    private val view: View,
    private val side: PanelSide,
    private val appRepository: AppRepository,
    private val zoneLayout: GestureZoneLayout,
    private val indexSession: SlideAlongRailSession,
    private val panelGridSession: PanelGridSession,
    private val actionExecutor: ActionExecutor,
    private val pathRecognizer: SwipePathRecognizer,
    private val gestureSession: GestureSession,
    private val panelEnterAnimator: OverlayPanelEnterAnimator,
    private val panelContentRect: RectF,
    private val settingsProvider: () -> AppSettings,
    private val appsProvider: () -> List<AppInfo>,
    private val dpFn: (Float) -> Float,
    private val spFn: (Float) -> Float,
    private val runAfterLayoutFn: (() -> Unit) -> Unit,
    private val activeTriggerZoneRectFn: () -> RectF,
    private val clearEdgeCaptureTouchActiveFn: () -> Unit,
    private val onInitiatingEdgeGestureReleasedFn: () -> Unit,
    private val notifyPresentationTouchRequirementChangedFn: () -> Unit,
    private val notifyOverlayLayoutIfNeededFn: () -> Unit,
    private val onAdjustPanelDismissFn: () -> Unit,
    private val onSessionStartFn: () -> Unit,
    private val onShellCommandsPersistFn: (List<ShellCommand>) -> Unit,
    private val onQuickLauncherPanelItemsPersistFn: (String, List<QuickLauncherItem>) -> Unit,
    private val onShellPanelFocusChangeFn: (Boolean) -> Unit,
    private val onOverlayWindowSuspendFn: () -> Unit,
    private val onOverlayWindowResumeFn: () -> Unit,
    private val onShellPanelAuxiliaryPrepareFn: () -> Unit,
    private val onShellPanelAuxiliaryDismissFn: () -> Unit,
    private val iconForFn: (AppInfo) -> Bitmap,
) : ShellPanelOverlayController.Host,
    QuickLauncherOverlayController.Host,
    IndexPanelRenderer.Host,
    AdjustPanelOverlayController.Host,
    TaskSwitcherOverlayController.Host {

    var indexPanelContentRectProvider: (() -> RectF)? = null

    override val context: Context get() = view.context

    override fun settings(): AppSettings = settingsProvider()

    override fun side(): PanelSide = side

    override fun apps(): List<AppInfo> = appsProvider()

    override fun gestureSession(): GestureSession = gestureSession

    override fun zoneLayout(): GestureZoneLayout = zoneLayout

    override fun pathRecognizer(): SwipePathRecognizer = pathRecognizer

    override fun actionExecutor(): ActionExecutor = actionExecutor

    override fun panelGridSession(): PanelGridSession = panelGridSession

    override fun panelEnterProgress(): Float = panelEnterAnimator.progress

    override fun panelEnterAdjustedX(localX: Float, panel: RectF): Float =
        panelEnterAnimator.adjustedX(localX, panel)

    override fun panelEnterOffsetX(panel: RectF): Float = panelEnterAnimator.enterOffsetX(panel)

    override fun panelContentRect(): RectF = panelContentRect

    override fun drawWithPanelEnterAnimation(
        canvas: Canvas,
        contentRect: RectF,
        drawContent: () -> Unit,
    ) = panelEnterAnimator.drawWithAnimation(canvas, contentRect, drawContent)

    override fun activeTriggerZoneRect(): RectF = activeTriggerZoneRectFn()

    override fun viewWidth(): Int = view.width

    override fun viewHeight(): Int = view.height

    override fun dp(value: Float): Float = dpFn(value)

    override fun sp(value: Float): Float = spFn(value)

    override fun density(): Float = view.resources.displayMetrics.density

    override fun screenWidthPx(): Int = view.resources.displayMetrics.widthPixels

    override fun screenHeightPx(): Int = view.resources.displayMetrics.heightPixels

    override fun viewLocationOnScreen(): IntArray = IntArray(2).also { view.getLocationOnScreen(it) }

    override fun invalidate() = view.invalidate()

    @Suppress("DEPRECATION")
    override fun invalidatePanel(rect: RectF) {
        val target = if (rect.isEmpty) indexPanelContentRectProvider?.invoke() ?: rect else rect
        if (target.isEmpty) {
            view.invalidate()
            return
        }
        val pad = dp(4f).toInt()
        view.invalidate(
            (target.left - pad).toInt().coerceAtLeast(0),
            (target.top - pad).toInt().coerceAtLeast(0),
            (target.right + pad).toInt().coerceAtMost(view.width.coerceAtLeast(1)),
            (target.bottom + pad).toInt().coerceAtMost(view.height.coerceAtLeast(1)),
        )
    }

    @Suppress("DEPRECATION")
    override fun invalidatePartial(left: Int, top: Int, right: Int, bottom: Int) =
        view.invalidate(left, top, right, bottom)

    override fun post(action: () -> Unit) {
        view.post(action)
    }

    override fun postDelayed(runnable: Runnable, delayMs: Long) {
        view.postDelayed(runnable, delayMs)
    }

    override fun removeCallbacks(runnable: Runnable) {
        view.removeCallbacks(runnable)
    }

    override fun requestFocus() {
        view.requestFocus()
    }

    override fun runAfterLayout(block: () -> Unit) = runAfterLayoutFn(block)

    override fun anchorLocalY(rawY: Float): Float {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return rawY - loc[1]
    }

    override fun indexSession(): SlideAlongRailSession = indexSession

    override fun iconFor(app: AppInfo): Bitmap = iconForFn(app)

    override fun appRepository(): AppRepository = appRepository

    override fun hapticTick() = HapticHelper.appTick(view, settings())

    override fun hapticLongThreshold() = HapticHelper.longThreshold(view, settings())

    override fun hapticConfirm() = HapticHelper.confirmLaunch(view, settings())

    override fun hapticConfirmLaunch() = HapticHelper.confirmLaunch(view, settings())

    override fun startPanelExitAnimation(onEnd: () -> Unit) =
        panelEnterAnimator.startExit(gestureSession.panelMode(), onEnd)

    override fun notifyPresentationTouchRequirementChanged() =
        notifyPresentationTouchRequirementChangedFn()

    override fun onQuickLauncherPanelItemsPersist(panelId: String, items: List<QuickLauncherItem>) =
        onQuickLauncherPanelItemsPersistFn(panelId, items)

    override fun onOverlayWindowSuspend() = onOverlayWindowSuspendFn()

    override fun onOverlayWindowResume() = onOverlayWindowResumeFn()

    override fun onAdjustPanelDismiss() = onAdjustPanelDismissFn()

    override fun onSessionStart() = onSessionStartFn()

    override fun notifyOverlayLayoutIfNeeded() = notifyOverlayLayoutIfNeededFn()

    override fun onShellCommandsPersist(commands: List<ShellCommand>) =
        onShellCommandsPersistFn(commands)

    override fun onShellPanelFocusChange(needsFocus: Boolean) =
        onShellPanelFocusChangeFn(needsFocus)

    override fun onShellPanelAuxiliaryPrepare() = onShellPanelAuxiliaryPrepareFn()

    override fun onShellPanelAuxiliaryDismiss() = onShellPanelAuxiliaryDismissFn()

    override fun clearEdgeCaptureTouchActive() = clearEdgeCaptureTouchActiveFn()

    override fun onInitiatingEdgeGestureReleased() = onInitiatingEdgeGestureReleasedFn()

    override fun setPanelPresentationFocus(needsFocus: Boolean) =
        onShellPanelFocusChangeFn(needsFocus)
}
