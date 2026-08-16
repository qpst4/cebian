package com.slideindex.app.overlay.appswitcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.HoneycombRuntimeTarget
import com.slideindex.app.overlay.layout.AppSwitcherLayoutEngine
import com.slideindex.app.overlay.layout.AppSwitcherPanelLayout
import com.slideindex.app.overlay.layout.AppSwitcherSide
import com.slideindex.app.service.AppSwitcherSlotPickTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppSwitcherDisplaySettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.util.HapticHelper

@SuppressLint("ViewConstructor")
internal class AppSwitcherOverlayView(
    context: Context,
    private val onLaunch: (HoneycombRuntimeTarget, Long) -> Unit,
    private val onClosed: () -> Unit,
    private val onMenuVisualActiveChange: (Boolean) -> Unit = {},
) : View(context) {

    private enum class SessionMode { NORMAL, EDIT }

    private var settings = AppSettings()
    private var display = AppSwitcherDisplaySettings()
    private var density = 1f
    private var targets: List<HoneycombRuntimeTarget> = emptyList()
    private var appsByPackage: Map<String, AppInfo> = emptyMap()

    private var activeSide: AppSwitcherSide? = null
    private var anchorRawY = 0f
    private var panelPinned = false
    private var sessionMode = SessionMode.NORMAL
    private var sessionActive = false
    private var externalTracking = false
    private var highlightedSlot = -1
    private var highlightedToolbarButton: AppSwitcherPinToolbarGeometry.Button? = null
    private var lastHapticHighlightedSlot = -1
    private var menuRevealProgress = 0f
    private var revealAnimator: ValueAnimator? = null
    private var panelLayout: AppSwitcherPanelLayout? = null
    private var slotPressIndex = -1
    private var slotPressDownTime = 0L
    private var slotLongPressArmed = false
    private var slotLongPressTrackingIndex = -1
    private var slotLongPressRunnable: Runnable? = null
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private var menuVisualActive = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
    }

    fun configure(
        settings: AppSettings,
        targets: List<HoneycombRuntimeTarget>,
        appsByPackage: Map<String, AppInfo>,
        side: AppSwitcherSide,
        anchorRawY: Float,
        externalTracking: Boolean,
        density: Float,
    ) {
        this.settings = settings
        this.display = settings.appSwitcherDisplay
        this.targets = targets
        this.appsByPackage = appsByPackage
        this.activeSide = side
        this.anchorRawY = anchorRawY
        this.externalTracking = externalTracking
        this.density = density
        rebuildLayout()
    }

    fun refreshTargets(targets: List<HoneycombRuntimeTarget>, appsByPackage: Map<String, AppInfo>) {
        this.targets = targets
        this.appsByPackage = appsByPackage
        rebuildLayout()
        invalidate()
    }

    fun isVisibleSession(): Boolean = sessionActive

    fun isPinned(): Boolean = panelPinned && sessionActive

    fun beginSession() {
        sessionActive = true
        panelPinned = false
        sessionMode = SessionMode.NORMAL
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedToolbarButton = null
        menuRevealProgress = if (externalTracking) 1f else 0f
        cancelSlotLongPress()
        revealAnimator?.cancel()
        if (!externalTracking) {
            animateMenuReveal()
        } else {
            setMenuVisualActive(true)
            HapticHelper.gestureStart(this, settings)
        }
        invalidate()
    }

    fun onExternalMove(rawX: Float, rawY: Float) {
        if (!sessionActive) return
        updateInteraction(rawX, rawY, System.currentTimeMillis())
        invalidate()
    }

    fun onExternalUp(rawX: Float, rawY: Float, cancelled: Boolean) {
        if (!sessionActive) return
        if (cancelled) {
            dismissPanel()
            return
        }
        handleRelease(rawX, rawY, System.currentTimeMillis(), fromPinned = false)
    }

    fun onExternalCancel() {
        dismissPanel()
    }

    fun enableDirectTouch() {
        externalTracking = false
    }

    fun dismissNow() {
        dismissPanel()
    }

    private fun rebuildLayout() {
        val side = activeSide ?: return
        val metrics = resources.displayMetrics
        val slotCount = resolveSlotCount()
        panelLayout = AppSwitcherRenderer.buildLayout(
            slotCount = slotCount,
            side = side,
            anchorRawY = anchorRawY,
            screenWidth = metrics.widthPixels.toFloat(),
            screenHeight = metrics.heightPixels.toFloat(),
            display = display,
            density = density,
        )
    }

    private fun resolveSlotCount(): Int {
        val configured = targets.size
        return if (sessionMode == SessionMode.EDIT) {
            configured.coerceAtLeast(MIN_EDIT_SLOTS).coerceAtMost(AppSwitcherDisplaySettings.MAX_SLOTS)
        } else {
            configured.coerceAtLeast(1)
        }
    }

    private fun updateInteraction(rawX: Float, rawY: Float, eventTime: Long) {
        val layout = panelLayout ?: return
        highlightedToolbarButton = if (panelPinned) {
            AppSwitcherPinToolbarGeometry.hitButton(layout, rawX, rawY, density)
        } else {
            null
        }
        val slot = AppSwitcherLayoutEngine.slotIndexAt(layout, rawX, rawY)
        if (slot != highlightedSlot) {
            highlightedSlot = slot
            if (display.slotHaptic && slot >= 0 && slot != lastHapticHighlightedSlot) {
                lastHapticHighlightedSlot = slot
                HapticHelper.appTick(this, settings)
            }
        }
        if (slot >= 0 && slotPressIndex != slot) {
            slotPressIndex = slot
            slotPressDownTime = eventTime
            scheduleSlotLongPress(slot)
        } else if (slot < 0) {
            cancelSlotLongPress()
        }
    }

    private fun handleRelease(rawX: Float, rawY: Float, eventTime: Long, fromPinned: Boolean): Boolean {
        val layout = panelLayout ?: return false
        updateInteraction(rawX, rawY, eventTime)
        val toolbarButton = AppSwitcherPinToolbarGeometry.hitButton(layout, rawX, rawY, density)
        val slot = AppSwitcherLayoutEngine.slotIndexAt(layout, rawX, rawY)
        val outside = AppSwitcherLayoutEngine.isOutsidePanel(layout, rawX, rawY, layout.itemSizePx * 0.35f) &&
            toolbarButton == null

        when {
            toolbarButton == AppSwitcherPinToolbarGeometry.Button.EDIT -> {
                enterEditMode()
                return true
            }
            toolbarButton == AppSwitcherPinToolbarGeometry.Button.DISMISS -> {
                dismissPanel()
                return true
            }
            sessionMode == SessionMode.EDIT && slot >= 0 -> {
                val target = targets.getOrNull(slot)
                if (target == null) {
                    openSlotPicker(slot)
                    dismissPanel()
                } else {
                    launchSlot(slot, eventTime)
                }
                return true
            }
            fromPinned && outside && display.emptyTapClose -> {
                dismissPanel()
                return true
            }
            fromPinned && slot >= 0 -> {
                launchSlot(slot, eventTime)
                return true
            }
            !fromPinned && slot >= 0 -> {
                launchSlot(slot, eventTime)
                return true
            }
            !fromPinned && display.pinOnRelease -> {
                pinPanel()
                return true
            }
            else -> {
                dismissPanel()
                return true
            }
        }
    }

    private fun launchSlot(slot: Int, eventTime: Long) {
        val target = targets.getOrNull(slot) ?: return
        val pressDuration = (eventTime - slotPressDownTime).coerceAtLeast(0L)
        val longPressArmed = slotLongPressArmed ||
            (settings.launchPolicyLongPressEligible() &&
                pressDuration >= settings.effectiveLongPressDurationMs())
        cancelSlotLongPress()
        dismissPanel()
        onLaunch(target, pressDuration)
        if (longPressArmed) {
            // longPressArmed is resolved by window host via press duration
        }
    }

    private fun pinPanel() {
        revealAnimator?.cancel()
        panelPinned = true
        sessionActive = true
        sessionMode = SessionMode.NORMAL
        setMenuVisualActive(true)
        menuRevealProgress = 1f
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedToolbarButton = null
        cancelSlotLongPress()
        if (!externalTracking) {
            invalidate()
            return
        }
        externalTracking = false
        invalidate()
    }

    private fun enterEditMode() {
        panelPinned = true
        sessionMode = SessionMode.EDIT
        rebuildLayout()
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        cancelSlotLongPress()
        invalidate()
    }

    private fun dismissPanel() {
        revealAnimator?.cancel()
        clearSessionState()
        onClosed()
    }

    private fun clearSessionState() {
        sessionActive = false
        panelPinned = false
        sessionMode = SessionMode.NORMAL
        setMenuVisualActive(false)
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedToolbarButton = null
        menuRevealProgress = 0f
        cancelSlotLongPress()
        invalidate()
    }

    private fun setMenuVisualActive(active: Boolean) {
        if (menuVisualActive == active) return
        menuVisualActive = active
        onMenuVisualActiveChange(active)
    }

    private fun animateMenuReveal() {
        revealAnimator?.cancel()
        revealAnimator = ValueAnimator.ofFloat(menuRevealProgress, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                menuRevealProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    setMenuVisualActive(true)
                    HapticHelper.gestureStart(this@AppSwitcherOverlayView, settings)
                }
            })
            start()
        }
    }

    private fun scheduleSlotLongPress(slot: Int) {
        cancelSlotLongPress()
        if (!settings.launchPolicyLongPressEligible()) return
        val target = targets.getOrNull(slot) ?: return
        if (target.item.type != com.slideindex.app.launcher.QuickLauncherItemType.APP) return
        slotLongPressTrackingIndex = slot
        val runnable = Runnable {
            if (highlightedSlot == slotLongPressTrackingIndex && slotLongPressTrackingIndex >= 0) {
                slotLongPressArmed = true
                HapticHelper.longThreshold(this, settings)
                invalidate()
            }
        }
        slotLongPressRunnable = runnable
        postDelayed(runnable, settings.effectiveLongPressDurationMs().toLong())
    }

    private fun cancelSlotLongPress() {
        slotLongPressRunnable?.let { removeCallbacks(it) }
        slotLongPressRunnable = null
        slotLongPressTrackingIndex = -1
        slotLongPressArmed = false
        slotPressIndex = -1
    }

    private fun openSlotPicker(slotIndex: Int) {
        val side = when (activeSide) {
            AppSwitcherSide.LEFT -> AppSwitcherSlotPickTrampolineActivity.SIDE_LEFT
            AppSwitcherSide.RIGHT -> AppSwitcherSlotPickTrampolineActivity.SIDE_RIGHT
            null -> AppSwitcherSlotPickTrampolineActivity.SIDE_LEFT
        }
        val intent = AppSwitcherSlotPickTrampolineActivity.createIntent(context, side, slotIndex).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!sessionActive) return false
        val rawX = event.rawX
        val rawY = event.rawY
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateInteraction(rawX, rawY, event.eventTime)
                invalidate()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                updateInteraction(rawX, rawY, event.eventTime)
                invalidate()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = handleRelease(rawX, rawY, event.eventTime, fromPinned = panelPinned)
                if (handled) performClick()
                handled
            }
            else -> false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = panelLayout ?: return
        drawBackgroundMask(canvas, menuRevealProgress)
        AppSwitcherRenderer.draw(
            context = context,
            canvas = canvas,
            layout = layout,
            display = display,
            targets = targets,
            editMode = sessionMode == SessionMode.EDIT,
            highlightedSlot = highlightedSlot,
            highlightedToolbarButton = highlightedToolbarButton,
            panelPinned = panelPinned,
            density = density,
            revealProgress = menuRevealProgress,
            appsByPackage = appsByPackage,
            activityShortcuts = settings.activityShortcuts,
            shellCommands = settings.shellCommands,
        )
    }

    private fun drawBackgroundMask(canvas: Canvas, progress: Float) {
        if (progress <= 0.01f) return
        val dim = display.dimPercent.coerceIn(
            AppSwitcherDisplaySettings.MIN_DIM_PERCENT,
            AppSwitcherDisplaySettings.MAX_DIM_PERCENT,
        )
        val alpha = (255f * dim / 100f * progress).toInt().coerceIn(0, 255)
        dimPaint.alpha = alpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }

    companion object {
        private const val MIN_EDIT_SLOTS = 8
    }
}
