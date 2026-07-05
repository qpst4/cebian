package com.slideindex.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureRuleCodec
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleCodec
import com.slideindex.app.gesture.allTriggerHandles
import com.slideindex.app.gesture.triggerHandle
import com.slideindex.app.gesture.withAddedTriggerHandlePair
import com.slideindex.app.gesture.withRemovedTriggerHandle
import com.slideindex.app.gesture.withSlotAction
import com.slideindex.app.gesture.withSlotTriggerMode
import com.slideindex.app.gesture.withTriggerAlignOppositeSide
import com.slideindex.app.gesture.withUpdatedTriggerHandle
import com.slideindex.app.gesture.withUpdatedTriggerHandleDistances
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "slide_index_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val legacyWidth = prefs[EDGE_TRIGGER_WIDTH] ?: 20f
        val legacyTop = prefs[TRIGGER_TOP] ?: 0.30f
        val legacyHeight = prefs[TRIGGER_HEIGHT] ?: 0.38f
        val leftTop = prefs[LEFT_TRIGGER_TOP] ?: legacyTop
        val rightTop = prefs[RIGHT_TRIGGER_TOP] ?: legacyTop
        val leftHeight = prefs[LEFT_TRIGGER_HEIGHT] ?: legacyHeight
        val rightHeight = prefs[RIGHT_TRIGGER_HEIGHT] ?: legacyHeight
        val legacyShortSwipe = prefs[SHORT_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP
        val legacyLongSwipe = prefs[LONG_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP
        val leftHandles = prefs[LEFT_TRIGGER_HANDLES]?.let {
            TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
        } ?: listOf(TriggerHandle.default(leftTop, leftHeight))
        val rightHandles = prefs[RIGHT_TRIGGER_HANDLES]?.let {
            TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
        } ?: listOf(TriggerHandle.default(rightTop, rightHeight))
        AppSettings(
            serviceEnabled = prefs[SERVICE_ENABLED] ?: false,
            leftEdgeEnabled = prefs[LEFT_EDGE_ENABLED] ?: true,
            rightEdgeEnabled = prefs[RIGHT_EDGE_ENABLED] ?: true,
            leftEdgeTriggerWidthDp = prefs[LEFT_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            rightEdgeTriggerWidthDp = prefs[RIGHT_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            leftTriggerTopFraction = leftTop,
            rightTriggerTopFraction = rightTop,
            leftTriggerHeightFraction = leftHeight,
            rightTriggerHeightFraction = rightHeight,
            leftTriggerHandles = leftHandles,
            rightTriggerHandles = rightHandles,
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
            gestureHintEnabled = prefs[GESTURE_HINT_ENABLED] ?: true,
            gestureHintStyleId = prefs[GESTURE_HINT_STYLE] ?: GestureHintStyle.BUBBLE.id,
            animationStyles = AnimationStyleCodec.decode(prefs[ANIMATION_STYLES]),
            gestureAngleConfig = readGestureAngleConfig(prefs),
            indexHeightFraction = prefs[INDEX_HEIGHT] ?: 0.42f,
            appsPerRow = prefs[APPS_PER_ROW] ?: 3,
            quickLauncherColumnsPerPage = prefs[QUICK_LAUNCHER_COLUMNS_PER_PAGE]
                ?: prefs[APPS_PER_ROW]
                ?: 3,
            quickLauncherRowsPerPage = prefs[QUICK_LAUNCHER_ROWS_PER_PAGE] ?: 4,
            panelOpacity = prefs[PANEL_OPACITY] ?: 0.95f,
            hapticEnabled = prefs[HAPTIC_ENABLED] ?: true,
            hapticStrengthLevel = prefs[HAPTIC_STRENGTH] ?: HapticStrength.MEDIUM.level,
            hideFromRecents = prefs[HIDE_FROM_RECENTS] ?: false,
            accessibilityKeepAliveEnabled = prefs[ACCESSIBILITY_KEEP_ALIVE] ?: false,
            hideTriggerInLandscape = prefs[HIDE_TRIGGER_LANDSCAPE] ?: false,
            hideTriggerOnLockScreen = prefs[HIDE_TRIGGER_LOCK_SCREEN] ?: false,
            hideTriggerOnLauncher = prefs[HIDE_TRIGGER_LAUNCHER] ?: false,
            dynamicColorEnabled = prefs[DYNAMIC_COLOR_ENABLED] ?: false,
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
            quickLauncher = readQuickLauncherItems(prefs),
            shellCommands = ShellCommandCodec.decodeAll(prefs[SHELL_COMMANDS] ?: emptySet()),
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
    }

    suspend fun setTriggerTopFraction(side: PanelSide, value: Float) = edit { prefs ->
        val top = value.coerceIn(0.05f, 0.80f)
        when (side) {
            PanelSide.LEFT -> prefs[LEFT_TRIGGER_TOP] = top
            PanelSide.RIGHT -> prefs[RIGHT_TRIGGER_TOP] = top
        }
    }

    suspend fun setTriggerHeightFraction(side: PanelSide, value: Float) = edit { prefs ->
        val height = value.coerceIn(0.15f, 0.90f)
        when (side) {
            PanelSide.LEFT -> prefs[LEFT_TRIGGER_HEIGHT] = height
            PanelSide.RIGHT -> prefs[RIGHT_TRIGGER_HEIGHT] = height
        }
    }

    suspend fun setTriggerVerticalRange(
        side: PanelSide,
        handleId: String,
        topFraction: Float,
        bottomFraction: Float,
    ) = edit { prefs ->
        val minBound = 0.05f
        val maxBound = 0.95f
        var top = topFraction.coerceIn(minBound, maxBound)
        var bottom = bottomFraction.coerceIn(minBound, maxBound)
        if (bottom < top) {
            val swap = top
            top = bottom
            bottom = swap
        }
        val height = bottom - top
        val current = readTriggerSettings(prefs)
        val sourceHandle = current.triggerHandle(side, handleId)
        var updated = current.withUpdatedTriggerHandle(side, handleId, top, height)
        if (sourceHandle?.alignOppositeSide != false) {
            val otherSide = side.opposite()
            if (updated.triggerHandle(otherSide, handleId) != null) {
                updated = updated.withUpdatedTriggerHandle(otherSide, handleId, top, height)
            }
        }
        writeTriggerHandles(prefs, updated)
        val primaryLeft = updated.leftTriggerHandles.first()
        val primaryRight = updated.rightTriggerHandles.first()
        prefs[LEFT_TRIGGER_TOP] = primaryLeft.topFraction
        prefs[RIGHT_TRIGGER_TOP] = primaryRight.topFraction
        prefs[LEFT_TRIGGER_HEIGHT] = primaryLeft.heightFraction
        prefs[RIGHT_TRIGGER_HEIGHT] = primaryRight.heightFraction
    }

    suspend fun addTriggerHandlePair() = edit { prefs ->
        val current = readTriggerSettings(prefs)
        val updated = current.withAddedTriggerHandlePair()
        writeTriggerHandles(prefs, updated)
    }

    suspend fun removeTriggerHandle(side: PanelSide, handleId: String) = edit { prefs ->
        val current = readTriggerSettings(prefs)
        val updated = current.withRemovedTriggerHandle(side, handleId)
        writeTriggerHandles(prefs, updated)
        prefs[GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
        val primary = updated.allTriggerHandles(side).firstOrNull()
        when (side) {
            PanelSide.LEFT -> primary?.let {
                prefs[LEFT_TRIGGER_TOP] = it.topFraction
                prefs[LEFT_TRIGGER_HEIGHT] = it.heightFraction
            }
            PanelSide.RIGHT -> primary?.let {
                prefs[RIGHT_TRIGGER_TOP] = it.topFraction
                prefs[RIGHT_TRIGGER_HEIGHT] = it.heightFraction
            }
        }
    }

    suspend fun setTriggerAlignOppositeSide(
        handleId: String,
        sourceSide: PanelSide,
        enabled: Boolean,
    ) = edit { prefs ->
        var current = readTriggerSettings(prefs).withTriggerAlignOppositeSide(handleId, enabled)
        if (enabled) {
            val source = current.triggerHandle(sourceSide, handleId)
            if (source != null) {
                val otherSide = sourceSide.opposite()
                if (current.triggerHandle(otherSide, handleId) != null) {
                    current = current.withUpdatedTriggerHandle(
                        side = otherSide,
                        handleId = handleId,
                        topFraction = source.topFraction,
                        heightFraction = source.heightFraction,
                    )
                }
            }
        }
        writeTriggerHandles(prefs, current)
        val primaryLeft = current.leftTriggerHandles.firstOrNull()
        val primaryRight = current.rightTriggerHandles.firstOrNull()
        primaryLeft?.let {
            prefs[LEFT_TRIGGER_TOP] = it.topFraction
            prefs[LEFT_TRIGGER_HEIGHT] = it.heightFraction
        }
        primaryRight?.let {
            prefs[RIGHT_TRIGGER_TOP] = it.topFraction
            prefs[RIGHT_TRIGGER_HEIGHT] = it.heightFraction
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

    suspend fun setShortSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) = edit { prefs ->
        updateTriggerSwipeDistances(prefs, side, handleId, shortSwipeDistanceDp = value)
    }

    suspend fun setLongSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) = edit { prefs ->
        updateTriggerSwipeDistances(prefs, side, handleId, longSwipeDistanceDp = value)
    }

    private fun updateTriggerSwipeDistances(
        prefs: MutablePreferences,
        side: PanelSide,
        handleId: String,
        shortSwipeDistanceDp: Float? = null,
        longSwipeDistanceDp: Float? = null,
    ) {
        val current = readTriggerSettings(prefs)
        val sourceHandle = current.triggerHandle(side, handleId)
        var updated = current.withUpdatedTriggerHandleDistances(
            side = side,
            handleId = handleId,
            shortSwipeDistanceDp = shortSwipeDistanceDp,
            longSwipeDistanceDp = longSwipeDistanceDp,
        )
        if (sourceHandle?.alignOppositeSide != false) {
            val otherSide = side.opposite()
            val synced = updated.triggerHandle(side, handleId) ?: return
            if (updated.triggerHandle(otherSide, handleId) != null) {
                updated = updated.withUpdatedTriggerHandleDistances(
                    side = otherSide,
                    handleId = handleId,
                    shortSwipeDistanceDp = synced.shortSwipeDistanceDp,
                    longSwipeDistanceDp = synced.longSwipeDistanceDp,
                )
            }
        }
        writeTriggerHandles(prefs, updated)
    }

    suspend fun setGestureHintEnabled(enabled: Boolean) = edit { it[GESTURE_HINT_ENABLED] = enabled }

    suspend fun setGestureHintStyle(style: GestureHintStyle) = edit {
        it[GESTURE_HINT_STYLE] = style.id
        style.toAnimationType()?.let { type ->
            val current = AnimationStyleCodec.decode(it[ANIMATION_STYLES])
            it[ANIMATION_STYLES] = AnimationStyleCodec.encode(current.selectType(type))
        }
    }

    suspend fun setAnimationStyles(styles: AnimationStyles) = edit {
        it[ANIMATION_STYLES] = AnimationStyleCodec.encode(styles)
    }

    suspend fun updateWaveStyle(style: WaveStyle) = edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[ANIMATION_STYLES])
        prefs[ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_WAVE, AnimationStyleCodec.encodeWave(style)),
        )
    }

    suspend fun updateCapsuleStyle(style: CapsuleStyle) = edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[ANIMATION_STYLES])
        prefs[ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_CAPSULE, AnimationStyleCodec.encodeCapsule(style)),
        )
    }

    suspend fun updateBubbleStyle(style: BubbleStyle) = edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[ANIMATION_STYLES])
        prefs[ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_BUBBLE, AnimationStyleCodec.encodeBubble(style)),
        )
    }

    suspend fun setGestureAngleConfig(config: GestureAngleConfig) = edit { prefs ->
        val normalized = config.normalized()
        prefs[GESTURE_ANGLE_UP] = normalized.upDegrees
        prefs[GESTURE_ANGLE_UP_RIGHT] = normalized.upRightDegrees
        prefs[GESTURE_ANGLE_IN] = normalized.inDegrees
        prefs[GESTURE_ANGLE_DOWN_RIGHT] = normalized.downRightDegrees
        prefs[GESTURE_ANGLE_DOWN] = normalized.downDegrees
    }
    suspend fun setIndexHeightFraction(value: Float) = edit { it[INDEX_HEIGHT] = value }
    suspend fun setAppsPerRow(value: Int) = edit { it[APPS_PER_ROW] = value.coerceIn(2, 5) }

    suspend fun setQuickLauncherColumnsPerPage(value: Int) =
        edit { it[QUICK_LAUNCHER_COLUMNS_PER_PAGE] = value.coerceIn(2, 5) }

    suspend fun setQuickLauncherRowsPerPage(value: Int) =
        edit { it[QUICK_LAUNCHER_ROWS_PER_PAGE] = value.coerceIn(2, 6) }
    suspend fun setPanelOpacity(value: Float) = edit { it[PANEL_OPACITY] = value }
    suspend fun setHapticEnabled(enabled: Boolean) = edit { it[HAPTIC_ENABLED] = enabled }
    suspend fun setHideFromRecents(enabled: Boolean) = edit { it[HIDE_FROM_RECENTS] = enabled }

    suspend fun setAccessibilityKeepAliveEnabled(enabled: Boolean) =
        edit { it[ACCESSIBILITY_KEEP_ALIVE] = enabled }
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

    suspend fun setHideTriggerInLandscape(enabled: Boolean) =
        edit { it[HIDE_TRIGGER_LANDSCAPE] = enabled }

    suspend fun setHideTriggerOnLockScreen(enabled: Boolean) =
        edit { it[HIDE_TRIGGER_LOCK_SCREEN] = enabled }

    suspend fun setHideTriggerOnLauncher(enabled: Boolean) =
        edit { it[HIDE_TRIGGER_LAUNCHER] = enabled }

    suspend fun setDynamicColorEnabled(enabled: Boolean) =
        edit { it[DYNAMIC_COLOR_ENABLED] = enabled }

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
        handleId: String = TriggerHandle.DEFAULT_ID,
    ) = edit { prefs ->
        val current = AppSettings(
            gestureRules = GestureRuleCodec.decodeAll(prefs[GESTURE_RULES] ?: emptySet()),
        )
        val updated = if (action.type == GestureActionType.NONE) {
            current.withSlotAction(side, trigger, action, handleId)
        } else {
            current
                .withSlotAction(side, trigger, action, handleId)
                .withSlotTriggerMode(side, trigger, triggerMode, handleId)
        }
        prefs[GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
    }

    private fun readTriggerSettings(prefs: Preferences): AppSettings {
        val legacyTop = prefs[TRIGGER_TOP] ?: 0.30f
        val legacyHeight = prefs[TRIGGER_HEIGHT] ?: 0.38f
        val legacyShortSwipe = prefs[SHORT_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP
        val legacyLongSwipe = prefs[LONG_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP
        return AppSettings(
            leftTriggerHandles = prefs[LEFT_TRIGGER_HANDLES]?.let {
                TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
            } ?: listOf(
                TriggerHandle.default(
                    prefs[LEFT_TRIGGER_TOP] ?: legacyTop,
                    prefs[LEFT_TRIGGER_HEIGHT] ?: legacyHeight,
                ),
            ),
            rightTriggerHandles = prefs[RIGHT_TRIGGER_HANDLES]?.let {
                TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
            } ?: listOf(
                TriggerHandle.default(
                    prefs[RIGHT_TRIGGER_TOP] ?: legacyTop,
                    prefs[RIGHT_TRIGGER_HEIGHT] ?: legacyHeight,
                ),
            ),
            gestureRules = GestureRuleCodec.decodeAll(prefs[GESTURE_RULES] ?: emptySet()),
        )
    }

    private fun writeTriggerHandles(prefs: MutablePreferences, settings: AppSettings) {
        prefs[LEFT_TRIGGER_HANDLES] = TriggerHandleCodec.encodeAll(settings.leftTriggerHandles)
        prefs[RIGHT_TRIGGER_HANDLES] = TriggerHandleCodec.encodeAll(settings.rightTriggerHandles)
    }

    suspend fun setQuickLauncherItems(
        items: List<com.slideindex.app.launcher.QuickLauncherItem>,
    ) = edit { prefs ->
        prefs[QUICK_LAUNCHER] = QuickLauncherItemCodec.encodeAll(items)
    }

    private fun readQuickLauncherItems(prefs: Preferences): List<com.slideindex.app.launcher.QuickLauncherItem> {
        val unified = QuickLauncherItemCodec.decodeAll(prefs[QUICK_LAUNCHER] ?: emptySet())
        if (unified.isNotEmpty()) return unified
        val left = QuickLauncherItemCodec.decodeAll(prefs[QUICK_LAUNCHER_LEFT] ?: emptySet())
        if (left.isNotEmpty()) return left
        return QuickLauncherItemCodec.decodeAll(prefs[QUICK_LAUNCHER_RIGHT] ?: emptySet())
    }

    suspend fun setShellCommands(items: List<ShellCommand>) = edit { prefs ->
        prefs[SHELL_COMMANDS] = ShellCommandCodec.encodeAll(items)
    }

    private fun legacyLaunchPolicy(prefs: Preferences): Int {
        return if (prefs[FREE_WINDOW_ENABLED] == true) {
            AppLaunchPolicy.ALWAYS_FREE_WINDOW.id
        } else {
            AppLaunchPolicy.ALWAYS_FULLSCREEN.id
        }
    }

    private fun readGestureAngleConfig(prefs: Preferences): GestureAngleConfig =
        GestureAngleConfig(
            upDegrees = prefs[GESTURE_ANGLE_UP] ?: GestureAngleConfig.DEFAULT_UP,
            upRightDegrees = prefs[GESTURE_ANGLE_UP_RIGHT] ?: GestureAngleConfig.DEFAULT_UP_RIGHT,
            inDegrees = prefs[GESTURE_ANGLE_IN] ?: GestureAngleConfig.DEFAULT_IN,
            downRightDegrees = prefs[GESTURE_ANGLE_DOWN_RIGHT] ?: GestureAngleConfig.DEFAULT_DOWN_RIGHT,
            downDegrees = prefs[GESTURE_ANGLE_DOWN] ?: GestureAngleConfig.DEFAULT_DOWN,
        ).normalized()

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
        private val LEFT_TRIGGER_HANDLES = stringSetPreferencesKey("left_trigger_handles")
        private val RIGHT_TRIGGER_HANDLES = stringSetPreferencesKey("right_trigger_handles")
        private val INTERCEPT_SYSTEM_BACK = booleanPreferencesKey("intercept_system_back_gesture")
        private val LIMIT_MAX_INTERCEPT_LENGTH = booleanPreferencesKey("limit_max_intercept_length")
        private val LEFT_DEFAULT_TRIGGER_MODE = intPreferencesKey("left_default_trigger_mode")
        private val RIGHT_DEFAULT_TRIGGER_MODE = intPreferencesKey("right_default_trigger_mode")
        private val SHORT_SWIPE_DISTANCE_DP = floatPreferencesKey("short_swipe_distance_dp")
        private val LONG_SWIPE_DISTANCE_DP = floatPreferencesKey("long_swipe_distance_dp")
        private val GESTURE_HINT_ENABLED = booleanPreferencesKey("gesture_hint_enabled")
        private val GESTURE_HINT_STYLE = intPreferencesKey("gesture_hint_style")
        private val ANIMATION_STYLES = stringPreferencesKey("animation_styles")
        private val GESTURE_ANGLE_UP = floatPreferencesKey("gesture_angle_up")
        private val GESTURE_ANGLE_UP_RIGHT = floatPreferencesKey("gesture_angle_up_right")
        private val GESTURE_ANGLE_IN = floatPreferencesKey("gesture_angle_in")
        private val GESTURE_ANGLE_DOWN_RIGHT = floatPreferencesKey("gesture_angle_down_right")
        private val GESTURE_ANGLE_DOWN = floatPreferencesKey("gesture_angle_down")
        private val EDGE_TRIGGER_WIDTH = floatPreferencesKey("edge_trigger_width_dp")
        private val TRIGGER_TOP = floatPreferencesKey("trigger_top_fraction")
        private val TRIGGER_HEIGHT = floatPreferencesKey("trigger_height_fraction")
        private val INDEX_HEIGHT = floatPreferencesKey("index_height_fraction")
        private val APPS_PER_ROW = intPreferencesKey("apps_per_row")
        private val QUICK_LAUNCHER_COLUMNS_PER_PAGE = intPreferencesKey("quick_launcher_columns_per_page")
        private val QUICK_LAUNCHER_ROWS_PER_PAGE = intPreferencesKey("quick_launcher_rows_per_page")
        private val PANEL_OPACITY = floatPreferencesKey("panel_opacity")
        private val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private val HAPTIC_STRENGTH = intPreferencesKey("haptic_strength_level")
        private val HIDE_FROM_RECENTS = booleanPreferencesKey("hide_from_recents")
        private val ACCESSIBILITY_KEEP_ALIVE = booleanPreferencesKey("accessibility_keep_alive")
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
        private val QUICK_LAUNCHER = stringSetPreferencesKey("quick_launcher")
        private val QUICK_LAUNCHER_LEFT = stringSetPreferencesKey("quick_launcher_left")
        private val QUICK_LAUNCHER_RIGHT = stringSetPreferencesKey("quick_launcher_right")
        private val SHELL_COMMANDS = stringSetPreferencesKey("shell_commands")
        private val HIDE_TRIGGER_LANDSCAPE = booleanPreferencesKey("hide_trigger_landscape")
        private val HIDE_TRIGGER_LOCK_SCREEN = booleanPreferencesKey("hide_trigger_lock_screen")
        private val HIDE_TRIGGER_LAUNCHER = booleanPreferencesKey("hide_trigger_launcher")
        private val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        private val THEME_COLOR = intPreferencesKey("theme_color_argb")
    }
}
