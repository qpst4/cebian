package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.floatball.FloatBallGestureCodec
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandCodec
import com.slideindex.app.activity.ActivityShortcutCodec
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

    suspend fun setThemePaletteStyle(style: ThemePaletteStyle) = editor.edit {
        it[SettingsPreferenceKeys.THEME_PALETTE_STYLE] = style.id
    }

    suspend fun setThemeMode(mode: AppThemeMode) = editor.edit {
        it[SettingsPreferenceKeys.THEME_MODE] = mode.id
    }

    suspend fun setCustomColorEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CUSTOM_COLOR_ENABLED] = enabled
    }

    suspend fun setDarkBackgroundStyle(style: DarkBackgroundStyle) = editor.edit {
        it[SettingsPreferenceKeys.DARK_BACKGROUND_STYLE] = style.id
    }

    suspend fun setThemeColorSpec(spec: AppColorSpec) = editor.edit {
        it[SettingsPreferenceKeys.THEME_COLOR_SPEC] = spec.id
    }

    suspend fun setBottomNavBlurRadiusDp(value: Float) = editor.edit { prefs ->
        val coerced = value.coerceIn(BottomNavBlurDefaults.MIN_RADIUS_DP, BottomNavBlurDefaults.MAX_RADIUS_DP)
        when (
            BottomNavStyle.fromId(
                prefs[SettingsPreferenceKeys.BOTTOM_NAV_STYLE] ?: BottomNavStyle.FLOATING_NAV.id,
            )
        ) {
            BottomNavStyle.CLASSIC ->
                prefs[SettingsPreferenceKeys.BOTTOM_NAV_CLASSIC_BLUR_RADIUS_DP] = coerced
            BottomNavStyle.LIQUID_GLASS ->
                prefs[SettingsPreferenceKeys.BOTTOM_NAV_LIQUID_GLASS_BLUR_RADIUS_DP] = coerced
            BottomNavStyle.FLOATING_NAV ->
                prefs[SettingsPreferenceKeys.BOTTOM_NAV_FLOATING_NAV_BLUR_RADIUS_DP] = coerced
        }
    }

    suspend fun setBottomNavStyle(style: BottomNavStyle) = editor.edit {
        it[SettingsPreferenceKeys.BOTTOM_NAV_STYLE] = style.id
    }

    suspend fun setBottomNavMode(mode: BottomNavMode) = editor.edit {
        it[SettingsPreferenceKeys.BOTTOM_NAV_MODE] = mode.id
    }

    suspend fun setBottomNavGlassEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.BOTTOM_NAV_GLASS_ENABLED] = enabled
    }

    suspend fun setTopAppBarBlurStyle(style: TopAppBarBlurStyle) = editor.edit {
        it[SettingsPreferenceKeys.TOP_APP_BAR_BLUR_STYLE] = style.id
    }

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

    suspend fun setFloatingPointerReleaseClickAndDismiss(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RELEASE_CLICK_AND_DISMISS] = enabled
    }

    suspend fun setFloatingPointerHoverEnterSelect(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HOVER_ENTER_SELECT] = enabled
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

    suspend fun setFingertipRingSlotCount(count: Int) = editor.edit { prefs ->
        val nextCount = FingertipRingCodec.effectiveSlotCount(count)
        val current = FingertipRingCodec.decode(
            prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOTS] ?: emptySet(),
            prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOT_COUNT] ?: FingertipRingCodec.DEFAULT_SLOT_COUNT,
        )
        prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOT_COUNT] = nextCount
        prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOTS] = FingertipRingCodec.encode(current, nextCount)
    }

    suspend fun setFingertipRingSlotAction(index: Int, action: GestureAction) = editor.edit { prefs ->
        val slotCount = prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOT_COUNT] ?: FingertipRingCodec.DEFAULT_SLOT_COUNT
        val current = FingertipRingCodec.decode(prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOTS] ?: emptySet(), slotCount)
        val updated = current.toMutableList()
        if (index in 0 until FingertipRingCodec.effectiveSlotCount(slotCount)) {
            updated[index] = action
            prefs[SettingsPreferenceKeys.FINGERTIP_RING_SLOTS] = FingertipRingCodec.encode(updated, slotCount)
        }
    }

    suspend fun setFingertipRingOrbitRadiusPx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FINGERTIP_RING_ORBIT_RADIUS_PX] = FingertipRingCodec.effectiveOrbitRadiusPx(value)
    }

    suspend fun setFingertipRingIconSizePx(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FINGERTIP_RING_ICON_SIZE_PX] = FingertipRingCodec.effectiveIconSizePx(value)
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
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RELEASE_CLICK_AND_DISMISS] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_HOVER_ENTER_SELECT] = false
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

    suspend fun setQuickLauncherPanels(
        panels: List<com.slideindex.app.launcher.QuickLauncherPanel>,
    ) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_PANELS] =
            com.slideindex.app.launcher.QuickLauncherPanelCodec.encodeAll(panels)
    }

    suspend fun updateQuickLauncherPanelItems(
        panelId: String,
        items: List<com.slideindex.app.launcher.QuickLauncherItem>,
    ) = editor.edit { prefs ->
        val current = readQuickLauncherPanelsFromPrefs(prefs)
        val updated = com.slideindex.app.launcher.QuickLauncherPanelMutator.updatePanelItems(
            current,
            panelId,
            items,
        )
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_PANELS] =
            com.slideindex.app.launcher.QuickLauncherPanelCodec.encodeAll(updated)
    }

    suspend fun setQuickLauncherItems(
        items: List<com.slideindex.app.launcher.QuickLauncherItem>,
    ) = editor.edit { prefs ->
        val panels = com.slideindex.app.launcher.QuickLauncherPanelDefaults.effectivePanels(
            readQuickLauncherPanelsFromPrefs(prefs),
        )
        val defaultId = panels.first().id
        val updated = com.slideindex.app.launcher.QuickLauncherPanelMutator.updatePanelItems(
            panels,
            defaultId,
            items,
        )
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_PANELS] =
            com.slideindex.app.launcher.QuickLauncherPanelCodec.encodeAll(updated)
    }

    private fun readQuickLauncherPanelsFromPrefs(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): List<com.slideindex.app.launcher.QuickLauncherPanel> {
        val encoded = com.slideindex.app.launcher.QuickLauncherPanelCodec.decodeAll(
            prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_PANELS] ?: emptySet(),
        )
        if (encoded.isNotEmpty()) return encoded
        val legacyItems = QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER] ?: emptySet())
            .ifEmpty {
                val left = QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_LEFT] ?: emptySet())
                if (left.isNotEmpty()) left else {
                    QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_RIGHT] ?: emptySet())
                }
            }
        val columns = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_COLUMNS_PER_PAGE] ?: 3
        val rows = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ROWS_PER_PAGE] ?: 4
        return com.slideindex.app.launcher.QuickLauncherPanelDefaults.migrateFromLegacyItems(
            items = legacyItems,
            columnsPerPage = columns,
            rowsPerPage = rows,
        )
    }

    suspend fun setHoneycombLauncherItems(
        items: List<com.slideindex.app.launcher.QuickLauncherItem>,
    ) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.HONEYCOMB_LAUNCHER] = QuickLauncherItemCodec.encodeAll(items)
    }

    suspend fun setFvAppSwitcherSettings(
        axis: FvAppSwitcherAxis,
        settings: FvAppSwitcherSettings,
    ) = editor.edit { prefs ->
        val linkAppearance = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LINK_APPEARANCE_AXES]
            ?: FvAppSwitcherSettings.linkFlagsFromPreferences(prefs).linkAppearanceAxes
        if (linkAppearance) {
            FvAppSwitcherSettings.writeAppearanceAxis(prefs, FvAppSwitcherAxis.VERTICAL, settings)
            FvAppSwitcherSettings.writeAppearanceAxis(prefs, FvAppSwitcherAxis.HORIZONTAL, settings)
        } else {
            FvAppSwitcherSettings.writeAppearanceAxis(prefs, axis, settings)
        }
    }

    suspend fun setFvAppSwitcherSlot(
        axis: FvAppSwitcherAxis,
        index: Int,
        item: com.slideindex.app.launcher.QuickLauncherItem,
    ) = editor.edit { prefs ->
        val linkSlots = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LINK_SLOT_AXES]
            ?: FvAppSwitcherSettings.linkFlagsFromPreferences(prefs).linkSlotAxes
        val targetAxes = if (linkSlots) {
            listOf(FvAppSwitcherAxis.VERTICAL, FvAppSwitcherAxis.HORIZONTAL)
        } else {
            listOf(axis)
        }
        targetAxes.forEach { targetAxis ->
            val current = FvAppSwitcherSettings.fromPreferences(prefs, targetAxis).slots.toMutableMap()
            if (item.payload.isBlank()) {
                current.remove(index)
            } else {
                current[index] = item
            }
            FvAppSwitcherSettings.writeSlotsAxis(
                prefs,
                targetAxis,
                FvAppSwitcherSettings.fromPreferences(prefs, targetAxis).copy(slots = current),
            )
        }
    }

    suspend fun setFvAppSwitcherCircleCount(
        axis: FvAppSwitcherAxis,
        circleCount: Int,
    ) = editor.edit { prefs ->
        val safeCount = circleCount.coerceIn(
            FvAppSwitcherSettings.MIN_CIRCLE_COUNT,
            FvAppSwitcherSettings.MAX_CIRCLE_COUNT,
        )
        val linkAppearance = prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LINK_APPEARANCE_AXES]
            ?: FvAppSwitcherSettings.linkFlagsFromPreferences(prefs).linkAppearanceAxes
        val targetAxes = if (linkAppearance) {
            listOf(FvAppSwitcherAxis.VERTICAL, FvAppSwitcherAxis.HORIZONTAL)
        } else {
            listOf(axis)
        }
        targetAxes.forEach { targetAxis ->
            val current = FvAppSwitcherSettings.fromPreferences(prefs, targetAxis)
            FvAppSwitcherSettings.writeAppearanceAxis(
                prefs,
                targetAxis,
                current.copy(circleCount = safeCount),
            )
        }
    }

    suspend fun setFvAppSwitcherLinkAppearanceAxes(
        enabled: Boolean,
        activeAxis: FvAppSwitcherAxis,
        mergeDirection: FvAppSwitcherAxisMergeDirection?,
    ) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LINK_APPEARANCE_AXES] = enabled
        if (!enabled || mergeDirection == null) return@edit
        val source = mergeSource(prefs, activeAxis, mergeDirection)
        FvAppSwitcherSettings.writeAppearanceAxis(prefs, FvAppSwitcherAxis.VERTICAL, source)
        FvAppSwitcherSettings.writeAppearanceAxis(prefs, FvAppSwitcherAxis.HORIZONTAL, source)
    }

    suspend fun setFvAppSwitcherLinkSlotAxes(
        enabled: Boolean,
        activeAxis: FvAppSwitcherAxis,
        mergeDirection: FvAppSwitcherAxisMergeDirection?,
    ) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.FV_APP_SWITCHER_LINK_SLOT_AXES] = enabled
        if (!enabled || mergeDirection == null) return@edit
        val source = mergeSource(prefs, activeAxis, mergeDirection)
        FvAppSwitcherSettings.writeSlotsAxis(prefs, FvAppSwitcherAxis.VERTICAL, source)
        FvAppSwitcherSettings.writeSlotsAxis(prefs, FvAppSwitcherAxis.HORIZONTAL, source)
    }

    private fun mergeSource(
        prefs: Preferences,
        activeAxis: FvAppSwitcherAxis,
        mergeDirection: FvAppSwitcherAxisMergeDirection,
    ): FvAppSwitcherSettings {
        val current = FvAppSwitcherSettings.fromPreferences(prefs, activeAxis)
        val other = FvAppSwitcherSettings.fromPreferences(prefs, activeAxis.other())
        return when (mergeDirection) {
            FvAppSwitcherAxisMergeDirection.USE_OTHER_AXIS -> other
            FvAppSwitcherAxisMergeDirection.USE_CURRENT_AXIS -> current
        }
    }

    suspend fun setQuickLauncherDisplaySettings(
        settings: QuickLauncherDisplaySettings,
    ) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_BACKGROUND_OPACITY_PERCENT] =
            settings.backgroundOpacityPercent.coerceIn(
                QuickLauncherDisplaySettings.MIN_BACKGROUND_OPACITY_PERCENT,
                QuickLauncherDisplaySettings.MAX_BACKGROUND_OPACITY_PERCENT,
            )
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ICON_SIZE_DP] =
            settings.iconSizeDp.coerceIn(
                QuickLauncherDisplaySettings.MIN_ICON_SIZE_DP,
                QuickLauncherDisplaySettings.MAX_ICON_SIZE_DP,
            )
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ICON_SHAPE] =
            QuickLauncherDisplaySettings.coerceIconShape(settings.iconShape)
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_BLUR_RADIUS_DP] =
            settings.blurRadiusDp.coerceIn(
                QuickLauncherDisplaySettings.MIN_BLUR_RADIUS_DP,
                QuickLauncherDisplaySettings.MAX_BLUR_RADIUS_DP,
            )
    }

    suspend fun setHoneycombDisplaySettings(settings: HoneycombDisplaySettings) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.HONEYCOMB_MODE] =
            settings.mode.coerceIn(HoneycombDisplaySettings.MODE_BROWSE, HoneycombDisplaySettings.MODE_HOLD)
        prefs[SettingsPreferenceKeys.HONEYCOMB_ICON_SIZE_DP] =
            settings.iconSizeDp.coerceIn(HoneycombDisplaySettings.MIN_ICON_SIZE_DP, HoneycombDisplaySettings.MAX_ICON_SIZE_DP)
        prefs[SettingsPreferenceKeys.HONEYCOMB_SPACING_DP] =
            settings.spacingDp.coerceIn(HoneycombDisplaySettings.MIN_SPACING_DP, HoneycombDisplaySettings.MAX_SPACING_DP)
        prefs[SettingsPreferenceKeys.HONEYCOMB_ANIMATION_SPEED] =
            settings.animationSpeed.coerceIn(HoneycombDisplaySettings.MIN_ANIMATION_SPEED, HoneycombDisplaySettings.MAX_ANIMATION_SPEED)
        prefs[SettingsPreferenceKeys.HONEYCOMB_INERTIA] =
            settings.inertia.coerceIn(HoneycombDisplaySettings.MIN_INERTIA, HoneycombDisplaySettings.MAX_INERTIA)
        prefs[SettingsPreferenceKeys.HONEYCOMB_CENTER_SCALE] =
            settings.centerScale.coerceIn(HoneycombDisplaySettings.MIN_CENTER_SCALE, HoneycombDisplaySettings.MAX_CENTER_SCALE)
        prefs[SettingsPreferenceKeys.HONEYCOMB_EDGE_SCALE] =
            settings.edgeScale.coerceIn(HoneycombDisplaySettings.MIN_EDGE_SCALE, HoneycombDisplaySettings.MAX_EDGE_SCALE)
        prefs[SettingsPreferenceKeys.HONEYCOMB_SELECTION_SCALE] =
            settings.selectionScale.coerceIn(HoneycombDisplaySettings.MIN_SELECTION_SCALE, HoneycombDisplaySettings.MAX_SELECTION_SCALE)
        prefs[SettingsPreferenceKeys.HONEYCOMB_EMPTY_TAP_CLOSE] = settings.emptyTapClose
        prefs[SettingsPreferenceKeys.HONEYCOMB_SHOW_SELECTED_NAME] = settings.showSelectedName
        prefs[SettingsPreferenceKeys.HONEYCOMB_SELECTED_HINT_ICON_SIZE_DP] =
            SelectedHintMetrics.clampIconSizeDp(settings.selectedHintIconSizeDp)
        prefs[SettingsPreferenceKeys.HONEYCOMB_FOLLOW_FINGER] = settings.followFinger
        prefs[SettingsPreferenceKeys.HONEYCOMB_FIXED_X_PERCENT] = settings.fixedXPercent.coerceIn(0, 100)
        prefs[SettingsPreferenceKeys.HONEYCOMB_FIXED_Y_PERCENT] = settings.fixedYPercent.coerceIn(0, 100)
        prefs[SettingsPreferenceKeys.HONEYCOMB_BACKGROUND_STYLE] = when (settings.backgroundStyle) {
            HoneycombDisplaySettings.BACKGROUND_BLACK,
            HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR,
            -> settings.backgroundStyle
            else -> HoneycombDisplaySettings.BACKGROUND_BLUR
        }
        prefs[SettingsPreferenceKeys.HONEYCOMB_BLUR_DP] =
            settings.blurDp.coerceIn(HoneycombDisplaySettings.MIN_BLUR_DP, HoneycombDisplaySettings.MAX_BLUR_DP)
        prefs[SettingsPreferenceKeys.HONEYCOMB_DIM_PERCENT] =
            settings.dimPercent.coerceIn(HoneycombDisplaySettings.MIN_DIM_PERCENT, HoneycombDisplaySettings.MAX_DIM_PERCENT)
        prefs[SettingsPreferenceKeys.HONEYCOMB_DISC_SIZE_PERCENT] =
            settings.discSizePercent.coerceIn(HoneycombDisplaySettings.MIN_DISC_SIZE_PERCENT, HoneycombDisplaySettings.MAX_DISC_SIZE_PERCENT)
    }

    suspend fun setShellCommands(items: List<ShellCommand>) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.SHELL_COMMANDS] = ShellCommandCodec.encodeAll(items)
    }

    suspend fun setActivityShortcuts(items: List<com.slideindex.app.activity.ActivityShortcut>) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.ACTIVITY_SHORTCUTS] = ActivityShortcutCodec.encodeAll(items)
    }

    suspend fun setWidgetPanelPages(
        pages: List<com.slideindex.app.widget.WidgetPanelPage>,
    ) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_PAGES] = WidgetPanelCodec.encodeAll(pages)
    }

    suspend fun setWidgetPanelBlurEnabled(enabled: Boolean) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_BLUR] = enabled
    }

    suspend fun setWidgetPanelBlurRadiusDp(radiusDp: Int) = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_BLUR_RADIUS_DP] =
            radiusDp.coerceIn(AppSettings.WIDGET_PANEL_BLUR_RADIUS_MIN_DP, AppSettings.WIDGET_PANEL_BLUR_RADIUS_MAX_DP)
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

    suspend fun setFloatBallPickCrossArmDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_PICK_CROSS_ARM_DP] = value.coerceIn(4f, 16f)
    }

    suspend fun setFloatBallOpacity(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_OPACITY] = value.coerceIn(0f, 1f)
    }

    suspend fun setFloatBallPosition(customCenterXFraction: Float, yFraction: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POSITION_X_FRACTION] =
            FloatBallPositionFractions.coerceCustomCenterX(customCenterXFraction)
        it[SettingsPreferenceKeys.FLOAT_BALL_POSITION_Y_FRACTION] =
            FloatBallPositionFractions.coerceY(yFraction)
    }

    suspend fun setFloatBallPositionYFraction(yFraction: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POSITION_Y_FRACTION] =
            FloatBallPositionFractions.coerceY(yFraction)
    }

    suspend fun setFloatBallVisibleFraction(visibleFraction: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_VISIBLE_FRACTION] =
            FloatBallPositionFractions.coerceVisible(visibleFraction)
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

    suspend fun setFloatBallPointerSpeedVerticalFraction(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SPEED_VERTICAL_FRACTION] =
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
        it[SettingsPreferenceKeys.FLOAT_BALL_LINE_WIDTH_FRACTION] = value.coerceIn(0.01f, 0.50f)
    }

    suspend fun setFloatBallLineOpacity(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_LINE_OPACITY] = value.coerceIn(0f, 1f)
    }

    suspend fun setFloatBallGestureAction(type: FloatBallGestureType, action: GestureAction) = editor.edit { prefs ->
        val updated = SettingsSnapshotReader.readFloatBallGestureActions(prefs).toMutableMap().apply {
            put(type, action)
        }
        prefs[SettingsPreferenceKeys.FLOAT_BALL_GESTURE_ACTIONS] = FloatBallGestureCodec.encodeAll(updated)
    }

    suspend fun setFloatBallStyleType(type: FloatBallStyleType) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_STYLE_TYPE] = type.storageKey
    }

    suspend fun setFloatBallCustomImageUri(uri: String) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_CUSTOM_IMAGE_URI] = uri
    }

    suspend fun setFloatBallSlideshowUris(uris: List<String>) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_SLIDESHOW_URIS] = uris.filter { it.isNotBlank() }.toSet()
    }

    suspend fun setFloatBallGifUri(uri: String) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_GIF_URI] = uri
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

    suspend fun setFloatBallPickTextFirstPanel(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_PICK_TEXT_FIRST_PANEL] = enabled
    }

    suspend fun setFloatBallPickPanelEnterAnimationMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_PICK_PANEL_ENTER_ANIMATION_MS] =
            PickPanelSlideAnimationDefaults.coerceMs(value)
    }

    suspend fun setFloatBallPickPanelExitAnimationMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_PICK_PANEL_EXIT_ANIMATION_MS] =
            PickPanelSlideAnimationDefaults.coerceMs(value)
    }

    suspend fun setFloatBallPointerSlopDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SLOP_DP] = value.coerceIn(4f, 32f)
    }

    suspend fun setFloatBallHoverPauseDelayMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_HOVER_PAUSE_DELAY_MS] = value.coerceIn(200, 1000)
    }

    suspend fun setFloatBallRegionalCancelSlopDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_REGIONAL_CANCEL_SLOP_DP] = value.coerceIn(3f, 30f)
    }

    suspend fun setFloatBallDownSwipeShortPercent(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_DOWN_SWIPE_SHORT_PERCENT] = value.coerceIn(50f, 500f)
    }

    suspend fun setFloatBallSideSwipeShortPercent(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_SIDE_SWIPE_SHORT_PERCENT] = value.coerceIn(50f, 500f)
    }

    suspend fun setFloatBallUpSwipeShortPercent(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_UP_SWIPE_SHORT_PERCENT] = value.coerceIn(50f, 500f)
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

    suspend fun setFloatBallImageSearchPickPanelTransparency(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.FLOAT_BALL_IMAGE_SEARCH_PICK_PANEL_TRANSPARENCY] =
            value.coerceIn(0f, 1f)
    }

    suspend fun setShareImageOcrHistoryEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SHARE_IMAGE_OCR_HISTORY_ENABLED] = enabled
    }

    suspend fun setClipboardBackgroundMonitoring(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_BACKGROUND_MONITORING] = enabled
    }

    suspend fun setClipboardBackgroundMonitoringMode(mode: ClipboardMonitoringMode) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_BACKGROUND_MONITORING_PATH] = mode.storageValue
    }

    suspend fun setClipboardScreenshotMonitoring(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_SCREENSHOT_MONITORING] = enabled
    }

    suspend fun setClipboardHistoryMaxEntries(maxEntries: Int) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_HISTORY_MAX_ENTRIES] =
            ClipboardHistoryCapacity.coerce(maxEntries)
    }

    suspend fun setClipboardHistoryFloatEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_ENABLED] = enabled
    }

    suspend fun setClipboardHistoryFloatEnabledLandscape(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_ENABLED_LANDSCAPE] = enabled
    }

    suspend fun setClipboardHistoryFloatLockPosition(lock: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_LOCK_POSITION] = lock
    }

    suspend fun setClipboardHistoryFloatHandleWidthDp(widthDp: Int) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_WIDTH_DP] =
            HistoryFloatHandleWidth.coerce(widthDp)
    }

    suspend fun setClipboardHistoryFloatHandleY(y: Int, landscape: Boolean) = editor.edit {
        val key = if (landscape) {
            SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_Y_LANDSCAPE
        } else {
            SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_Y_PORTRAIT
        }
        it[key] = y
    }

    suspend fun setClipboardFloatEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENABLED] = enabled
    }

    suspend fun setClipboardFloatShowChip(showChip: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_SHOW_CHIP] = showChip
    }

    suspend fun setClipboardFloatPinPosition(pin: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_PIN_POSITION] = pin
    }

    suspend fun setClipboardFloatEntryClickAction(action: ClipboardFloatEntryClickAction) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_ENTRY_CLICK_ACTION] = action.storageValue
    }

    suspend fun setClipboardFloatListStyle(style: ClipboardFloatListStyle) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_LIST_STYLE] = style.id
    }

    suspend fun setClipboardFloatAlpha(alpha: Float) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_ALPHA] = alpha.coerceIn(0.2f, 1.0f)
    }

    suspend fun setClipboardFloatAutoDimWhenUnfocused(autoDim: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_AUTO_DIM_UNFOCUSED] = autoDim
    }

    suspend fun setClipboardFloatAutoCloseSeconds(seconds: Int) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_AUTO_CLOSE_SECONDS] = seconds.coerceAtLeast(0)
    }

    suspend fun setClipboardFloatGeometry(
        x: Int,
        y: Int,
        widthDp: Int,
        heightDp: Int,
        landscape: Boolean,
    ) = editor.edit {
        val current = ClipboardFloatGeometryPrefs.readOrientationGeometry(it, landscape)
        ClipboardFloatGeometryPrefs.writeOrientationGeometry(
            it,
            landscape,
            current.copy(
                panelX = x,
                panelY = y,
                panelWidthDp = ClipboardFloatWindowMetrics.coerceWidth(widthDp),
                panelHeightDp = ClipboardFloatWindowMetrics.coerceHeight(heightDp),
            ),
        )
        if (!landscape) {
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_X] = x
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_Y] = y
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_WIDTH_DP] =
                ClipboardFloatWindowMetrics.coerceWidth(widthDp)
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_HEIGHT_DP] =
                ClipboardFloatWindowMetrics.coerceHeight(heightDp)
        }
    }

    suspend fun resetClipboardFloatGeometry() = editor.edit {
        ClipboardFloatGeometryPrefs.resetAllGeometry(it)
    }

    suspend fun setClipboardFloatChipGeometry(
        x: Int,
        y: Int,
        followIme: Boolean,
        landscape: Boolean,
    ) = editor.edit {
        val current = ClipboardFloatGeometryPrefs.readOrientationGeometry(it, landscape)
        ClipboardFloatGeometryPrefs.writeOrientationGeometry(
            it,
            landscape,
            current.copy(chipX = x, chipY = y),
        )
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_FOLLOW_IME] = followIme
        if (!landscape) {
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_X] = x
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_Y] = y
        }
    }

    suspend fun setClipboardFloatOrientationGeometry(
        landscape: Boolean,
        geometry: ClipboardFloatOrientationGeometry,
        chipFollowIme: Boolean,
    ) = editor.edit {
        ClipboardFloatGeometryPrefs.writeOrientationGeometry(it, landscape, geometry)
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_FOLLOW_IME] = chipFollowIme
        if (!landscape) {
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_X] = geometry.panelX
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_Y] = geometry.panelY
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_WIDTH_DP] = geometry.panelWidthDp
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PANEL_HEIGHT_DP] = geometry.panelHeightDp
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_X] = geometry.chipX
            it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_CHIP_Y] = geometry.chipY
        }
    }

    suspend fun addClipboardFloatBlockedPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_BLOCKED_PACKAGES]?.toMutableSet()
            ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_BLOCKED_PACKAGES] = current
    }

    suspend fun removeClipboardFloatBlockedPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_BLOCKED_PACKAGES]?.toMutableSet()
            ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_BLOCKED_PACKAGES] = current
    }

    suspend fun setClipboardFloatPasteHapticEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CLIPBOARD_FLOAT_PASTE_HAPTIC_ENABLED] = enabled
    }

    suspend fun recordClipboardFloatPasteResult(success: Boolean) = editor.edit {
        val key = if (success) {
            SettingsPreferenceKeys.CLIPBOARD_FLOAT_PASTE_SUCCESS_COUNT
        } else {
            SettingsPreferenceKeys.CLIPBOARD_FLOAT_PASTE_FAIL_COUNT
        }
        it[key] = (it[key] ?: 0) + 1
    }

    suspend fun setStashPanelBackgroundBlurEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.STASH_PANEL_BACKGROUND_BLUR_ENABLED] = enabled
    }

    suspend fun setStashPanelBackgroundBlurRadiusDp(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.STASH_PANEL_BACKGROUND_BLUR_RADIUS_DP] = value.coerceIn(
            AppSettings.STASH_PANEL_BLUR_RADIUS_MIN_DP,
            AppSettings.STASH_PANEL_BLUR_RADIUS_MAX_DP,
        )
    }

    suspend fun setDefaultImageViewerPackage(packageName: String?) = editor.edit {
        if (packageName == null) {
            it.remove(SettingsPreferenceKeys.DEFAULT_IMAGE_VIEWER_PACKAGE)
        } else {
            it[SettingsPreferenceKeys.DEFAULT_IMAGE_VIEWER_PACKAGE] = packageName
        }
    }

    suspend fun setSearchEngines(engines: List<SearchEngineConfig>) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_ENGINES_JSON] = SearchEngineStore.encode(engines)
        it[SettingsPreferenceKeys.SEARCH_ENGINES_INITIALIZED] = true
    }

    suspend fun setSearchEngineGridColumns(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_ENGINE_GRID_COLUMNS] = value.coerceIn(3, 7)
    }

    suspend fun setSearchEngineGridRows(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_ENGINE_GRID_ROWS] = value.coerceIn(1, 4)
    }

    suspend fun setSearchEngineShowLabels(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_ENGINE_SHOW_LABELS] = enabled
    }

    suspend fun setSearchPanelDefaultEngineId(id: String?) = editor.edit {
        if (id == null) {
            it.remove(SettingsPreferenceKeys.SEARCH_PANEL_DEFAULT_ENGINE_ID)
        } else {
            it[SettingsPreferenceKeys.SEARCH_PANEL_DEFAULT_ENGINE_ID] = id
        }
    }

    suspend fun setSearchPanelInputBehavior(behavior: SearchPanelInputBehavior) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_INPUT_BEHAVIOR] = behavior.name
    }

    suspend fun setSearchPanelContactSearchEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_CONTACT_SEARCH_ENABLED] = enabled
    }

    suspend fun setSearchPanelFileSearchEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_FILE_SEARCH_ENABLED] = enabled
    }

    suspend fun setSearchPanelAppSearchEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_APP_SEARCH_ENABLED] = enabled
    }

    suspend fun setSearchPanelSettingsSearchEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_SETTINGS_SEARCH_ENABLED] = enabled
    }

    suspend fun setSearchPanelFileTypesEnabled(types: Set<String>) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_FILE_TYPES_ENABLED] = types
    }

    suspend fun setSearchPanelFileShowFolders(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_FILE_SHOW_FOLDERS] = enabled
    }

    suspend fun setSearchPanelFileShowSystemFiles(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_FILE_SHOW_SYSTEM] = enabled
    }

    suspend fun setSearchPanelFilePreviewsEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_FILE_PREVIEWS_ENABLED] = enabled
    }

    suspend fun setSearchPanelFileFolderWhitelist(patterns: Set<String>) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_FILE_FOLDER_WHITELIST] = patterns
    }

    suspend fun setSearchPanelFileFolderBlacklist(patterns: Set<String>) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_FILE_FOLDER_BLACKLIST] = patterns
    }

    suspend fun setSearchPanelPresentationMode(mode: SearchPanelPresentationMode) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_PRESENTATION_MODE] = mode.name
    }

    suspend fun setSearchPanelBarPosition(position: SearchPanelBarPosition) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_BAR_POSITION] = position.name
    }

    suspend fun setSearchPanelListOrder(order: SearchPanelListOrder) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_LIST_ORDER] = order.name
    }

    suspend fun setSearchPanelAppDisplayStyle(style: SearchPanelAppDisplayStyle) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_APP_DISPLAY_STYLE] = style.name
    }

    suspend fun setSearchPanelCalculatorEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_CALCULATOR_ENABLED] = enabled
    }

    suspend fun setSearchPanelBackgroundStyle(style: Int) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_BACKGROUND_STYLE] =
            style.coerceIn(SearchPanelBackgroundStyle.BLUR, SearchPanelBackgroundStyle.WALLPAPER_BLUR)
    }

    suspend fun setSearchPanelBlurRadiusDp(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_BLUR_RADIUS_DP] = value.coerceIn(
            AppSettings.SEARCH_PANEL_BLUR_RADIUS_MIN_DP,
            AppSettings.SEARCH_PANEL_BLUR_RADIUS_MAX_DP,
        )
    }

    suspend fun setSearchPanelDimPercent(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_DIM_PERCENT] = value.coerceIn(
            AppSettings.SEARCH_PANEL_DIM_MIN_PERCENT,
            AppSettings.SEARCH_PANEL_DIM_MAX_PERCENT,
        )
    }

    suspend fun setSearchPanelWebSuggestionsEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_WEB_SUGGESTIONS_ENABLED] = enabled
    }

    suspend fun setSearchPanelWebSuggestionsCount(count: Int) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_WEB_SUGGESTIONS_COUNT] = count.coerceIn(
            AppSettings.SEARCH_PANEL_WEB_SUGGESTIONS_COUNT_MIN,
            AppSettings.SEARCH_PANEL_WEB_SUGGESTIONS_COUNT_MAX,
        )
    }

    suspend fun setSearchPanelHistoryMaxEntries(maxEntries: Int) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_HISTORY_MAX_ENTRIES] =
            SearchPanelHistoryCapacity.coerce(maxEntries)
    }

    suspend fun setSearchPanelSectionAliases(aliases: SearchPanelSectionAliasSettings) = editor.edit {
        it[SettingsPreferenceKeys.SEARCH_PANEL_SECTION_ALIASES_JSON] =
            SearchPanelSectionAliasSettings.toJson(aliases)
    }

    suspend fun setAggregatedImageSearchEngines(configs: List<AggregatedImageSearchEngineConfig>) = editor.edit {
        it[SettingsPreferenceKeys.AGGREGATED_IMAGE_SEARCH_ENGINES_JSON] =
            AggregatedImageSearchEnginePreferencesStore.encode(configs)
        it[SettingsPreferenceKeys.AGGREGATED_IMAGE_SEARCH_ENGINES_INITIALIZED] = true
    }

    suspend fun setCornerGestureEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_ENABLED] = enabled
    }

    suspend fun setCornerGestureLeftEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_LEFT_ENABLED] = enabled
    }

    suspend fun setCornerGestureRightEnabled(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_ENABLED] = enabled
    }

    suspend fun setCornerGestureVerticalEdgeWidthDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_VERTICAL_EDGE_WIDTH_DP] =
            CornerGestureSettings.clampVerticalEdgeWidthDp(value)
    }

    suspend fun setCornerGestureVerticalEdgeHeightDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_VERTICAL_EDGE_HEIGHT_DP] =
            CornerGestureSettings.clampVerticalEdgeHeightDp(value)
    }

    suspend fun setCornerGestureHorizontalEdgeWidthDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_HORIZONTAL_EDGE_WIDTH_DP] =
            CornerGestureSettings.clampHorizontalEdgeWidthDp(value)
    }

    suspend fun setCornerGestureHorizontalEdgeHeightDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_HORIZONTAL_EDGE_HEIGHT_DP] =
            CornerGestureSettings.clampHorizontalEdgeHeightDp(value)
    }

    suspend fun setCornerGestureTriggerSlopDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_TRIGGER_SLOP_DP] = CornerGestureSettings.clampTriggerSlopDp(value)
    }

    suspend fun setCornerGestureHideInLandscape(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_HIDE_LANDSCAPE] = enabled
    }

    suspend fun setCornerGestureLandscapePreventFalseTouch(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_LANDSCAPE_PREVENT_FALSE_TOUCH] = enabled
    }

    suspend fun setCornerGestureOverrideSystemNav(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_OVERRIDE_SYSTEM_NAV] = enabled
    }

    suspend fun setCornerGestureOuterDiameterDp(value: Float) = editor.edit { prefs ->
        val outer = CornerGestureSettings.clampOuterDiameterDp(value)
        prefs[SettingsPreferenceKeys.CORNER_GESTURE_OUTER_DIAMETER_DP] = outer
        val inner = prefs[SettingsPreferenceKeys.CORNER_GESTURE_INNER_DIAMETER_DP] ?: 72f
        prefs[SettingsPreferenceKeys.CORNER_GESTURE_INNER_DIAMETER_DP] =
            CornerGestureSettings.clampInnerDiameterDp(inner, outer)
    }

    suspend fun setCornerGestureInnerDiameterDp(value: Float) = editor.edit { prefs ->
        val outer = prefs[SettingsPreferenceKeys.CORNER_GESTURE_OUTER_DIAMETER_DP] ?: 280f
        prefs[SettingsPreferenceKeys.CORNER_GESTURE_INNER_DIAMETER_DP] =
            CornerGestureSettings.clampInnerDiameterDp(value, outer)
    }

    suspend fun setCornerGestureBubbleSizeDp(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_BUBBLE_SIZE_DP] = CornerGestureSettings.clampBubbleSizeDp(value)
    }

    suspend fun setCornerGestureLeftSlotAction(index: Int, action: GestureAction) = editor.edit { prefs ->
        val current = CornerRadialMenuCodec.decode(
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_LEFT_SLOTS] ?: emptySet(),
            CornerRadialMenuCodec.defaultLeftSlots(),
        )
        val updated = current.toMutableList()
        if (index in 0 until CornerRadialMenuCodec.SLOT_COUNT) {
            updated[index] = action
            val encoded = CornerRadialMenuCodec.encode(updated)
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_LEFT_SLOTS] = encoded
            if (prefs[SettingsPreferenceKeys.CORNER_GESTURE_UNIFIED_SLOTS] ?: true) {
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOTS] = encoded
            }
        }
    }

    suspend fun setCornerSlotSubMenu(
        isLeft: Boolean,
        index: Int,
        config: CornerSlotSubMenuConfig,
    ) = editor.edit { prefs ->
        if (index !in 0 until CornerRadialMenuCodec.SLOT_COUNT) return@edit
        val key = if (isLeft) {
            SettingsPreferenceKeys.CORNER_GESTURE_LEFT_SLOT_SUB_MENUS
        } else {
            SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOT_SUB_MENUS
        }
        val current = CornerSlotSubMenuCodec.decode(
            prefs[key] ?: emptySet(),
            CornerSlotSubMenuCodec.defaultSlotSubMenus(),
        )
        val updated = current.toMutableList()
        updated[index] = config
        val encoded = CornerSlotSubMenuCodec.encode(updated)
        prefs[key] = encoded
        if (prefs[SettingsPreferenceKeys.CORNER_GESTURE_UNIFIED_SLOTS] ?: true) {
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOT_SUB_MENUS] = encoded
        }
    }

    suspend fun setCornerGestureCancelOutsideWheel(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_CANCEL_OUTSIDE_WHEEL] = enabled
    }

    suspend fun setCornerGestureProgressiveLayers(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_PROGRESSIVE_LAYERS] = enabled
    }

    suspend fun setCornerGestureSlotHaptic(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_SLOT_HAPTIC] = enabled
    }

    suspend fun setCornerGestureShowSelectedName(enabled: Boolean) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_SHOW_SELECTED_NAME] = enabled
    }

    suspend fun setCornerGestureSelectedHintIconSizeDp(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_SELECTED_HINT_ICON_SIZE_DP] =
            SelectedHintMetrics.clampIconSizeDp(value)
    }

    suspend fun setCornerGestureBackgroundStyle(style: Int) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_BACKGROUND_STYLE] =
            CornerGestureSettings.clampBackgroundStyle(style)
        // 清理旧壁纸模糊开关，避免读档回退到错误背景。
        it.remove(SettingsPreferenceKeys.CORNER_GESTURE_WALLPAPER_BLUR_ENABLED)
    }

    suspend fun setCornerGestureBlurDp(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_BLUR_DP] = CornerGestureSettings.clampBlurDp(value)
    }

    suspend fun setCornerGestureDimPercent(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.CORNER_GESTURE_DIM_PERCENT] = CornerGestureSettings.clampDimPercent(value)
    }

    suspend fun setCornerGestureUnifiedSlots(enabled: Boolean) = editor.edit { prefs ->
        if (enabled) {
            val left = CornerRadialMenuCodec.decode(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_LEFT_SLOTS] ?: emptySet(),
                CornerRadialMenuCodec.defaultLeftSlots(),
            )
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOTS] = CornerRadialMenuCodec.encode(left)
            val leftSubMenus = CornerSlotSubMenuCodec.decode(
                prefs[SettingsPreferenceKeys.CORNER_GESTURE_LEFT_SLOT_SUB_MENUS] ?: emptySet(),
                CornerSlotSubMenuCodec.defaultSlotSubMenus(),
            )
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOT_SUB_MENUS] =
                CornerSlotSubMenuCodec.encode(leftSubMenus)
        }
        prefs[SettingsPreferenceKeys.CORNER_GESTURE_UNIFIED_SLOTS] = enabled
    }

    suspend fun setCornerGestureInnerZoneAction(action: GestureAction) = editor.edit {
        val sanitized = CornerInnerZoneActionCodec.sanitize(action)
        it[SettingsPreferenceKeys.CORNER_GESTURE_INNER_ZONE_ACTION_PAYLOAD] =
            CornerInnerZoneActionCodec.encode(sanitized)
    }

    suspend fun setCornerGestureRightSlotAction(index: Int, action: GestureAction) = editor.edit { prefs ->
        val current = CornerRadialMenuCodec.decode(
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOTS] ?: emptySet(),
            CornerRadialMenuCodec.defaultRightSlots(),
        )
        val updated = current.toMutableList()
        if (index in 0 until CornerRadialMenuCodec.SLOT_COUNT) {
            updated[index] = action
            prefs[SettingsPreferenceKeys.CORNER_GESTURE_RIGHT_SLOTS] = CornerRadialMenuCodec.encode(updated)
        }
    }

    suspend fun setHolographicLauncherTimeoutSeconds(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.HOLOGRAPHIC_TIMEOUT_SECONDS] = value.coerceIn(
            HolographicLauncherSettings.MIN_TIMEOUT_SECONDS,
            HolographicLauncherSettings.MAX_TIMEOUT_SECONDS,
        )
    }

    suspend fun setHolographicRotationSensitivity(value: Float) = editor.edit {
        it[SettingsPreferenceKeys.HOLOGRAPHIC_ROTATION_SENSITIVITY] = value.coerceIn(
            HolographicLauncherSettings.MIN_ROTATION_SENSITIVITY,
            HolographicLauncherSettings.MAX_ROTATION_SENSITIVITY,
        )
    }

    suspend fun setHolographicHapticLevel(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.HOLOGRAPHIC_HAPTIC_LEVEL] = value.coerceIn(0, 3)
    }

    suspend fun setHolographicBackgroundStyle(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.HOLOGRAPHIC_BACKGROUND_STYLE] = value.coerceIn(
            HolographicLauncherSettings.BACKGROUND_BLUR,
            HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR,
        )
    }

    suspend fun setHolographicBlurDp(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.HOLOGRAPHIC_BLUR_DP] = value.coerceIn(
            HolographicLauncherSettings.MIN_BLUR_DP,
            HolographicLauncherSettings.MAX_BLUR_DP,
        )
    }

    suspend fun setHolographicDimPercent(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.HOLOGRAPHIC_DIM_PERCENT] = value.coerceIn(
            HolographicLauncherSettings.MIN_DIM_PERCENT,
            HolographicLauncherSettings.MAX_DIM_PERCENT,
        )
    }

    suspend fun addHolographicHiddenApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.HOLOGRAPHIC_HIDDEN_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.HOLOGRAPHIC_HIDDEN_APP_PACKAGES] = current
    }

    suspend fun removeHolographicHiddenApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.HOLOGRAPHIC_HIDDEN_APP_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.HOLOGRAPHIC_HIDDEN_APP_PACKAGES] = current
    }
}
