package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandCodec
import com.slideindex.app.widget.WidgetPanelCodec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlaySettingsMutator @Inject constructor(
    private val editor: SettingsPreferencesEditor,
) {
    suspend fun setThemeColor(argb: Int) = editor.edit { it[SettingsPreferenceKeys.THEME_COLOR] = argb }

    suspend fun setDynamicColorEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] = enabled }

    suspend fun setFreeWindowEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.FREE_WINDOW_ENABLED] = enabled }
    suspend fun setFreeWindowModeId(id: Int) = editor.edit {
        it[SettingsPreferenceKeys.FREE_WINDOW_MODE] = FreeWindowMode.fromId(id).id
    }
    suspend fun setFreeWindowLayout(
        widthFraction: Float,
        heightFraction: Float,
        leftFraction: Float,
        topFraction: Float,
    ) = editor.edit {
        it[SettingsPreferenceKeys.FREE_WINDOW_WIDTH] = widthFraction.coerceIn(0.35f, 0.95f)
        it[SettingsPreferenceKeys.FREE_WINDOW_HEIGHT] = heightFraction.coerceIn(0.35f, 0.9f)
        it[SettingsPreferenceKeys.FREE_WINDOW_LEFT] = leftFraction.coerceIn(0f, 0.65f)
        it[SettingsPreferenceKeys.FREE_WINDOW_TOP] = topFraction.coerceIn(0f, 0.65f)
    }
    suspend fun setAppLaunchPolicyId(id: Int) = editor.edit {
        it[SettingsPreferenceKeys.APP_LAUNCH_POLICY] = AppLaunchPolicy.fromId(id).id
    }
    suspend fun setLongPressLaunchDurationMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.LONG_PRESS_LAUNCH_DURATION] = value.coerceIn(250, 900)
    }

    suspend fun setFloatingPointerSensitivityFraction(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_SENSITIVITY] =
            value.coerceIn(0.2f, 0.75f)
    }

    suspend fun setFloatingPointerJoystickDiameterPx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_SIZE] = value.coerceIn(180f, 360f)
    }

    suspend fun setFloatingPointerPointerDiameterPx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_POINTER_SIZE] = value.coerceIn(48f, 120f)
    }

    suspend fun setFloatingPointerDesignId(designId: String) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_DESIGN_ID] = designId.ifBlank { FloatingPointerDesignIds.RING }
    }

    suspend fun setFloatingPointerRingThicknessPx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RING_THICKNESS] = value.coerceIn(4f, 24f)
    }

    suspend fun setFloatingPointerDotDiameterPx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_DOT_DIAMETER] = value.coerceIn(2f, 24f)
    }

    suspend fun setFloatingPointerRingColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RING_COLOR] = argb
    }

    suspend fun setFloatingPointerFillColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_FILL_COLOR] = argb
    }

    suspend fun setFloatingPointerDotColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_DOT_COLOR] = argb
    }

    suspend fun setFloatingPointerClickVisualFeedbackEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_VISUAL_FEEDBACK] = enabled
    }

    suspend fun setFloatingPointerClickHapticEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_HAPTIC] = enabled
    }

    suspend fun setFloatingPointerRippleColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_COLOR] = argb
    }

    suspend fun setFloatingPointerRippleSizeDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_SIZE_DP] = value.coerceIn(40f, 200f)
    }

    suspend fun setFloatingPointerRippleDurationMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_DURATION_MS] = value.coerceIn(100, 1500)
    }

    suspend fun setFloatingPointerTrailType(type: FloatingPointerTrailType) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_TYPE] = type.id
    }

    suspend fun setFloatingPointerTrailDurationMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_DURATION] = value.coerceIn(50, 500)
    }

    suspend fun setFloatingPointerTrailColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_COLOR] = argb
    }

    suspend fun setFloatingPointerHideWhenJoystickReleased(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_ON_RELEASE] = enabled
    }

    suspend fun setFloatingPointerClickDistanceThresholdDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_DISTANCE_THRESHOLD_DP] = value.coerceIn(1f, 30f)
    }

    suspend fun setFloatingPointerJoystickInnerColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_INNER_COLOR] = argb
    }

    suspend fun setFloatingPointerJoystickOuterColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_OUTER_COLOR] = argb
    }

    suspend fun setFloatingPointerJoystickGradientRadiusFraction(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_GRADIENT] = value.coerceIn(0.5f, 1f)
    }

    suspend fun setFloatingPointerHideOnOutsideClick(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_OUTSIDE_CLICK] = enabled
    }

    suspend fun setFloatingPointerHideOnQuickSwipe(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_QUICK_SWIPE] = enabled
    }

    suspend fun setFloatingPointerHideWhenIdle(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_IDLE] = enabled
    }

    suspend fun setFloatingPointerIdleHideDelayMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_IDLE_DELAY] = value.coerceIn(1000, 10000)
    }

    suspend fun setFloatingPointerJoystickLongPressAction(action: GestureAction) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_LONG_PRESS_ACTION] = QuickLauncherItemCodec.encodeActionPayload(action)
    }

    suspend fun setFloatingPointerRadialAlwaysVisible(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ALWAYS_VISIBLE] = enabled
    }

    suspend fun setFloatingPointerRadialLongPressMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_LONG_PRESS_MS] = value.coerceIn(200, 2000)
    }

    suspend fun setFloatingPointerRadialOuterDiameterPx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_SIZE] = value.coerceIn(240f, 720f)
    }

    suspend fun setFloatingPointerRadialInnerDiameterPx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_SIZE] = value.coerceIn(80f, 480f)
    }

    suspend fun setFloatingPointerRadialOuterColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialInnerColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialDividerThicknessPx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_SIZE] = value.coerceIn(1f, 12f)
    }

    suspend fun setFloatingPointerRadialDividerColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialIconSizeFraction(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_SIZE] = value.coerceIn(0.2f, 0.9f)
    }

    suspend fun setFloatingPointerRadialIconColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialSlotAction(index: Int, action: GestureAction) = editor.edit { prefs ->
        val current = FloatingPointerRadialMenuCodec.decode(prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_SLOTS] ?: emptySet())
        val updated = current.toMutableList()
        if (index in 0 until FloatingPointerRadialMenuCodec.SLOT_COUNT) {
            updated[index] = action
            prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_SLOTS] = FloatingPointerRadialMenuCodec.encode(updated)
        }
    }

    suspend fun resetFloatingPointerRadialDesignDefaults() = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_SIZE] = 440f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_SIZE] = 192f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_COLOR] = 0xE62B3D4F.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_COLOR] = 0xE61A1A28.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_SIZE] = 4f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_COLOR] = 0x22FFFFFF
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_SIZE] = 0.85f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_COLOR] = 0xFFFFFFFF.toInt()
    }

    suspend fun resetFloatingPointerVisualDefaults() = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_POINTER_SIZE] = 100f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RING_THICKNESS] = 12f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_DOT_DIAMETER] = 15f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RING_COLOR] = 0xFFFFFFFF.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_FILL_COLOR] = 0x19000000
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_DOT_COLOR] = 0xFFFFFFFF.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_VISUAL_FEEDBACK] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_HAPTIC] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_COLOR] = 0xFFFD746C.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_SIZE_DP] = 80f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_DURATION_MS] = 500
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_TYPE] = FloatingPointerTrailType.HIGH_DETAIL.id
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_DURATION] = 150
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_COLOR] = 0x66FF5252
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_ON_RELEASE] = false
    }

    suspend fun resetFloatingPointerJoystickVisualDefaults() = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_SIZE] = 275f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_INNER_COLOR] = 0x80FFFFFF.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_OUTER_COLOR] = 0x80C0C0C0.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_GRADIENT] = 1f
    }

    suspend fun resetFloatingPointerJoystickBehaviorDefaults() = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_OUTSIDE_CLICK] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_QUICK_SWIPE] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_IDLE] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_IDLE_DELAY] = 3000
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_DISTANCE_THRESHOLD_DP] = 6f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_LONG_PRESS_ACTION] = QuickLauncherItemCodec.encodeActionPayload(GestureAction.OpenFloatingPointerRadialMenu)
    }

    suspend fun setFloatingPointerEdgeThresholdDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_THRESHOLD_DP] = value.coerceIn(5f, 160f)
    }

    suspend fun setFloatingPointerEdgePreviewSensitivity(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_SENSITIVITY] = value.coerceIn(0, 5)
    }

    suspend fun setFloatingPointerEdgePreviewGlowSize(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_GLOW_SIZE] = value.coerceIn(0, 7)
    }

    suspend fun setFloatingPointerEdgePreviewShowIcon(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_SHOW_ICON] = enabled
    }

    suspend fun setFloatingPointerEdgeVisualSizeDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_SIZE_DP] = value.coerceIn(0f, 80f)
    }

    suspend fun setFloatingPointerEdgeVisualOpacity(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_OPACITY] = value.coerceIn(0, 100)
    }

    suspend fun setFloatingPointerEdgeVisualColor(argb: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_COLOR] = argb
    }

    suspend fun setFloatingPointerEdgeBarEnabled(side: FloatingPointerEdgeSide, enabled: Boolean) = editor.edit { prefs ->
        val config = FloatingPointerEdgeActionsCodec.decode(
            prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] ?: emptySet(),
        )
        val updated = config.withBar(side, config.bar(side).copy(enabled = enabled))
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] = FloatingPointerEdgeActionsCodec.encode(updated)
    }

    suspend fun setFloatingPointerEdgeBarSlotAction(
        side: FloatingPointerEdgeSide,
        slotIndex: Int,
        action: GestureAction,
    ) = editor.edit { prefs ->
        val config = FloatingPointerEdgeActionsCodec.decode(
            prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] ?: emptySet(),
        )
        val bar = config.bar(side)
        val slots = bar.layoutSlots().toMutableList()
        if (slotIndex !in slots.indices) return@edit
        slots[slotIndex] = slots[slotIndex].copy(action = action)
        val updated = config.withBar(side, bar.copy(actions = slots))
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] = FloatingPointerEdgeActionsCodec.encode(updated)
    }

    suspend fun addFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide) = editor.edit { prefs ->
        val config = FloatingPointerEdgeActionsCodec.decode(
            prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] ?: emptySet(),
        )
        val bar = config.bar(side)
        if (bar.layoutSlots().size >= FloatingPointerEdgeActionsCodec.MAX_SLOTS_PER_EDGE) return@edit
        val updated = config.withBar(
            side,
            bar.copy(actions = bar.layoutSlots() + FloatingPointerEdgeActionSlot(GestureAction.None)),
        )
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] = FloatingPointerEdgeActionsCodec.encode(updated)
    }

    suspend fun removeFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide, slotIndex: Int) =
        editor.edit { prefs ->
            val config = FloatingPointerEdgeActionsCodec.decode(
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] ?: emptySet(),
            )
            val bar = config.bar(side)
            val slots = bar.layoutSlots().toMutableList()
            if (slots.size <= 1 || slotIndex !in slots.indices) return@edit
            slots.removeAt(slotIndex)
            val updated = config.withBar(side, bar.copy(actions = slots))
            prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] = FloatingPointerEdgeActionsCodec.encode(updated)
        }

    suspend fun resetFloatingPointerEdgeDefaults() = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_THRESHOLD_DP] = 30f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_SENSITIVITY] = 3
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_GLOW_SIZE] = 4
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_SHOW_ICON] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_SIZE_DP] = 0f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_OPACITY] = 75
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_COLOR] = 0xFFFD746C.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] =
            FloatingPointerEdgeActionsCodec.encode(FloatingPointerEdgeActionsCodec.defaultConfig())
    }

    suspend fun setQuickLauncherItems(
        items: List<com.slideindex.app.launcher.QuickLauncherItem>,
    ) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER] = QuickLauncherItemCodec.encodeAll(items)
    }

    suspend fun setShellCommands(items: List<ShellCommand>) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.SHELL_COMMANDS] = ShellCommandCodec.encodeAll(items)
    }

    suspend fun setWidgetPanelPages(
        pages: List<com.slideindex.app.widget.WidgetPanelPage>,
    ) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_PAGES] = WidgetPanelCodec.encodeAll(pages)
    }

    suspend fun setWidgetPanelBlurEnabled(enabled: Boolean) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_BLUR] = enabled
    }

    suspend fun setWidgetPanelWidthFraction(fraction: Float) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_WIDTH] = fraction.coerceIn(0.5f, 0.95f)
    }

    suspend fun setDebugPerformanceMonitorEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.DEBUG_PERFORMANCE_MONITOR] = enabled }

    suspend fun setFloatBallEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_ENABLED] = enabled
    }

    suspend fun setFloatBallSizeDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_SIZE_DP] = value.coerceIn(36f, 72f)
    }

    suspend fun setFloatBallOpacity(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_OPACITY] = value.coerceIn(0.3f, 1f)
    }

    suspend fun setFloatBallPosition(xFraction: Float, yFraction: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POSITION_X_FRACTION] = xFraction.coerceIn(0.05f, 0.95f)
        it[SettingsPreferenceKeys.FLOAT_BALL_POSITION_Y_FRACTION] = yFraction.coerceIn(0.05f, 0.95f)
    }

    suspend fun setFloatBallPositionYFraction(yFraction: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POSITION_Y_FRACTION] = yFraction.coerceIn(0.05f, 0.95f)
    }

    suspend fun setFloatBallPositionXFraction(xFraction: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POSITION_X_FRACTION] = xFraction.coerceIn(0.05f, 0.95f)
    }

    suspend fun setFloatBallOcrFallbackEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_OCR_FALLBACK_ENABLED] = enabled
    }

    suspend fun setFloatBallOcrModelId(modelId: String) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_OCR_MODEL_ID] = modelId
    }

    suspend fun setOcrDownloadWifiOnly(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.OCR_DOWNLOAD_WIFI_ONLY] = enabled
    }

    suspend fun setFloatBallPointerSpeedFraction(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SPEED_FRACTION] =
            value.coerceIn(0.2f, 0.75f)
    }

    suspend fun setFloatBallPositionMode(mode: FloatBallPositionMode) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POSITION_MODE] = mode.storageKey
        when (mode) {
            FloatBallPositionMode.LEFT -> {
                it[SettingsPreferenceKeys.FLOAT_BALL_ACTIVE_SIDE] = FloatBallSide.LEFT.storageKey
            }
            FloatBallPositionMode.RIGHT -> {
                it[SettingsPreferenceKeys.FLOAT_BALL_ACTIVE_SIDE] = FloatBallSide.RIGHT.storageKey
            }
            else -> Unit
        }
    }

    suspend fun setFloatBallActiveSide(side: FloatBallSide) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_ACTIVE_SIDE] = side.storageKey
    }

    suspend fun setFloatBallLineHeightFraction(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_LINE_HEIGHT_FRACTION] = value.coerceIn(0.04f, 0.4f)
    }

    suspend fun setFloatBallLineWidthFraction(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_LINE_WIDTH_FRACTION] = value.coerceIn(0.04f, 0.5f)
    }

    suspend fun setFloatBallLineOpacity(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_LINE_OPACITY] = value.coerceIn(0.1f, 1f)
    }

    suspend fun setFloatBallPickOffsetDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_PICK_OFFSET_DP] = value.coerceIn(4f, 48f)
    }

    suspend fun setFloatBallPickTextSizeSp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_PICK_TEXT_SIZE_SP] = value.coerceIn(12f, 22f)
    }

    suspend fun setFloatBallPickBottomTransitionFraction(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_PICK_BOTTOM_TRANSITION_FRACTION] =
            value.coerceIn(0.05f, 0.22f)
    }

    suspend fun setFloatBallPointerSlopDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SLOP_DP] = value.coerceIn(4f, 32f)
    }

    suspend fun setFloatBallInstantTranslate(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_INSTANT_TRANSLATE] = enabled
    }

    suspend fun setFloatBallTranslateEngine(engine: FloatBallTranslateEngine) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_ENGINE] = engine.storageKey
    }

    suspend fun setFloatBallTranslateTargetLang(languageCode: String) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_TARGET_LANG] = languageCode
    }

    suspend fun setFloatBallTranslatePickPanelTransparency(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_PICK_PANEL_TRANSPARENCY] =
            value.coerceIn(0f, 1f)
    }

    suspend fun setShareImageOcrHistoryEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SHARE_IMAGE_OCR_HISTORY_ENABLED] = enabled
    }
}
