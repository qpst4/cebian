package com.slideindex.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureRuleCodec
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.withSlotAction
import com.slideindex.app.gesture.withSlotTriggerMode
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.PanelSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "slide_index_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val legacyWidth = prefs[EDGE_TRIGGER_WIDTH] ?: 20f
        val legacyTop = prefs[TRIGGER_TOP] ?: 0.30f
        val legacyHeight = prefs[TRIGGER_HEIGHT] ?: 0.38f
        AppSettings(
            serviceEnabled = prefs[SERVICE_ENABLED] ?: false,
            leftEdgeEnabled = prefs[LEFT_EDGE_ENABLED] ?: true,
            rightEdgeEnabled = prefs[RIGHT_EDGE_ENABLED] ?: true,
            leftEdgeTriggerWidthDp = prefs[LEFT_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            rightEdgeTriggerWidthDp = prefs[RIGHT_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            leftTriggerTopFraction = prefs[LEFT_TRIGGER_TOP] ?: legacyTop,
            rightTriggerTopFraction = prefs[RIGHT_TRIGGER_TOP] ?: legacyTop,
            leftTriggerHeightFraction = prefs[LEFT_TRIGGER_HEIGHT] ?: legacyHeight,
            rightTriggerHeightFraction = prefs[RIGHT_TRIGGER_HEIGHT] ?: legacyHeight,
            alignHandlesEnabled = prefs[ALIGN_HANDLES_ENABLED] ?: true,
            interceptSystemBackGesture = prefs[INTERCEPT_SYSTEM_BACK] ?: false,
            limitMaxInterceptLength = prefs[LIMIT_MAX_INTERCEPT_LENGTH] ?: false,
            leftDefaultTriggerMode = GestureTriggerMode.fromId(
                prefs[LEFT_DEFAULT_TRIGGER_MODE] ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            rightDefaultTriggerMode = GestureTriggerMode.fromId(
                prefs[RIGHT_DEFAULT_TRIGGER_MODE] ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            shortSwipeDistanceDp = prefs[SHORT_SWIPE_DISTANCE_DP] ?: 60f,
            longSwipeDistanceDp = prefs[LONG_SWIPE_DISTANCE_DP] ?: 120f,
            indexHeightFraction = prefs[INDEX_HEIGHT] ?: 0.42f,
            appsPerRow = prefs[APPS_PER_ROW] ?: 3,
            panelOpacity = prefs[PANEL_OPACITY] ?: 0.95f,
            hapticEnabled = prefs[HAPTIC_ENABLED] ?: true,
            hapticStrengthLevel = prefs[HAPTIC_STRENGTH] ?: HapticStrength.MEDIUM.level,
            freeWindowEnabled = prefs[FREE_WINDOW_ENABLED] ?: false,
            freeWindowModeId = prefs[FREE_WINDOW_MODE] ?: FreeWindowMode.detectDefault().id,
            freeWindowWidthFraction = prefs[FREE_WINDOW_WIDTH] ?: 0.8f,
            freeWindowHeightFraction = prefs[FREE_WINDOW_HEIGHT] ?: 0.55f,
            freeWindowLeftFraction = prefs[FREE_WINDOW_LEFT] ?: 0.1f,
            freeWindowTopFraction = prefs[FREE_WINDOW_TOP] ?: 0.15f,
            appLaunchPolicyId = prefs[APP_LAUNCH_POLICY] ?: legacyLaunchPolicy(prefs),
            longPressLaunchDurationMs = prefs[LONG_PRESS_LAUNCH_DURATION] ?: 450,
            hiddenAppPackages = prefs[HIDDEN_APP_PACKAGES] ?: emptySet(),
            excludedTriggerAppPackages = prefs[EXCLUDED_TRIGGER_APP_PACKAGES] ?: emptySet(),
            gestureRules = GestureRuleCodec.decodeAll(prefs[GESTURE_RULES] ?: emptySet()),
            quickLauncherLeft = QuickLauncherItemCodec.decodeAll(prefs[QUICK_LAUNCHER_LEFT] ?: emptySet()),
            quickLauncherRight = QuickLauncherItemCodec.decodeAll(prefs[QUICK_LAUNCHER_RIGHT] ?: emptySet()),
            themeColorArgb = prefs[THEME_COLOR] ?: 0xFF6750A4.toInt(),
        )
    }

    suspend fun setServiceEnabled(enabled: Boolean) = edit { it[SERVICE_ENABLED] = enabled }
    suspend fun setLeftEdgeEnabled(enabled: Boolean) = edit { it[LEFT_EDGE_ENABLED] = enabled }
    suspend fun setRightEdgeEnabled(enabled: Boolean) = edit { it[RIGHT_EDGE_ENABLED] = enabled }

    suspend fun setEdgeTriggerWidthDp(side: PanelSide, value: Float) = edit { prefs ->
        val width = value.coerceIn(12f, 36f)
        when (side) {
            PanelSide.LEFT -> prefs[LEFT_EDGE_TRIGGER_WIDTH] = width
            PanelSide.RIGHT -> prefs[RIGHT_EDGE_TRIGGER_WIDTH] = width
        }
        if (prefs[ALIGN_HANDLES_ENABLED] != false) {
            prefs[LEFT_EDGE_TRIGGER_WIDTH] = width
            prefs[RIGHT_EDGE_TRIGGER_WIDTH] = width
        }
    }

    suspend fun setTriggerTopFraction(side: PanelSide, value: Float) = edit { prefs ->
        val top = value.coerceIn(0.05f, 0.80f)
        when (side) {
            PanelSide.LEFT -> prefs[LEFT_TRIGGER_TOP] = top
            PanelSide.RIGHT -> prefs[RIGHT_TRIGGER_TOP] = top
        }
        if (prefs[ALIGN_HANDLES_ENABLED] != false) {
            prefs[LEFT_TRIGGER_TOP] = top
            prefs[RIGHT_TRIGGER_TOP] = top
        }
    }

    suspend fun setTriggerHeightFraction(side: PanelSide, value: Float) = edit { prefs ->
        val height = value.coerceIn(0.15f, 0.90f)
        when (side) {
            PanelSide.LEFT -> prefs[LEFT_TRIGGER_HEIGHT] = height
            PanelSide.RIGHT -> prefs[RIGHT_TRIGGER_HEIGHT] = height
        }
        if (prefs[ALIGN_HANDLES_ENABLED] != false) {
            prefs[LEFT_TRIGGER_HEIGHT] = height
            prefs[RIGHT_TRIGGER_HEIGHT] = height
        }
    }

    suspend fun setTriggerVerticalRange(side: PanelSide, topFraction: Float, bottomFraction: Float) = edit { prefs ->
        val minTop = 0.05f
        val maxBottom = 0.95f
        val minHeight = 0.15f
        var top = topFraction.coerceIn(minTop, maxBottom - minHeight)
        var bottom = bottomFraction.coerceIn(top + minHeight, maxBottom)
        if (bottom - top < minHeight) {
            bottom = (top + minHeight).coerceAtMost(maxBottom)
            top = (bottom - minHeight).coerceAtLeast(minTop)
        }
        val height = bottom - top
        when (side) {
            PanelSide.LEFT -> {
                prefs[LEFT_TRIGGER_TOP] = top
                prefs[LEFT_TRIGGER_HEIGHT] = height
            }
            PanelSide.RIGHT -> {
                prefs[RIGHT_TRIGGER_TOP] = top
                prefs[RIGHT_TRIGGER_HEIGHT] = height
            }
        }
        if (prefs[ALIGN_HANDLES_ENABLED] != false) {
            prefs[LEFT_TRIGGER_TOP] = top
            prefs[RIGHT_TRIGGER_TOP] = top
            prefs[LEFT_TRIGGER_HEIGHT] = height
            prefs[RIGHT_TRIGGER_HEIGHT] = height
        }
    }

    suspend fun setAlignHandlesEnabled(enabled: Boolean) = edit { prefs ->
        prefs[ALIGN_HANDLES_ENABLED] = enabled
        if (enabled) {
            val width = prefs[LEFT_EDGE_TRIGGER_WIDTH] ?: prefs[EDGE_TRIGGER_WIDTH] ?: 20f
            val top = prefs[LEFT_TRIGGER_TOP] ?: prefs[TRIGGER_TOP] ?: 0.30f
            val height = prefs[LEFT_TRIGGER_HEIGHT] ?: prefs[TRIGGER_HEIGHT] ?: 0.38f
            prefs[RIGHT_EDGE_TRIGGER_WIDTH] = width
            prefs[RIGHT_EDGE_TRIGGER_WIDTH] = width
            prefs[LEFT_TRIGGER_TOP] = top
            prefs[RIGHT_TRIGGER_TOP] = top
            prefs[LEFT_TRIGGER_HEIGHT] = height
            prefs[RIGHT_TRIGGER_HEIGHT] = height
        }
    }

    suspend fun setInterceptSystemBackGesture(enabled: Boolean) = edit { it[INTERCEPT_SYSTEM_BACK] = enabled }
    suspend fun setLimitMaxInterceptLength(enabled: Boolean) = edit { it[LIMIT_MAX_INTERCEPT_LENGTH] = enabled }

    suspend fun setDefaultTriggerMode(side: PanelSide, mode: GestureTriggerMode) = edit { prefs ->
        val resolved = if (mode == GestureTriggerMode.DEFAULT) GestureTriggerMode.ON_RELEASE else mode
        when (side) {
            PanelSide.LEFT -> prefs[LEFT_DEFAULT_TRIGGER_MODE] = resolved.id
            PanelSide.RIGHT -> prefs[RIGHT_DEFAULT_TRIGGER_MODE] = resolved.id
        }
    }

    suspend fun setShortSwipeDistanceDp(value: Float) = edit {
        val short = value.roundToInt().toFloat().coerceIn(24f, 160f)
        it[SHORT_SWIPE_DISTANCE_DP] = short
        val long = it[LONG_SWIPE_DISTANCE_DP] ?: 120f
        if (long < short + 16f) {
            it[LONG_SWIPE_DISTANCE_DP] = (short + 16f).coerceAtMost(240f)
        }
    }

    suspend fun setLongSwipeDistanceDp(value: Float) = edit {
        val short = it[SHORT_SWIPE_DISTANCE_DP] ?: 60f
        it[LONG_SWIPE_DISTANCE_DP] = value.roundToInt().toFloat().coerceIn(short + 16f, 240f)
    }
    suspend fun setIndexHeightFraction(value: Float) = edit { it[INDEX_HEIGHT] = value }
    suspend fun setAppsPerRow(value: Int) = edit { it[APPS_PER_ROW] = value.coerceIn(2, 5) }
    suspend fun setPanelOpacity(value: Float) = edit { it[PANEL_OPACITY] = value }
    suspend fun setHapticEnabled(enabled: Boolean) = edit { it[HAPTIC_ENABLED] = enabled }
    suspend fun setHapticStrengthLevel(level: Int) = edit {
        it[HAPTIC_STRENGTH] = level.coerceIn(
            HapticStrength.LIGHT.level,
            HapticStrength.STRONG.level,
        )
    }
    suspend fun setFreeWindowEnabled(enabled: Boolean) = edit { it[FREE_WINDOW_ENABLED] = enabled }
    suspend fun setFreeWindowModeId(id: Int) = edit {
        it[FREE_WINDOW_MODE] = FreeWindowMode.fromId(id).id
    }
    suspend fun setFreeWindowLayout(
        widthFraction: Float,
        heightFraction: Float,
        leftFraction: Float,
        topFraction: Float,
    ) = edit {
        it[FREE_WINDOW_WIDTH] = widthFraction.coerceIn(0.35f, 0.95f)
        it[FREE_WINDOW_HEIGHT] = heightFraction.coerceIn(0.35f, 0.9f)
        it[FREE_WINDOW_LEFT] = leftFraction.coerceIn(0f, 0.65f)
        it[FREE_WINDOW_TOP] = topFraction.coerceIn(0f, 0.65f)
    }
    suspend fun setAppLaunchPolicyId(id: Int) = edit {
        it[APP_LAUNCH_POLICY] = AppLaunchPolicy.fromId(id).id
    }
    suspend fun setLongPressLaunchDurationMs(value: Int) = edit {
        it[LONG_PRESS_LAUNCH_DURATION] = value.coerceIn(250, 900)
    }
    suspend fun addHiddenApp(packageName: String) = edit {
        val current = it[HIDDEN_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[HIDDEN_APP_PACKAGES] = current
    }
    suspend fun removeHiddenApp(packageName: String) = edit {
        val current = it[HIDDEN_APP_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[HIDDEN_APP_PACKAGES] = current
    }
    suspend fun addExcludedTriggerApp(packageName: String) = edit {
        val current = it[EXCLUDED_TRIGGER_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[EXCLUDED_TRIGGER_APP_PACKAGES] = current
    }
    suspend fun removeExcludedTriggerApp(packageName: String) = edit {
        val current = it[EXCLUDED_TRIGGER_APP_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[EXCLUDED_TRIGGER_APP_PACKAGES] = current
    }
    suspend fun setThemeColor(argb: Int) = edit { it[THEME_COLOR] = argb }

    suspend fun upsertGestureRule(rule: GestureRule) = edit { prefs ->
        val current = GestureRuleCodec.decodeAll(prefs[GESTURE_RULES] ?: emptySet())
            .filterNot { it.id == rule.id }
        prefs[GESTURE_RULES] = GestureRuleCodec.encodeAll(current + rule)
    }

    suspend fun removeGestureRule(id: String) = edit { prefs ->
        val current = GestureRuleCodec.decodeAll(prefs[GESTURE_RULES] ?: emptySet())
            .filterNot { it.id == id }
        prefs[GESTURE_RULES] = GestureRuleCodec.encodeAll(current)
    }

    suspend fun setSlotAction(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
    ) = edit { prefs ->
        val current = AppSettings(
            gestureRules = GestureRuleCodec.decodeAll(prefs[GESTURE_RULES] ?: emptySet()),
        )
        prefs[GESTURE_RULES] = GestureRuleCodec.encodeAll(
            current.withSlotAction(side, trigger, action).gestureRules,
        )
    }

    suspend fun setSlotTriggerMode(
        side: PanelSide,
        trigger: GestureTriggerType,
        triggerMode: GestureTriggerMode,
    ) = edit { prefs ->
        val current = AppSettings(
            gestureRules = GestureRuleCodec.decodeAll(prefs[GESTURE_RULES] ?: emptySet()),
        )
        prefs[GESTURE_RULES] = GestureRuleCodec.encodeAll(
            current.withSlotTriggerMode(side, trigger, triggerMode).gestureRules,
        )
    }

    suspend fun setSlotConfig(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        triggerMode: GestureTriggerMode,
    ) = edit { prefs ->
        val current = AppSettings(
            gestureRules = GestureRuleCodec.decodeAll(prefs[GESTURE_RULES] ?: emptySet()),
        )
        val updated = if (action.type == GestureActionType.NONE) {
            current.withSlotAction(side, trigger, action)
        } else {
            current
                .withSlotAction(side, trigger, action)
                .withSlotTriggerMode(side, trigger, triggerMode)
        }
        prefs[GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
    }

    suspend fun setQuickLauncherItems(
        side: PanelSide,
        items: List<com.slideindex.app.launcher.QuickLauncherItem>,
    ) = edit { prefs ->
        val encoded = QuickLauncherItemCodec.encodeAll(items)
        when (side) {
            PanelSide.LEFT -> prefs[QUICK_LAUNCHER_LEFT] = encoded
            PanelSide.RIGHT -> prefs[QUICK_LAUNCHER_RIGHT] = encoded
        }
    }

    private fun legacyLaunchPolicy(prefs: Preferences): Int {
        return if (prefs[FREE_WINDOW_ENABLED] == true) {
            AppLaunchPolicy.ALWAYS_FREE_WINDOW.id
        } else {
            AppLaunchPolicy.ALWAYS_FULLSCREEN.id
        }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs ->
            block(prefs)
        }
    }

    companion object {
        private val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val LEFT_EDGE_ENABLED = booleanPreferencesKey("left_edge_enabled")
        private val RIGHT_EDGE_ENABLED = booleanPreferencesKey("right_edge_enabled")
        private val LEFT_EDGE_TRIGGER_WIDTH = floatPreferencesKey("left_edge_trigger_width_dp")
        private val RIGHT_EDGE_TRIGGER_WIDTH = floatPreferencesKey("right_edge_trigger_width_dp")
        private val LEFT_TRIGGER_TOP = floatPreferencesKey("left_trigger_top_fraction")
        private val RIGHT_TRIGGER_TOP = floatPreferencesKey("right_trigger_top_fraction")
        private val LEFT_TRIGGER_HEIGHT = floatPreferencesKey("left_trigger_height_fraction")
        private val RIGHT_TRIGGER_HEIGHT = floatPreferencesKey("right_trigger_height_fraction")
        private val ALIGN_HANDLES_ENABLED = booleanPreferencesKey("align_handles_enabled")
        private val INTERCEPT_SYSTEM_BACK = booleanPreferencesKey("intercept_system_back_gesture")
        private val LIMIT_MAX_INTERCEPT_LENGTH = booleanPreferencesKey("limit_max_intercept_length")
        private val LEFT_DEFAULT_TRIGGER_MODE = intPreferencesKey("left_default_trigger_mode")
        private val RIGHT_DEFAULT_TRIGGER_MODE = intPreferencesKey("right_default_trigger_mode")
        private val SHORT_SWIPE_DISTANCE_DP = floatPreferencesKey("short_swipe_distance_dp")
        private val LONG_SWIPE_DISTANCE_DP = floatPreferencesKey("long_swipe_distance_dp")
        private val EDGE_TRIGGER_WIDTH = floatPreferencesKey("edge_trigger_width_dp")
        private val TRIGGER_TOP = floatPreferencesKey("trigger_top_fraction")
        private val TRIGGER_HEIGHT = floatPreferencesKey("trigger_height_fraction")
        private val INDEX_HEIGHT = floatPreferencesKey("index_height_fraction")
        private val APPS_PER_ROW = intPreferencesKey("apps_per_row")
        private val PANEL_OPACITY = floatPreferencesKey("panel_opacity")
        private val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private val HAPTIC_STRENGTH = intPreferencesKey("haptic_strength_level")
        private val FREE_WINDOW_ENABLED = booleanPreferencesKey("free_window_enabled")
        private val FREE_WINDOW_MODE = intPreferencesKey("free_window_mode_id")
        private val FREE_WINDOW_WIDTH = floatPreferencesKey("free_window_width_fraction")
        private val FREE_WINDOW_HEIGHT = floatPreferencesKey("free_window_height_fraction")
        private val FREE_WINDOW_LEFT = floatPreferencesKey("free_window_left_fraction")
        private val FREE_WINDOW_TOP = floatPreferencesKey("free_window_top_fraction")
        private val APP_LAUNCH_POLICY = intPreferencesKey("app_launch_policy_id")
        private val LONG_PRESS_LAUNCH_DURATION = intPreferencesKey("long_press_launch_duration_ms")
        private val HIDDEN_APP_PACKAGES = stringSetPreferencesKey("hidden_app_packages")
        private val EXCLUDED_TRIGGER_APP_PACKAGES = stringSetPreferencesKey("excluded_trigger_app_packages")
        private val GESTURE_RULES = stringSetPreferencesKey("gesture_rules")
        private val QUICK_LAUNCHER_LEFT = stringSetPreferencesKey("quick_launcher_left")
        private val QUICK_LAUNCHER_RIGHT = stringSetPreferencesKey("quick_launcher_right")
        private val THEME_COLOR = intPreferencesKey("theme_color_argb")
    }
}
