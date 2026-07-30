package com.slideindex.app.overlay.corner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.isCornerInnerZoneOnly
import com.slideindex.app.gesture.isEffective
import com.slideindex.app.service.CornerGestureSlotPickTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.HapticHelper

@SuppressLint("ViewConstructor")
internal class CornerGestureOverlayView(
    context: Context,
    private val appRepository: AppRepository,
    private val onSessionEnd: () -> Unit,
    private val onReleaseCapture: () -> Unit = {},
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit,
) : View(context) {

    private enum class SessionMode { NORMAL, EDIT }

    private var settings = AppSettings()
    private var cornerSettings = CornerGestureSettings()
    private var density = 1f

    private var activeAnchor: CornerAnchor? = null
    private var wheelPinned = false
    private var sessionMode = SessionMode.NORMAL
    private var menuActive = false
    private var activated = false
    private var startRawX = 0f
    private var startRawY = 0f
    private var highlightedSlot = -1
    private var lastHapticHighlightedSlot = -1
    private var highlightedEditButton = false
    private var activeLayerCount = 1
    private var maxInwardSlop = 0f
    private var menuRevealProgress = 0f
    private var revealAnimator: ValueAnimator? = null
    private var editModeEntered = false
    private var slotPressIndex = -1
    private var slotPressDownTime = 0L
    private var slotLongPressArmed = false
    private var slotLongPressTrackingIndex = -1
    private var slotLongPressRunnable: Runnable? = null

    private val actionExecutor = ActionExecutor(
        context = context,
        appRepository = appRepository,
        onShellCommandsPersist = onShellCommandsPersist,
    )

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
    }

    fun applySettings(settings: AppSettings, density: Float) {
        this.settings = settings
        this.cornerSettings = settings.cornerGestureSettings
        this.density = density
    }

    fun isSessionActive(): Boolean = activeAnchor != null && !wheelPinned

    fun isWheelPinned(): Boolean = wheelPinned && activeAnchor != null

    fun shouldPassthroughTap(): Boolean =
        activeAnchor != null && !wheelPinned && !menuActive && !activated

    fun isOverlayVisible(): Boolean = activeAnchor != null

    fun dismissPinnedWheel() = dismissWheel()

    fun beginSession(anchor: CornerAnchor, event: MotionEvent) {
        if (wheelPinned) dismissWheel()
        activeAnchor = anchor
        wheelPinned = false
        sessionMode = SessionMode.NORMAL
        menuActive = false
        activated = false
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 1
        maxInwardSlop = 0f
        menuRevealProgress = 0f
        editModeEntered = false
        cancelSlotLongPress()
        revealAnimator?.cancel()
        startRawX = event.rawX
        startRawY = event.rawY
    }

    fun handleTouch(event: MotionEvent): Boolean {
        val anchor = activeAnchor ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val slopPx = cornerSettings.triggerSlopDp * density * landscapeSlopScale()
                val inward = CornerRadialMenuGeometry.inwardSlopDistance(
                    anchor = anchor,
                    startX = startRawX,
                    startY = startRawY,
                    currentX = event.rawX,
                    currentY = event.rawY,
                )
                maxInwardSlop = maxOf(maxInwardSlop, inward)
                if (!menuActive && maxInwardSlop >= slopPx) {
                    menuActive = true
                    activated = true
                    if (!isProgressiveReveal()) {
                        activeLayerCount = 3
                    }
                    HapticHelper.gestureStart(this, settings)
                    animateMenuReveal()
                }
                if (menuActive) {
                    updateInteraction(anchor, event.rawX, event.rawY, event.eventTime)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!menuActive) {
                    dismissWheel()
                    return activated
                }
                return handleRelease(anchor, event, fromPinned = false)
            }
        }
        return false
    }

    fun cancelSession() {
        revealAnimator?.cancel()
        clearSessionState()
    }

    private fun handleRelease(anchor: CornerAnchor, event: MotionEvent, fromPinned: Boolean): Boolean {
        val rawX = event.rawX
        val rawY = event.rawY
        val mode = sessionMode
        val (anchorX, anchorY) = anchorCenter(anchor)
        val onEditButton = CornerRadialMenuGeometry.isEditButtonHit(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            settings = cornerSettings,
            fingerX = rawX,
            fingerY = rawY,
            density = density,
        )
        val slot = resolveSlotAt(anchor, rawX, rawY)
        val innerRelease = isFingerInInnerZone(anchor, rawX, rawY)
        val outsideRelease = isFingerOutsideWheel(anchor, rawX, rawY)
        val innerAction = cornerSettings.innerZoneAction

        when {
            onEditButton && !fromPinned -> {
                pinWheelForEdit(anchor)
                return true
            }
            mode == SessionMode.EDIT && slot >= 0 -> {
                openSlotPicker(anchor, slot)
                dismissWheel()
                return true
            }
            innerRelease && innerAction is GestureAction.CornerInnerPinWheel -> {
                pinWheel(anchor)
                return true
            }
            innerRelease && innerAction is GestureAction.CornerInnerCancel -> {
                dismissWheel()
                return activated || fromPinned
            }
            innerRelease && innerAction.isCornerInnerZoneOnly() -> {
                dismissWheel()
                return activated || fromPinned
            }
            innerRelease && innerAction.isEffective() -> {
                executeAction(innerAction, anchor, rawX, rawY)
                dismissWheel()
                return true
            }
            fromPinned && outsideRelease -> {
                dismissWheel()
                return true
            }
            fromPinned && innerRelease -> {
                dismissWheel()
                return true
            }
            mode == SessionMode.NORMAL && slot >= 0 -> {
                val action = cornerSettings.slotsFor(anchor).getOrElse(slot) { GestureAction.None }
                if (action.isEffective()) {
                    val longPress = slotLongPressTriggered(event, slot, action)
                    if (longPress) {
                        HapticHelper.confirmLaunch(this, settings)
                    }
                    executeAction(action, anchor, rawX, rawY, longPressArmed = longPress)
                }
                cancelSlotLongPress()
                dismissWheel()
                return activated || fromPinned
            }
            fromPinned -> {
                dismissWheel()
                return true
            }
            outsideRelease && cornerSettings.cancelOutsideWheel -> {
                dismissWheel()
                return activated
            }
            else -> {
                dismissWheel()
                return activated
            }
        }
    }

    private fun pinWheel(anchor: CornerAnchor) {
        revealAnimator?.cancel()
        wheelPinned = true
        activeAnchor = anchor
        sessionMode = SessionMode.NORMAL
        menuActive = true
        activated = false
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 3
        menuRevealProgress = 1f
        editModeEntered = false
        maxInwardSlop = 0f
        cancelSlotLongPress()
        invalidate()
    }

    private fun pinWheelForEdit(anchor: CornerAnchor) {
        revealAnimator?.cancel()
        wheelPinned = true
        activeAnchor = anchor
        sessionMode = SessionMode.EDIT
        menuActive = true
        activated = true
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 3
        menuRevealProgress = 1f
        editModeEntered = true
        maxInwardSlop = 0f
        cancelSlotLongPress()
        invalidate()
    }

    private fun dismissWheel() {
        revealAnimator?.cancel()
        clearSessionState()
        onSessionEnd()
    }

    private fun clearSessionState() {
        activeAnchor = null
        wheelPinned = false
        sessionMode = SessionMode.NORMAL
        menuActive = false
        activated = false
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 1
        menuRevealProgress = 0f
        editModeEntered = false
        cancelSlotLongPress()
        invalidate()
    }

    private fun animateMenuReveal() {
        revealAnimator?.cancel()
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                menuRevealProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lastHapticHighlightedSlot = -1
                }
            })
            start()
        }
    }

    private fun isMenuRevealing(): Boolean = menuRevealProgress < 1f

    private fun isProgressiveReveal(): Boolean =
        cornerSettings.progressiveLayers &&
            sessionMode == SessionMode.NORMAL &&
            !wheelPinned

    private fun menuActiveLayerCount(): Int = when {
        wheelPinned || sessionMode == SessionMode.EDIT -> 3
        !isProgressiveReveal() -> 3
        else -> activeLayerCount
    }

    private fun updateActiveLayerCount(anchor: CornerAnchor, anchorX: Float, anchorY: Float, rawX: Float, rawY: Float) {
        if (!isProgressiveReveal()) {
            activeLayerCount = 3
            return
        }
        if (isFingerInInnerZone(anchor, rawX, rawY)) return
        activeLayerCount = CornerWheelLayout.activeLayerCount(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = rawX,
            fingerY = rawY,
            settings = cornerSettings,
            density = density,
            progressive = true,
        )
    }

    private fun updateInteraction(anchor: CornerAnchor, rawX: Float, rawY: Float, eventTime: Long) {
        val (anchorX, anchorY) = anchorCenter(anchor)
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val editMode = sessionMode == SessionMode.EDIT
        val slots = cornerSettings.slotsFor(anchor)

        updateActiveLayerCount(anchor, anchorX, anchorY, rawX, rawY)

        val onEditButton = CornerRadialMenuGeometry.isEditButtonHit(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            settings = cornerSettings,
            fingerX = rawX,
            fingerY = rawY,
            density = density,
        )
        highlightedEditButton = onEditButton
        if (onEditButton) {
            highlightedSlot = -1
            lastHapticHighlightedSlot = -1
            syncSlotPressTracking(-1, eventTime)
            if (!editModeEntered) {
                sessionMode = SessionMode.EDIT
                editModeEntered = true
                activeLayerCount = 3
                HapticHelper.gestureStart(this, settings)
            }
            return
        }

        val slot = CornerRadialMenuGeometry.slotIndexAt(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = rawX,
            fingerY = rawY,
            settings = cornerSettings,
            density = density,
            slots = slots,
            editMode = editMode,
            activeLayerCount = 3,
            revealProgress = menuRevealProgress,
        )
        if (slot != null) {
            highlightedSlot = slot
            if (isProgressiveReveal()) {
                activeLayerCount = maxOf(activeLayerCount, CornerRadialMenuCodec.layerOf(slot) + 1)
            }
            maybeHapticForSlotChange(slot)
            syncSlotPressTracking(slot, eventTime)
            return
        }

        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        syncSlotPressTracking(-1, eventTime)
        if (cornerSettings.cancelOutsideWheel &&
            !editMode &&
            CornerWheelLayout.isOutsideWheel(
                anchor = anchor,
                anchorX = anchorX,
                anchorY = anchorY,
                fingerX = rawX,
                fingerY = rawY,
                screenWidth = screenW,
                screenHeight = screenH,
                settings = cornerSettings,
                density = density,
            )
        ) {
            highlightedEditButton = false
            return
        }

        if (isFingerInInnerZone(anchor, rawX, rawY)) {
            return
        }
    }

    private fun releaseLayerCount(): Int {
        var count = menuActiveLayerCount()
        if (isProgressiveReveal() && highlightedSlot >= 0) {
            count = maxOf(count, CornerRadialMenuCodec.layerOf(highlightedSlot) + 1)
        }
        return count
    }

    private fun maybeHapticForSlotChange(slot: Int) {
        if (isMenuRevealing()) return
        if (slot == lastHapticHighlightedSlot) return
        lastHapticHighlightedSlot = slot
        if (slot < 0 || !cornerSettings.slotHapticEnabled) return
        HapticHelper.appTick(this, settings)
    }

    private fun resolveSlotAt(anchor: CornerAnchor, rawX: Float, rawY: Float): Int {
        val (anchorX, anchorY) = anchorCenter(anchor)
        return CornerRadialMenuGeometry.slotIndexAt(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = rawX,
            fingerY = rawY,
            settings = cornerSettings,
            density = density,
            slots = cornerSettings.slotsFor(anchor),
            editMode = sessionMode == SessionMode.EDIT,
            activeLayerCount = releaseLayerCount(),
            revealProgress = menuRevealProgress,
        ) ?: -1
    }

    private fun isFingerInInnerZone(anchor: CornerAnchor, rawX: Float, rawY: Float): Boolean {
        val (anchorX, anchorY) = anchorCenter(anchor)
        return CornerWheelLayout.isInInnerZone(anchorX, anchorY, rawX, rawY, cornerSettings, density)
    }

    private fun isFingerOutsideWheel(anchor: CornerAnchor, rawX: Float, rawY: Float): Boolean {
        val (anchorX, anchorY) = anchorCenter(anchor)
        return CornerWheelLayout.isOutsideWheel(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = rawX,
            fingerY = rawY,
            screenWidth = width.toFloat(),
            screenHeight = height.toFloat(),
            settings = cornerSettings,
            density = density,
        )
    }

    private fun executeAction(
        action: GestureAction,
        anchor: CornerAnchor,
        rawX: Float,
        rawY: Float,
        longPressArmed: Boolean = false,
    ) {
        actionExecutor.execute(
            action = action,
            settings = settings,
            longPressArmed = longPressArmed,
            anchorRawX = rawX,
            anchorRawY = rawY,
            panelSide = anchor.toPanelSide(),
        )
    }

    private fun syncSlotPressTracking(slot: Int, eventTime: Long) {
        if (isMenuRevealing()) {
            if (slot < 0) {
                cancelSlotLongPress()
                slotPressIndex = -1
                slotPressDownTime = 0L
            }
            return
        }
        if (slot >= 0) {
            if (slot != slotPressIndex) {
                slotPressIndex = slot
                slotPressDownTime = eventTime
                scheduleSlotLongPress(slot)
            }
        } else {
            cancelSlotLongPress()
            slotPressIndex = -1
            slotPressDownTime = 0L
        }
    }

    private fun scheduleSlotLongPress(slot: Int) {
        cancelSlotLongPress()
        if (isMenuRevealing()) return
        if (!settings.launchPolicyLongPressEligible()) return
        val anchor = activeAnchor ?: return
        val action = cornerSettings.slotsFor(anchor).getOrElse(slot) { GestureAction.None }
        if (!action.usesLaunchPolicy()) return
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
    }

    private fun slotLongPressTriggered(event: MotionEvent, slot: Int, action: GestureAction): Boolean {
        if (slotLongPressArmed) return true
        if (!settings.launchPolicyLongPressEligible() || !action.usesLaunchPolicy()) return false
        if (slotPressIndex < 0 || slotPressIndex != slot) return false
        return event.eventTime - slotPressDownTime >= settings.effectiveLongPressDurationMs()
    }

    private fun GestureAction.usesLaunchPolicy(): Boolean =
        this is GestureAction.LaunchApp || this is GestureAction.LaunchShortcut

    private fun openSlotPicker(anchor: CornerAnchor, slotIndex: Int) {
        val corner = when (anchor) {
            CornerAnchor.LEFT -> CornerGestureSlotPickTrampolineActivity.CORNER_LEFT
            CornerAnchor.RIGHT -> CornerGestureSlotPickTrampolineActivity.CORNER_RIGHT
        }
        val intent = CornerGestureSlotPickTrampolineActivity.createIntent(context, corner, slotIndex).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun anchorCenter(anchor: CornerAnchor): Pair<Float, Float> = when (anchor) {
        CornerAnchor.LEFT -> 0f to height.toFloat()
        CornerAnchor.RIGHT -> width.toFloat() to height.toFloat()
    }

    fun handlePinnedTouchEvent(event: MotionEvent): Boolean = handlePinnedTouch(event)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (wheelPinned) {
            return handlePinnedTouch(event)
        }
        if (!isSessionActive()) return false
        return handleTouch(event)
    }

    private fun handlePinnedTouch(event: MotionEvent): Boolean {
        val anchor = activeAnchor ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateInteraction(anchor, event.rawX, event.rawY, event.eventTime)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateInteraction(anchor, event.rawX, event.rawY, event.eventTime)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return handleRelease(anchor, event, fromPinned = true)
            }
        }
        return false
    }

    private fun landscapeSlopScale(): Float {
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (landscape && cornerSettings.landscapePreventFalseTouch) 1.35f else 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!menuActive) return
        val anchor = activeAnchor ?: return
        val (anchorX, anchorY) = anchorCenter(anchor)
        CornerRadialMenuRenderer.draw(
            context = context,
            canvas = canvas,
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            settings = cornerSettings,
            slots = cornerSettings.slotsFor(anchor),
            highlightedSlot = highlightedSlot,
            highlightedEditButton = highlightedEditButton,
            editMode = sessionMode == SessionMode.EDIT,
            activeLayerCount = CornerRadialMenuGeometry.displayLayerCount(
                activeLayerCount = menuActiveLayerCount(),
                highlightedSlot = highlightedSlot,
            ),
            density = density,
            revealProgress = menuRevealProgress,
        )
    }
}
