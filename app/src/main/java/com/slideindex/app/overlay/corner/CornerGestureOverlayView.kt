package com.slideindex.app.overlay.corner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
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
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.gesturepicker.launchShortcutDisplayLabel
import com.slideindex.app.util.HapticHelper
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
internal class CornerGestureOverlayView(
    context: Context,
    private val appRepository: AppRepository,
    private val onSessionEnd: () -> Unit,
    private val onReleaseCapture: () -> Unit = {},
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit,
    private val onMenuVisualActiveChange: (Boolean) -> Unit = {},
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
    private var menuActivationRadDist = 0f
    private var maxInwardSlop = 0f
    private var menuRevealProgress = 0f
    private var revealAnimator: ValueAnimator? = null
    private var editModeEntered = false
    private var slotPressIndex = -1
    private var slotPressDownTime = 0L
    private var slotLongPressArmed = false
    private var slotLongPressTrackingIndex = -1
    private var slotLongPressRunnable: Runnable? = null
    private var shortcutSubMenuVisible = false
    private var shortcutSubMenuSlot = -1
    private var shortcutSubMenuLayout: CornerShortcutSubMenuLayout? = null
    private var highlightedShortcutIndex = -1
    private var lastHapticHighlightedShortcutIndex = -1
    private var shortcutSubMenuRevealProgress = 0f
    private var subMenuRevealAnimator: ValueAnimator? = null
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private var menuVisualActive = false

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
        if (menuVisualActive) {
            onMenuVisualActiveChange(true)
        }
        invalidate()
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
        menuActivationRadDist = 0f
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
                    } else {
                        val (anchorX, anchorY) = anchorCenter(anchor)
                        menuActivationRadDist = hypot(event.rawX - anchorX, event.rawY - anchorY)
                    }
                    setMenuVisualActive(true)
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
        val resolvedSlot = resolveSlotAt(anchor, rawX, rawY)
        val slot = when {
            shortcutSubMenuVisible && shortcutSubMenuSlot >= 0 && resolvedSlot < 0 -> shortcutSubMenuSlot
            else -> resolvedSlot
        }
        val innerRelease = isFingerInInnerZone(anchor, rawX, rawY)
        val outsideRelease = isFingerOutsideWheel(anchor, rawX, rawY)
        val innerAction = cornerSettings.innerZoneAction

        when {
            onEditButton -> {
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
            mode == SessionMode.NORMAL && isFingerOnShortcutSubMenu(rawX, rawY) -> {
                resolveShortcutSubMenuRelease(anchor, rawX, rawY)?.let { shortcut ->
                    executeAction(shortcut, anchor, rawX, rawY, longPressArmed = false)
                }
                cancelSlotLongPress()
                hideShortcutSubMenu()
                dismissWheel()
                return activated || fromPinned
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
        setMenuVisualActive(true)
        activated = false
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 3
        menuRevealProgress = 1f
        editModeEntered = false
        maxInwardSlop = 0f
        cancelSlotLongPress()
        hideShortcutSubMenu()
        invalidate()
    }

    private fun pinWheelForEdit(anchor: CornerAnchor) {
        revealAnimator?.cancel()
        wheelPinned = true
        activeAnchor = anchor
        sessionMode = SessionMode.EDIT
        menuActive = true
        setMenuVisualActive(true)
        activated = true
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 3
        menuRevealProgress = 1f
        editModeEntered = true
        maxInwardSlop = 0f
        cancelSlotLongPress()
        hideShortcutSubMenu()
        invalidate()
    }

    private fun hideShortcutSubMenu() {
        subMenuRevealAnimator?.cancel()
        subMenuRevealAnimator = null
        shortcutSubMenuVisible = false
        shortcutSubMenuSlot = -1
        shortcutSubMenuLayout = null
        shortcutSubMenuRevealProgress = 0f
        highlightedShortcutIndex = -1
        lastHapticHighlightedShortcutIndex = -1
    }

    private fun showShortcutSubMenu(anchor: CornerAnchor, slot: Int) {
        shortcutSubMenuVisible = true
        shortcutSubMenuSlot = slot
        rebuildShortcutSubMenuLayout(anchor, slot)
        shortcutSubMenuRevealProgress = 0f
        subMenuRevealAnimator?.cancel()
        subMenuRevealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                shortcutSubMenuRevealProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun fingerSubMenuSlopPx(): Float = 10f * density

    private fun isFingerOnShortcutSubMenu(rawX: Float, rawY: Float): Boolean =
        shortcutSubMenuVisible &&
            shortcutSubMenuLayout?.containsFinger(rawX, rawY, fingerSubMenuSlopPx()) == true

    private fun resolveShortcutSubMenuRelease(
        anchor: CornerAnchor,
        rawX: Float,
        rawY: Float,
    ): GestureAction.LaunchShortcut? {
        if (!shortcutSubMenuVisible || shortcutSubMenuSlot < 0) return null
        val layout = shortcutSubMenuLayout ?: return null
        val index = layout.indexAt(rawX, rawY, fingerSubMenuSlopPx())
        if (index < 0) return null
        return cornerSettings.slotSubMenuFor(anchor, shortcutSubMenuSlot).items.getOrNull(index)
    }

    private fun resolveSlotAtFinger(
        anchor: CornerAnchor,
        rawX: Float,
        rawY: Float,
        editMode: Boolean,
        slots: List<GestureAction>,
    ): Int? = CornerRadialMenuGeometry.slotIndexAt(
        anchor = anchor,
        anchorX = anchorCenter(anchor).first,
        anchorY = anchorCenter(anchor).second,
        fingerX = rawX,
        fingerY = rawY,
        settings = cornerSettings,
        density = density,
        slots = slots,
        editMode = editMode,
        activeLayerCount = menuActiveLayerCount(),
        revealProgress = menuRevealProgress,
    )

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
        setMenuVisualActive(false)
        activated = false
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        highlightedEditButton = false
        activeLayerCount = 1
        menuActivationRadDist = 0f
        menuRevealProgress = 0f
        editModeEntered = false
        cancelSlotLongPress()
        hideShortcutSubMenu()
        invalidate()
    }

    private fun setMenuVisualActive(active: Boolean) {
        if (menuVisualActive == active) return
        menuVisualActive = active
        onMenuVisualActiveChange(active)
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
        if (isFingerInInnerZone(anchor, rawX, rawY)) {
            activeLayerCount = 1
            return
        }
        activeLayerCount = CornerWheelLayout.activeLayerCount(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            fingerX = rawX,
            fingerY = rawY,
            settings = cornerSettings,
            density = density,
            progressive = true,
            activationRadDist = menuActivationRadDist,
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
            highlightedShortcutIndex = -1
            lastHapticHighlightedShortcutIndex = -1
            hideShortcutSubMenu()
            syncSlotPressTracking(-1, eventTime)
            if (!editModeEntered) {
                sessionMode = SessionMode.EDIT
                editModeEntered = true
                activeLayerCount = 3
                HapticHelper.gestureStart(this, settings)
            }
            return
        }

        if (shortcutSubMenuVisible && shortcutSubMenuSlot >= 0) {
            val layout = shortcutSubMenuLayout
            val slop = fingerSubMenuSlopPx()
            val slotAtFinger = resolveSlotAtFinger(anchor, rawX, rawY, editMode, slots)
            val onSubMenu = layout?.containsFinger(rawX, rawY, slop) == true
            val onSlot = slotAtFinger == shortcutSubMenuSlot
            if (onSubMenu || onSlot) {
                highlightedSlot = shortcutSubMenuSlot
                highlightedShortcutIndex = if (onSubMenu) {
                    layout?.indexAt(rawX, rawY, slop) ?: -1
                } else {
                    -1
                }
                if (highlightedShortcutIndex >= 0) {
                    maybeHapticForShortcutChange(highlightedShortcutIndex)
                }
                syncSlotPressTracking(shortcutSubMenuSlot, eventTime)
                return
            }
            hideShortcutSubMenu()
        }

        val slot = resolveSlotAtFinger(
            anchor = anchor,
            rawX = rawX,
            rawY = rawY,
            editMode = editMode,
            slots = slots,
        )
        if (slot != null) {
            if (shortcutSubMenuVisible && slot != shortcutSubMenuSlot) {
                hideShortcutSubMenu()
            }
            highlightedSlot = slot
            highlightedShortcutIndex = -1
            lastHapticHighlightedShortcutIndex = -1
            maybeHapticForSlotChange(slot)
            syncSlotPressTracking(slot, eventTime)
            return
        }

        highlightedSlot = if (shortcutSubMenuVisible && shortcutSubMenuSlot >= 0) {
            shortcutSubMenuSlot
        } else {
            -1
        }
        lastHapticHighlightedSlot = -1
        highlightedShortcutIndex = -1
        lastHapticHighlightedShortcutIndex = -1
        if (!shortcutSubMenuVisible) {
            syncSlotPressTracking(-1, eventTime)
        }
        val inSubMenu = shortcutSubMenuVisible &&
            shortcutSubMenuLayout?.containsFinger(rawX, rawY, fingerSubMenuSlopPx()) == true
        if (cornerSettings.cancelOutsideWheel &&
            !editMode &&
            !inSubMenu &&
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

    private fun maybeHapticForShortcutChange(index: Int) {
        if (isMenuRevealing()) return
        if (index == lastHapticHighlightedShortcutIndex) return
        lastHapticHighlightedShortcutIndex = index
        if (index < 0 || !cornerSettings.slotHapticEnabled) return
        HapticHelper.appTick(this, settings)
    }

    private fun rebuildShortcutSubMenuLayout(anchor: CornerAnchor, slot: Int) {
        val items = cornerSettings.slotSubMenuFor(anchor, slot).items
        if (items.isEmpty()) {
            shortcutSubMenuLayout = null
            return
        }
        val (anchorX, anchorY) = anchorCenter(anchor)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * density }
        val textWidths = items.map { shortcut ->
            val label = launchShortcutDisplayLabel(shortcut).ifBlank {
                gestureActionLabelText(context, shortcut)
            }
            textPaint.measureText(label)
        }
        shortcutSubMenuLayout = CornerShortcutSubMenuLayoutCalculator.build(
            anchor = anchor,
            anchorX = anchorX,
            anchorY = anchorY,
            slotIndex = slot,
            settings = cornerSettings,
            density = density,
            revealProgress = menuRevealProgress,
            items = items,
            textWidthsPx = textWidths,
            screenWidth = width.toFloat(),
        )
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
        if (shortcutSubMenuVisible &&
            shortcutSubMenuLayout?.containsFinger(rawX, rawY, fingerSubMenuSlopPx()) == true
        ) {
            return false
        }
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
            if (!shortcutSubMenuVisible) {
                slotPressIndex = -1
                slotPressDownTime = 0L
            }
        }
    }

    private fun scheduleSlotLongPress(slot: Int) {
        cancelSlotLongPress()
        if (isMenuRevealing()) return
        if (sessionMode == SessionMode.EDIT) return
        val anchor = activeAnchor ?: return
        val subMenu = cornerSettings.slotSubMenuFor(anchor, slot)
        val hasSubMenu = subMenu.isActive()
        val action = cornerSettings.slotsFor(anchor).getOrElse(slot) { GestureAction.None }
        val launchPolicyTrack = settings.launchPolicyLongPressEligible() && action.usesLaunchPolicy()
        if (!hasSubMenu && !launchPolicyTrack) return
        slotLongPressTrackingIndex = slot
        val runnable = Runnable {
            val tracking = slotLongPressTrackingIndex
            if (tracking < 0) return@Runnable
            if (hasSubMenu) {
                showShortcutSubMenu(anchor, tracking)
            }
            if (launchPolicyTrack) {
                slotLongPressArmed = true
            }
            HapticHelper.longThreshold(this, settings)
            invalidate()
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = when {
            wheelPinned -> handlePinnedTouch(event)
            !isSessionActive() -> false
            else -> handleTouch(event)
        }
        if (handled && event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return handled
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

    private fun drawBackgroundMask(canvas: Canvas, progress: Float) {
        if (cornerSettings.backgroundStyle == CornerGestureSettings.BACKGROUND_NONE) return
        if (progress <= 0.01f) return
        val dim = cornerSettings.dimPercent.coerceIn(
            CornerGestureSettings.MIN_DIM_PERCENT,
            CornerGestureSettings.MAX_DIM_PERCENT,
        )
        val alpha = (255f * dim / 100f * progress).toInt().coerceIn(0, 255)
        if (alpha <= 0) return
        dimPaint.alpha = alpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!menuActive) return
        val anchor = activeAnchor ?: return
        val (anchorX, anchorY) = anchorCenter(anchor)
        drawBackgroundMask(canvas, menuRevealProgress)
        val subMenuItems = if (shortcutSubMenuVisible && shortcutSubMenuSlot >= 0) {
            cornerSettings.slotSubMenuFor(anchor, shortcutSubMenuSlot).items
        } else {
            emptyList()
        }
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
            hintIconSizeDp = cornerSettings.selectedHintIconSizeDp,
            activityShortcuts = settings.activityShortcuts,
            shellCommands = settings.shellCommands,
            shortcutSubMenuItems = subMenuItems,
            shortcutSubMenuLayout = if (shortcutSubMenuVisible) shortcutSubMenuLayout else null,
            highlightedShortcutIndex = highlightedShortcutIndex,
            shortcutSubMenuRevealProgress = shortcutSubMenuRevealProgress,
        )
    }
}
