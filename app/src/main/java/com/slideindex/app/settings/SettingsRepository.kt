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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "slide_index_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            serviceEnabled = prefs[SERVICE_ENABLED] ?: false,
            leftEdgeEnabled = prefs[LEFT_EDGE_ENABLED] ?: true,
            rightEdgeEnabled = prefs[RIGHT_EDGE_ENABLED] ?: true,
            edgeTriggerWidthDp = prefs[EDGE_TRIGGER_WIDTH] ?: 20f,
            triggerTopFraction = prefs[TRIGGER_TOP] ?: 0.30f,
            triggerHeightFraction = prefs[TRIGGER_HEIGHT] ?: 0.38f,
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
            themeColorArgb = prefs[THEME_COLOR] ?: 0xFF6750A4.toInt(),
        )
    }

    suspend fun setServiceEnabled(enabled: Boolean) = edit { it[SERVICE_ENABLED] = enabled }
    suspend fun setLeftEdgeEnabled(enabled: Boolean) = edit { it[LEFT_EDGE_ENABLED] = enabled }
    suspend fun setRightEdgeEnabled(enabled: Boolean) = edit { it[RIGHT_EDGE_ENABLED] = enabled }
    suspend fun setEdgeTriggerWidthDp(value: Float) = edit { it[EDGE_TRIGGER_WIDTH] = value }
    suspend fun setTriggerTopFraction(value: Float) = edit { it[TRIGGER_TOP] = value }
    suspend fun setTriggerHeightFraction(value: Float) = edit { it[TRIGGER_HEIGHT] = value }
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
        private val THEME_COLOR = intPreferencesKey("theme_color_argb")
    }
}
