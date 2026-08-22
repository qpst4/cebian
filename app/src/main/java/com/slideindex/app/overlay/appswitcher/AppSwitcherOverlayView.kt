package com.slideindex.app.overlay.appswitcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.HoneycombRuntimeTarget
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.overlay.layout.FvAppSwitcherSide
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.overlay.layout.FvIconShape
import com.slideindex.app.overlay.layout.FvPanelLayout
import com.slideindex.app.overlay.layout.FvToolbarButton
import com.slideindex.app.service.AppSwitcherSlotPickTrampolineActivity
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FvAppSwitcherSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.InputMethodHelper
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class AppSwitcherOverlayView(
    context: Context,
    private val onLaunch: (HoneycombRuntimeTarget, Boolean) -> Unit,
    private val onClosed: () -> Unit,
    private val onCircleCountChange: (Int) -> Unit,
    var onSettingsChange: (FvAppSwitcherSettings) -> Unit = {},
    private val onMenuVisualActiveChange: (Boolean) -> Unit = {},
    private val onPrepareDirectTouch: () -> Unit = {},
) : View(context) {

    private enum class SessionMode { NORMAL, EDIT }

    private val composeDialogHost = OverlayComposeDialogHost(
        context = context,
        themeSettings = { settings },
    )

    private var settings = AppSettings()
    private var fvSettings = FvAppSwitcherSettings()
    private var density = 1f
    private var layoutScreenWidth = 0f
    private var targets: List<HoneycombRuntimeTarget?> = emptyList()
    private var appsByPackage: Map<String, AppInfo> = emptyMap()

    private var activeSide: FvAppSwitcherSide? = null
    private var screenAnchorX = 0f
    private var screenAnchorY = 0f
    private var panelPinned = false
    private var sessionMode = SessionMode.NORMAL
    private var sessionActive = false
    private var externalTracking = false
    private var highlightedSlot = -1
    private var highlightedToolbarButton: FvToolbarButton? = null
    private var lastHapticToolbarButton: FvToolbarButton? = null
    private var lastHapticHighlightedSlot = -1
    private var menuRevealProgress = 0f
    private var revealAnimator: ValueAnimator? = null
    private var panelLayout: FvPanelLayout? = null
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
        fvSettings: FvAppSwitcherSettings,
        targets: List<HoneycombRuntimeTarget?>,
        appsByPackage: Map<String, AppInfo>,
        side: FvAppSwitcherSide,
        anchorX: Float,
        anchorY: Float,
        externalTracking: Boolean,
        layoutDensity: Float,
        screenWidth: Float,
    ) {
        this.settings = settings
        this.fvSettings = fvSettings
        this.targets = targets
        this.appsByPackage = appsByPackage
        this.activeSide = side
        this.screenAnchorX = anchorX
        this.screenAnchorY = anchorY
        this.externalTracking = externalTracking
        this.density = layoutDensity
        this.layoutScreenWidth = screenWidth
        rebuildLayout()
    }

    fun refreshTargets(
        fvSettings: FvAppSwitcherSettings,
        targets: List<HoneycombRuntimeTarget?>,
        appsByPackage: Map<String, AppInfo>,
    ) {
        this.fvSettings = fvSettings
        this.targets = targets
        this.appsByPackage = appsByPackage
        rebuildLayout()
        invalidate()
    }

    fun isVisibleSession(): Boolean = sessionActive

    fun isPinned(): Boolean = panelPinned && sessionActive

    fun beginSession() {
        rebuildLayout()
        sessionActive = true
        panelPinned = false
        val hasConfiguredSlots = targets.any { it != null }
        sessionMode = if (!hasConfiguredSlots) SessionMode.EDIT else SessionMode.NORMAL
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
        val screenWidth = if (layoutScreenWidth > 0f) {
            layoutScreenWidth
        } else {
            resources.displayMetrics.widthPixels.toFloat()
        }
        panelLayout = AppSwitcherRenderer.buildLayout(
            circleCount = fvSettings.circleCount,
            side = side,
            anchorX = screenAnchorX,
            anchorY = screenAnchorY,
            screenWidth = screenWidth,
            density = density,
            iconSizeDp = fvSettings.iconSizeDp,
            iconShape = fvSettings.iconShape,
            baseRadiusDp = fvSettings.baseRadiusDp,
            layerGapDp = fvSettings.layerGapDp,
            endMarginDeg = fvSettings.endMarginDeg,
        )
    }

    private fun updateInteraction(localX: Float, localY: Float, eventTime: Long) {
        val layout = panelLayout ?: return
        val toolbarActive = panelPinned || externalTracking
        highlightedToolbarButton = if (toolbarActive) {
            FvCircleLayoutEngine.toolbarButtonAt(layout, localX, localY)
        } else {
            null
        }
        if (highlightedToolbarButton != lastHapticToolbarButton && highlightedToolbarButton != null) {
            lastHapticToolbarButton = highlightedToolbarButton
            HapticHelper.appTick(this, settings)
        } else if (highlightedToolbarButton == null) {
            lastHapticToolbarButton = null
        }
        val slot = if (highlightedToolbarButton == null &&
            !FvCircleLayoutEngine.isOutsidePanel(layout, localX, localY, layout.itemSizePx * 0.35f)
        ) {
            FvCircleLayoutEngine.slotIndexAt(layout, localX, localY)
        } else {
            -1
        }
        if (slot != highlightedSlot) {
            highlightedSlot = slot
            if (slot >= 0 && slot != lastHapticHighlightedSlot) {
                lastHapticHighlightedSlot = slot
                HapticHelper.appTick(this, settings)
            }
        }
        syncSlotPressTracking(slot, eventTime)
    }

    private fun syncSlotPressTracking(slot: Int, eventTime: Long) {
        if (slot >= 0) {
            if (slot != slotPressIndex) {
                slotPressIndex = slot
                slotPressDownTime = eventTime
                slotLongPressArmed = false
                scheduleSlotLongPress(slot)
            }
        } else if (!slotLongPressArmed) {
            cancelSlotLongPress()
        } else {
            cancelSlotLongPressPending()
        }
    }

    private fun handleRelease(localX: Float, localY: Float, eventTime: Long, fromPinned: Boolean): Boolean {
        val layout = panelLayout ?: return false
        updateInteraction(localX, localY, eventTime)
        val toolbarButton = FvCircleLayoutEngine.toolbarButtonAt(layout, localX, localY)
        val slot = FvCircleLayoutEngine.slotIndexAt(layout, localX, localY)
        val outside = FvCircleLayoutEngine.isOutsidePanel(layout, localX, localY, layout.itemSizePx * 0.35f) &&
            toolbarButton == null

        when {
            toolbarButton == FvToolbarButton.PIN -> {
                prepareForToolbarAction()
                pinPanel()
                enterEditMode()
                return true
            }
            toolbarButton == FvToolbarButton.HIDE -> {
                dismissPanel()
                return true
            }
            toolbarButton == FvToolbarButton.SETTINGS -> {
                prepareForToolbarAction()
                pinPanel()
                post { showAppearanceDialog() }
                return true
            }
            toolbarButton == FvToolbarButton.MOVE -> {
                dismissPanel()
                return true
            }
            toolbarButton == FvToolbarButton.KEYBOARD -> {
                val wasEdit = sessionMode == SessionMode.EDIT
                prepareForToolbarAction()
                pinPanel()
                if (wasEdit) enterEditMode()
                InputMethodHelper.showInputMethodPicker(context)
                return true
            }
            sessionMode == SessionMode.EDIT && slot >= 0 -> {
                prepareForToolbarAction()
                openSlotPicker(slot)
                return true
            }
            fromPinned && outside -> {
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
            !fromPinned -> {
                val wasEdit = sessionMode == SessionMode.EDIT
                if (FvCircleLayoutEngine.isNearToolbar(layout, localX, localY)) {
                    prepareForToolbarAction()
                    pinPanel()
                    if (wasEdit) enterEditMode()
                } else if (slot >= 0) {
                    // slot已在上面 !fromPinned && slot >= 0 分支处理，此处不会到达
                    pinPanel()
                } else {
                    // 手指落在空白处松开，自动消失
                    dismissPanel()
                }
                return true
            }
            else -> {
                dismissPanel()
                return true
            }
        }
    }

    private fun prepareForToolbarAction() {
        if (!externalTracking) return
        onPrepareDirectTouch()
        externalTracking = false
    }

    private fun launchSlot(slot: Int, eventTime: Long) {
        val target = targets.getOrNull(slot) ?: return
        val longPressArmed = slotLongPressTriggered(slot, eventTime)
        if (longPressArmed) {
            HapticHelper.confirmLaunch(this, settings)
        }
        cancelSlotLongPress()
        dismissPanel()
        onLaunch(target, longPressArmed)
    }

    private fun slotLongPressTriggered(slot: Int, eventTime: Long): Boolean {
        if (slotLongPressArmed) return true
        if (!settings.launchPolicyLongPressEligible()) return false
        val target = targets.getOrNull(slot) ?: return false
        if (target.item.type != QuickLauncherItemType.APP) return false
        if (slotPressIndex < 0 || slotPressIndex != slot) return false
        return eventTime - slotPressDownTime >= settings.effectiveLongPressDurationMs()
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
        prepareForToolbarAction()
        invalidate()
    }

    private fun enterEditMode() {
        panelPinned = true
        sessionMode = SessionMode.EDIT
        highlightedSlot = -1
        lastHapticHighlightedSlot = -1
        cancelSlotLongPress()
        invalidate()
    }

    private fun showAppearanceDialog() {
        composeDialogHost.show {
            AppSwitcherAppearanceDialogContent(
                currentSettings = fvSettings,
                onSettingsChange = { next ->
                    fvSettings = next
                    rebuildLayout()
                    invalidate()
                    this@AppSwitcherOverlayView.onSettingsChange(next)
                },
                onDismiss = {
                    composeDialogHost.dismiss()
                },
            )
        }
    }

    private fun dismissPanel() {
        revealAnimator?.cancel()
        composeDialogHost.dismiss()
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
        lastHapticToolbarButton = null
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
        cancelSlotLongPressPending()
        if (!settings.launchPolicyLongPressEligible()) return
        val target = targets.getOrNull(slot) ?: return
        if (target.item.type != QuickLauncherItemType.APP) return
        slotLongPressTrackingIndex = slot
        val runnable = Runnable {
            if (slotPressIndex == slotLongPressTrackingIndex && slotLongPressTrackingIndex >= 0) {
                slotLongPressArmed = true
                HapticHelper.longThreshold(this, settings)
                invalidate()
            }
        }
        slotLongPressRunnable = runnable
        postDelayed(runnable, settings.effectiveLongPressDurationMs().toLong())
    }

    private fun cancelSlotLongPressPending() {
        slotLongPressRunnable?.let { removeCallbacks(it) }
        slotLongPressRunnable = null
        slotLongPressTrackingIndex = -1
    }

    private fun cancelSlotLongPress() {
        cancelSlotLongPressPending()
        slotLongPressArmed = false
        slotPressIndex = -1
        slotPressDownTime = 0L
    }

    private fun openSlotPicker(slotIndex: Int) {
        AppSwitcherOverlayWindow.openSlotPicker(slotIndex)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!sessionActive) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateInteraction(event.rawX, event.rawY, event.eventTime)
                invalidate()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                updateInteraction(event.rawX, event.rawY, event.eventTime)
                invalidate()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = handleRelease(event.rawX, event.rawY, event.eventTime, fromPinned = panelPinned)
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
        val slotRevealProgress = if (panelPinned || externalTracking) 1f else menuRevealProgress
        AppSwitcherRenderer.draw(
            context = context,
            canvas = canvas,
            layout = layout,
            targets = targets,
            editMode = sessionMode == SessionMode.EDIT,
            highlightedSlot = highlightedSlot,
            highlightedToolbarButton = highlightedToolbarButton,
            showToolbar = panelPinned || externalTracking,
            density = density,
            revealProgress = slotRevealProgress,
            appsByPackage = appsByPackage,
            activityShortcuts = settings.activityShortcuts,
            shellCommands = settings.shellCommands,
        )
    }

    private fun drawBackgroundMask(canvas: Canvas, progress: Float) {
        if (progress <= 0.01f) return
        val alpha = (255f * 0.48f * progress).toInt().coerceIn(0, 255)
        dimPaint.alpha = alpha
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        composeDialogHost.dismiss()
    }
}

@Composable
private fun AppSwitcherAppearanceDialogContent(
    currentSettings: FvAppSwitcherSettings,
    onSettingsChange: (FvAppSwitcherSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var settingsState by remember(currentSettings) { mutableStateOf(currentSettings) }
    val scrollState = rememberScrollState()

    fun update(transform: (FvAppSwitcherSettings) -> FvAppSwitcherSettings) {
        val next = transform(settingsState)
        settingsState = next
        onSettingsChange(next)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.fv_app_switcher_appearance_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.fv_app_switcher_circle_count_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                ) {
                    // 1. 圈数选择
                    Text(
                        text = stringResource(R.string.fv_app_switcher_circle_count_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            1 to "1 圈(5)",
                            2 to "2 圈(13)",
                            3 to "3 圈(24)",
                            4 to "4 圈(38)",
                        ).forEach { (count, label) ->
                            val selected = settingsState.circleCount == count
                            FilterChip(
                                selected = selected,
                                onClick = { update { it.copy(circleCount = count) } },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. 图标形状
                    Text(
                        text = stringResource(R.string.fv_app_switcher_icon_shape_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            FvIconShape.ROUNDED_RECT to stringResource(R.string.fv_icon_shape_rounded_rect),
                            FvIconShape.CIRCLE to stringResource(R.string.fv_icon_shape_circle),
                            FvIconShape.SQUIRCLE to stringResource(R.string.fv_icon_shape_squircle),
                            FvIconShape.SQUARE to stringResource(R.string.fv_icon_shape_square),
                        ).forEach { (shape, label) ->
                            val selected = settingsState.iconShape == shape
                            FilterChip(
                                selected = selected,
                                onClick = { update { it.copy(iconShape = shape) } },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. 图标大小
                    AppearanceSliderRow(
                        title = stringResource(R.string.fv_app_switcher_icon_size_title),
                        valueText = "${settingsState.iconSizeDp.toInt()} dp",
                        value = settingsState.iconSizeDp,
                        range = FvAppSwitcherSettings.MIN_ICON_SIZE_DP..FvAppSwitcherSettings.MAX_ICON_SIZE_DP,
                        steps = ((FvAppSwitcherSettings.MAX_ICON_SIZE_DP - FvAppSwitcherSettings.MIN_ICON_SIZE_DP) / 2f).toInt() - 1,
                        onValueChange = { update { s -> s.copy(iconSizeDp = (it / 2f).roundToInt() * 2f) } },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. 内圈半径
                    AppearanceSliderRow(
                        title = stringResource(R.string.fv_app_switcher_base_radius_title),
                        valueText = "${settingsState.baseRadiusDp.toInt()} dp",
                        value = settingsState.baseRadiusDp,
                        range = FvAppSwitcherSettings.MIN_BASE_RADIUS_DP..FvAppSwitcherSettings.MAX_BASE_RADIUS_DP,
                        steps = ((FvAppSwitcherSettings.MAX_BASE_RADIUS_DP - FvAppSwitcherSettings.MIN_BASE_RADIUS_DP) / 2f).toInt() - 1,
                        onValueChange = { update { s -> s.copy(baseRadiusDp = (it / 2f).roundToInt() * 2f) } },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. 环间距
                    AppearanceSliderRow(
                        title = stringResource(R.string.fv_app_switcher_layer_gap_title),
                        valueText = "${settingsState.layerGapDp.toInt()} dp",
                        value = settingsState.layerGapDp,
                        range = FvAppSwitcherSettings.MIN_LAYER_GAP_DP..FvAppSwitcherSettings.MAX_LAYER_GAP_DP,
                        steps = ((FvAppSwitcherSettings.MAX_LAYER_GAP_DP - FvAppSwitcherSettings.MIN_LAYER_GAP_DP) / 2f).toInt() - 1,
                        onValueChange = { update { s -> s.copy(layerGapDp = (it / 2f).roundToInt() * 2f) } },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 6. 扇区边距
                    AppearanceSliderRow(
                        title = stringResource(R.string.fv_app_switcher_end_margin_title),
                        valueText = "${settingsState.endMarginDeg.toInt()}°",
                        value = settingsState.endMarginDeg,
                        range = FvAppSwitcherSettings.MIN_END_MARGIN_DEG..FvAppSwitcherSettings.MAX_END_MARGIN_DEG,
                        steps = ((FvAppSwitcherSettings.MAX_END_MARGIN_DEG - FvAppSwitcherSettings.MIN_END_MARGIN_DEG) / 2f).toInt() - 1,
                        onValueChange = { update { s -> s.copy(endMarginDeg = (it / 2f).roundToInt() * 2f) } },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            val reset = FvAppSwitcherSettings(
                                circleCount = settingsState.circleCount,
                                slots = settingsState.slots,
                            )
                            update { reset }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.fv_app_switcher_reset_defaults),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.fv_app_switcher_done),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
