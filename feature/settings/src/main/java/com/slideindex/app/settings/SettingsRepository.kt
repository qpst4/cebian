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
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.TriggerDesignPresets
import com.slideindex.app.gesture.coerceInLimits
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageAppFilterCodec
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.message.MessageThemeIds
import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.otp.OtpMatchRuleCodec
import com.slideindex.app.shake.ShakeGestureCodec
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.widget.WidgetPanelCodec
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "slide_index_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cachedSettings: AppSettings = AppSettings()

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val legacyWidth = prefs[SettingsPreferenceKeys.EDGE_TRIGGER_WIDTH] ?: 20f
        val legacyTop = prefs[SettingsPreferenceKeys.TRIGGER_TOP] ?: 0.30f
        val legacyHeight = prefs[SettingsPreferenceKeys.TRIGGER_HEIGHT] ?: 0.38f
        val leftTop = prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] ?: legacyTop
        val rightTop = prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] ?: legacyTop
        val leftHeight = prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] ?: legacyHeight
        val rightHeight = prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] ?: legacyHeight
        val legacyShortSwipe = prefs[SettingsPreferenceKeys.SHORT_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP
        val legacyLongSwipe = prefs[SettingsPreferenceKeys.LONG_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP
        val leftHandles = prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES]?.let {
            TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
        } ?: listOf(TriggerHandle.default(leftTop, leftHeight))
        val rightHandles = prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES]?.let {
            TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
        } ?: listOf(TriggerHandle.default(rightTop, rightHeight))
        AppSettings(
            serviceEnabled = prefs[SettingsPreferenceKeys.SERVICE_ENABLED] ?: false,
            leftEdgeEnabled = prefs[SettingsPreferenceKeys.LEFT_EDGE_ENABLED] ?: true,
            rightEdgeEnabled = prefs[SettingsPreferenceKeys.RIGHT_EDGE_ENABLED] ?: true,
            leftEdgeTriggerWidthDp = prefs[SettingsPreferenceKeys.LEFT_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            rightEdgeTriggerWidthDp = prefs[SettingsPreferenceKeys.RIGHT_EDGE_TRIGGER_WIDTH] ?: legacyWidth,
            leftTriggerTopFraction = leftTop,
            rightTriggerTopFraction = rightTop,
            leftTriggerHeightFraction = leftHeight,
            rightTriggerHeightFraction = rightHeight,
            leftTriggerHandles = leftHandles,
            rightTriggerHandles = rightHandles,
            interceptSystemBackGesture = prefs[SettingsPreferenceKeys.INTERCEPT_SYSTEM_BACK] ?: false,
            limitMaxInterceptLength = prefs[SettingsPreferenceKeys.LIMIT_MAX_INTERCEPT_LENGTH] ?: false,
            leftDefaultTriggerMode = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE] ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            rightDefaultTriggerMode = GestureTriggerMode.fromId(
                prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE] ?: GestureTriggerMode.ON_RELEASE.id,
            ),
            shortSwipeDistanceDp = prefs[SettingsPreferenceKeys.SHORT_SWIPE_DISTANCE_DP] ?: 60f,
            longSwipeDistanceDp = prefs[SettingsPreferenceKeys.LONG_SWIPE_DISTANCE_DP] ?: 120f,
            gestureHintEnabled = prefs[SettingsPreferenceKeys.GESTURE_HINT_ENABLED] ?: true,
            gestureHintStyleId = prefs[SettingsPreferenceKeys.GESTURE_HINT_STYLE] ?: GestureHintStyle.BUBBLE.id,
            animationStyles = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES]),
            gestureAngleConfig = readGestureAngleConfig(prefs),
            indexHeightFraction = prefs[SettingsPreferenceKeys.INDEX_HEIGHT] ?: 0.42f,
            appsPerRow = prefs[SettingsPreferenceKeys.APPS_PER_ROW] ?: 3,
            quickLauncherColumnsPerPage = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_COLUMNS_PER_PAGE]
                ?: prefs[SettingsPreferenceKeys.APPS_PER_ROW]
                ?: 3,
            quickLauncherRowsPerPage = prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_ROWS_PER_PAGE] ?: 4,
            panelOpacity = prefs[SettingsPreferenceKeys.PANEL_OPACITY] ?: 0.95f,
            hapticEnabled = prefs[SettingsPreferenceKeys.HAPTIC_ENABLED] ?: true,
            hapticStrengthLevel = prefs[SettingsPreferenceKeys.HAPTIC_STRENGTH] ?: HapticStrength.MEDIUM.level,
            hideFromRecents = prefs[SettingsPreferenceKeys.HIDE_FROM_RECENTS] ?: false,
            accessibilityKeepAliveEnabled = prefs[SettingsPreferenceKeys.ACCESSIBILITY_KEEP_ALIVE] ?: false,
            hideTriggerInLandscape = prefs[SettingsPreferenceKeys.HIDE_TRIGGER_LANDSCAPE] ?: false,
            hideTriggerOnLockScreen = prefs[SettingsPreferenceKeys.HIDE_TRIGGER_LOCK_SCREEN] ?: false,
            hideTriggerOnLauncher = prefs[SettingsPreferenceKeys.HIDE_TRIGGER_LAUNCHER] ?: false,
            dynamicColorEnabled = prefs[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            freeWindowEnabled = prefs[SettingsPreferenceKeys.FREE_WINDOW_ENABLED] ?: false,
            freeWindowModeId = prefs[SettingsPreferenceKeys.FREE_WINDOW_MODE] ?: FreeWindowMode.detectDefault().id,
            freeWindowWidthFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_WIDTH] ?: 0.8f,
            freeWindowHeightFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_HEIGHT] ?: 0.55f,
            freeWindowLeftFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_LEFT] ?: 0.1f,
            freeWindowTopFraction = prefs[SettingsPreferenceKeys.FREE_WINDOW_TOP] ?: 0.15f,
            appLaunchPolicyId = prefs[SettingsPreferenceKeys.APP_LAUNCH_POLICY] ?: legacyLaunchPolicy(prefs),
            longPressLaunchDurationMs = prefs[SettingsPreferenceKeys.LONG_PRESS_LAUNCH_DURATION] ?: 450,
            hiddenAppPackages = prefs[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES] ?: emptySet(),
            excludedTriggerAppPackages = prefs[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] ?: emptySet(),
            gestureRules = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet()),
            quickLauncher = readQuickLauncherItems(prefs),
            shellCommands = ShellCommandCodec.decodeAll(prefs[SettingsPreferenceKeys.SHELL_COMMANDS] ?: emptySet()),
            themeColorArgb = prefs[SettingsPreferenceKeys.THEME_COLOR] ?: 0xFF6750A4.toInt(),
            widgetPanelPages = WidgetPanelCodec.decodeAll(prefs[SettingsPreferenceKeys.WIDGET_PANEL_PAGES] ?: emptySet()),
            widgetPanelWidthFraction = prefs[SettingsPreferenceKeys.WIDGET_PANEL_WIDTH] ?: 0.8f,
            widgetPanelHeightFraction = prefs[SettingsPreferenceKeys.WIDGET_PANEL_HEIGHT] ?: 0.55f,
            widgetPanelTopFraction = prefs[SettingsPreferenceKeys.WIDGET_PANEL_TOP] ?: 0.15f,
            widgetPanelBlurEnabled = prefs[SettingsPreferenceKeys.WIDGET_PANEL_BLUR] ?: true,
            floatingPointerJoystickAreaWidthPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_WIDTH] ?: 703f,
            floatingPointerJoystickAreaHeightPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_HEIGHT] ?: 711f,
            floatingPointerJoystickAreaZoomFraction = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_ZOOM] ?: 0.8f,
            floatingPointerMatchJoystickToScreenAspect = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_MATCH_ASPECT] ?: false,
            floatingPointerJoystickDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_SIZE] ?: 275f,
            floatingPointerPointerDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_POINTER_SIZE] ?: 100f,
            floatingPointerDesignId = prefs[SettingsPreferenceKeys.FLOATING_POINTER_DESIGN_ID] ?: FloatingPointerDesignIds.RING,
            floatingPointerRingThicknessPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RING_THICKNESS] ?: 12f,
            floatingPointerDotDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_DOT_DIAMETER] ?: 15f,
            floatingPointerRingColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RING_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerFillColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_FILL_COLOR] ?: 0x19000000,
            floatingPointerDotColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_DOT_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerClickVisualFeedbackEnabled = prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_VISUAL_FEEDBACK] ?: true,
            floatingPointerClickHapticEnabled = prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_HAPTIC] ?: true,
            floatingPointerRippleColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_COLOR] ?: 0xFFFD746C.toInt(),
            floatingPointerRippleSizeDp = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_SIZE_DP] ?: 80f,
            floatingPointerRippleDurationMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_DURATION_MS] ?: 500,
            floatingPointerTrailTypeId = prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_TYPE] ?: FloatingPointerTrailType.HIGH_DETAIL.id,
            floatingPointerTrailDurationMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_DURATION] ?: 150,
            floatingPointerTrailColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_COLOR] ?: 0x66FF5252,
            floatingPointerHideWhenJoystickReleased = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_ON_RELEASE] ?: false,
            floatingPointerJoystickInnerColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_INNER_COLOR] ?: 0x80FFFFFF.toInt(),
            floatingPointerJoystickOuterColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_OUTER_COLOR] ?: 0x80C0C0C0.toInt(),
            floatingPointerJoystickGradientRadiusFraction = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_GRADIENT] ?: 1f,
            floatingPointerHideOnOutsideClick = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_OUTSIDE_CLICK] ?: true,
            floatingPointerHideOnQuickSwipe = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_QUICK_SWIPE] ?: true,
            floatingPointerHideWhenIdle = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_IDLE] ?: true,
            floatingPointerIdleHideDelayMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_IDLE_DELAY] ?: 3000,
            floatingPointerRadialMenuEnabled = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ENABLED] ?: true,
            floatingPointerRadialAlwaysVisible = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ALWAYS_VISIBLE] ?: false,
            floatingPointerRadialLongPressMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_LONG_PRESS_MS] ?: 500,
            floatingPointerRadialOuterDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_SIZE] ?: 440f,
            floatingPointerRadialInnerDiameterPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_SIZE] ?: 192f,
            floatingPointerRadialOuterColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_COLOR] ?: 0xE62B3D4F.toInt(),
            floatingPointerRadialInnerColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_COLOR] ?: 0xE61A1A28.toInt(),
            floatingPointerRadialDividerThicknessPx = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_SIZE] ?: 4f,
            floatingPointerRadialDividerColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_COLOR] ?: 0x22FFFFFF,
            floatingPointerRadialIconSizeFraction = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_SIZE] ?: 0.85f,
            floatingPointerRadialIconColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerRadialSlotActions = FloatingPointerRadialMenuCodec.decode(
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_SLOTS] ?: emptySet(),
            ),
            otpCopyToClipboard = prefs[SettingsPreferenceKeys.OTP_COPY_TO_CLIPBOARD] ?: false,
            otpKeywordsRegex = resolveOtpKeywordsRegex(prefs[SettingsPreferenceKeys.OTP_KEYWORDS_REGEX]),
            otpUserMatchRules = OtpMatchRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.OTP_USER_MATCH_RULES] ?: emptySet()),
            otpDisabledOfficialRuleIds = prefs[SettingsPreferenceKeys.OTP_DISABLED_OFFICIAL_RULE_IDS] ?: emptySet(),
            otpAutoInputEnabled = prefs[SettingsPreferenceKeys.OTP_AUTO_INPUT_ENABLED] ?: false,
            otpAutoConfirmEnabled = prefs[SettingsPreferenceKeys.OTP_AUTO_CONFIRM_ENABLED] ?: false,
            otpAutoInputDelayMs = prefs[SettingsPreferenceKeys.OTP_AUTO_INPUT_DELAY_MS] ?: 0,
            otpAutoInputIntervalMs = prefs[SettingsPreferenceKeys.OTP_AUTO_INPUT_INTERVAL_MS] ?: 0,
            otpLsposedSmsCaptureEnabled = prefs[SettingsPreferenceKeys.OTP_LSPOSED_SMS_CAPTURE_ENABLED] ?: false,
            otpLsposedSystemInjectEnabled = prefs[SettingsPreferenceKeys.OTP_LSPOSED_SYSTEM_INJECT_ENABLED] ?: true,
            shakeGestureSettings = readShakeGestureSettings(prefs),
            messageReminderSettings = readMessageReminderSettings(prefs),
            debugPerformanceMonitorEnabled = prefs[SettingsPreferenceKeys.DEBUG_PERFORMANCE_MONITOR] ?: false,
        )
    }

    init {
        cacheScope.launch {
            settings.collect { cachedSettings = it }
        }
    }

    fun readSnapshot(): AppSettings = cachedSettings

    suspend fun setServiceEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.SERVICE_ENABLED] = enabled }
    suspend fun setLeftEdgeEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.LEFT_EDGE_ENABLED] = enabled }
    suspend fun setRightEdgeEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.RIGHT_EDGE_ENABLED] = enabled }

    suspend fun setEdgeTriggerWidthDp(side: PanelSide, value: Float) = edit { prefs ->
        val width = value.coerceIn(12f, 36f)
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_EDGE_TRIGGER_WIDTH] = width
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_EDGE_TRIGGER_WIDTH] = width
        }
    }

    suspend fun setTriggerTopFraction(side: PanelSide, value: Float) = edit { prefs ->
        val top = value.coerceIn(0.05f, 0.80f)
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = top
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = top
        }
    }

    suspend fun setTriggerHeightFraction(side: PanelSide, value: Float) = edit { prefs ->
        val height = value.coerceIn(0.15f, 0.90f)
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] = height
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] = height
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
        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = primaryLeft.topFraction
        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = primaryRight.topFraction
        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] = primaryLeft.heightFraction
        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] = primaryRight.heightFraction
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
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
        val primary = updated.allTriggerHandles(side).firstOrNull()
        when (side) {
            PanelSide.LEFT -> primary?.let {
                prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = it.topFraction
                prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] = it.heightFraction
            }
            PanelSide.RIGHT -> primary?.let {
                prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = it.topFraction
                prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] = it.heightFraction
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
                    current = current.withSyncedTriggerHandleDesign(
                        sourceSide = sourceSide,
                        handleId = handleId,
                        design = source.design,
                    )
                }
            }
        }
        writeTriggerHandles(prefs, current)
        val primaryLeft = current.leftTriggerHandles.firstOrNull()
        val primaryRight = current.rightTriggerHandles.firstOrNull()
        primaryLeft?.let {
            prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = it.topFraction
            prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] = it.heightFraction
        }
        primaryRight?.let {
            prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = it.topFraction
            prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] = it.heightFraction
        }
    }

    suspend fun setTriggerHandleDesign(
        side: PanelSide,
        handleId: String,
        design: TriggerHandleDesign,
    ) = edit { prefs ->
        val current = readTriggerSettings(prefs)
        val updated = current.withSyncedTriggerHandleDesign(
            sourceSide = side,
            handleId = handleId,
            design = design.coerceInLimits(),
        )
        writeTriggerHandles(prefs, updated)
    }

    suspend fun applyTriggerDesignPreset(
        side: PanelSide,
        handleId: String,
        preset: TriggerDesignPreset,
    ) = setTriggerHandleDesign(
        side = side,
        handleId = handleId,
        design = TriggerDesignPresets.apply(preset),
    )

    suspend fun setInterceptSystemBackGesture(enabled: Boolean) = edit { it[SettingsPreferenceKeys.INTERCEPT_SYSTEM_BACK] = enabled }
    suspend fun setLimitMaxInterceptLength(enabled: Boolean) = edit { it[SettingsPreferenceKeys.LIMIT_MAX_INTERCEPT_LENGTH] = enabled }

    suspend fun setDefaultTriggerMode(side: PanelSide, mode: GestureTriggerMode) = edit { prefs ->
        val resolved = if (mode == GestureTriggerMode.DEFAULT) GestureTriggerMode.ON_RELEASE else mode
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE] = resolved.id
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE] = resolved.id
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

    suspend fun setGestureHintEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.GESTURE_HINT_ENABLED] = enabled }

    suspend fun setGestureHintStyle(style: GestureHintStyle) = edit {
        it[SettingsPreferenceKeys.GESTURE_HINT_STYLE] = style.id
        style.toAnimationType()?.let { type ->
            val current = AnimationStyleCodec.decode(it[SettingsPreferenceKeys.ANIMATION_STYLES])
            it[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(current.selectType(type))
        }
    }

    suspend fun setAnimationStyles(styles: AnimationStyles) = edit {
        it[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(styles)
    }

    suspend fun updateWaveStyle(style: WaveStyle) = edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_WAVE, AnimationStyleCodec.encodeWave(style)),
        )
    }

    suspend fun updateCapsuleStyle(style: CapsuleStyle) = edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_CAPSULE, AnimationStyleCodec.encodeCapsule(style)),
        )
    }

    suspend fun updateBubbleStyle(style: BubbleStyle) = edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_BUBBLE, AnimationStyleCodec.encodeBubble(style)),
        )
    }

    suspend fun setGestureAngleConfig(config: GestureAngleConfig) = edit { prefs ->
        val normalized = config.normalized()
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_UP] = normalized.upDegrees
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_UP_RIGHT] = normalized.upRightDegrees
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_IN] = normalized.inDegrees
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_DOWN_RIGHT] = normalized.downRightDegrees
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_DOWN] = normalized.downDegrees
    }
    suspend fun setIndexHeightFraction(value: Float) = edit { it[SettingsPreferenceKeys.INDEX_HEIGHT] = value }
    suspend fun setAppsPerRow(value: Int) = edit { it[SettingsPreferenceKeys.APPS_PER_ROW] = value.coerceIn(2, 5) }

    suspend fun setQuickLauncherColumnsPerPage(value: Int) =
        edit { it[SettingsPreferenceKeys.QUICK_LAUNCHER_COLUMNS_PER_PAGE] = value.coerceIn(2, 5) }

    suspend fun setQuickLauncherRowsPerPage(value: Int) =
        edit { it[SettingsPreferenceKeys.QUICK_LAUNCHER_ROWS_PER_PAGE] = value.coerceIn(2, 6) }
    suspend fun setPanelOpacity(value: Float) = edit { it[SettingsPreferenceKeys.PANEL_OPACITY] = value }
    suspend fun setHapticEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.HAPTIC_ENABLED] = enabled }
    suspend fun setHideFromRecents(enabled: Boolean) = edit { it[SettingsPreferenceKeys.HIDE_FROM_RECENTS] = enabled }

    suspend fun setAccessibilityKeepAliveEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.ACCESSIBILITY_KEEP_ALIVE] = enabled }
    suspend fun setHapticStrengthLevel(level: Int) = edit {
        it[SettingsPreferenceKeys.HAPTIC_STRENGTH] = level.coerceIn(
            HapticStrength.LIGHT.level,
            HapticStrength.STRONG.level,
        )
    }
    suspend fun setFreeWindowEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.FREE_WINDOW_ENABLED] = enabled }
    suspend fun setFreeWindowModeId(id: Int) = edit {
        it[SettingsPreferenceKeys.FREE_WINDOW_MODE] = FreeWindowMode.fromId(id).id
    }
    suspend fun setFreeWindowLayout(
        widthFraction: Float,
        heightFraction: Float,
        leftFraction: Float,
        topFraction: Float,
    ) = edit {
        it[SettingsPreferenceKeys.FREE_WINDOW_WIDTH] = widthFraction.coerceIn(0.35f, 0.95f)
        it[SettingsPreferenceKeys.FREE_WINDOW_HEIGHT] = heightFraction.coerceIn(0.35f, 0.9f)
        it[SettingsPreferenceKeys.FREE_WINDOW_LEFT] = leftFraction.coerceIn(0f, 0.65f)
        it[SettingsPreferenceKeys.FREE_WINDOW_TOP] = topFraction.coerceIn(0f, 0.65f)
    }
    suspend fun setAppLaunchPolicyId(id: Int) = edit {
        it[SettingsPreferenceKeys.APP_LAUNCH_POLICY] = AppLaunchPolicy.fromId(id).id
    }
    suspend fun setLongPressLaunchDurationMs(value: Int) = edit {
        it[SettingsPreferenceKeys.LONG_PRESS_LAUNCH_DURATION] = value.coerceIn(250, 900)
    }
    suspend fun addHiddenApp(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES] = current
    }
    suspend fun removeHiddenApp(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES] = current
    }
    suspend fun addExcludedTriggerApp(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] = current
    }
    suspend fun removeExcludedTriggerApp(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] = current
    }
    suspend fun setThemeColor(argb: Int) = edit { it[SettingsPreferenceKeys.THEME_COLOR] = argb }

    suspend fun setHideTriggerInLandscape(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LANDSCAPE] = enabled }

    suspend fun setHideTriggerOnLockScreen(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LOCK_SCREEN] = enabled }

    suspend fun setHideTriggerOnLauncher(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LAUNCHER] = enabled }

    suspend fun setDynamicColorEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] = enabled }

    suspend fun setFloatingPointerJoystickAreaWidthPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_WIDTH] = value.coerceIn(120f, 800f)
    }

    suspend fun setFloatingPointerJoystickAreaHeightPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_HEIGHT] = value.coerceIn(120f, 1400f)
    }

    suspend fun setFloatingPointerJoystickAreaZoomFraction(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_ZOOM] = value.coerceIn(0.1f, 1f)
    }

    suspend fun setFloatingPointerMatchJoystickToScreenAspect(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_MATCH_ASPECT] = enabled
    }

    suspend fun setFloatingPointerJoystickDiameterPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_SIZE] = value.coerceIn(180f, 360f)
    }

    suspend fun setFloatingPointerPointerDiameterPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_POINTER_SIZE] = value.coerceIn(48f, 120f)
    }

    suspend fun setFloatingPointerDesignId(designId: String) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_DESIGN_ID] = designId.ifBlank { FloatingPointerDesignIds.RING }
    }

    suspend fun setFloatingPointerRingThicknessPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RING_THICKNESS] = value.coerceIn(4f, 24f)
    }

    suspend fun setFloatingPointerDotDiameterPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_DOT_DIAMETER] = value.coerceIn(2f, 24f)
    }

    suspend fun setFloatingPointerRingColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RING_COLOR] = argb
    }

    suspend fun setFloatingPointerFillColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_FILL_COLOR] = argb
    }

    suspend fun setFloatingPointerDotColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_DOT_COLOR] = argb
    }

    suspend fun setFloatingPointerClickVisualFeedbackEnabled(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_VISUAL_FEEDBACK] = enabled
    }

    suspend fun setFloatingPointerClickHapticEnabled(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_HAPTIC] = enabled
    }

    suspend fun setFloatingPointerRippleColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_COLOR] = argb
    }

    suspend fun setFloatingPointerRippleSizeDp(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_SIZE_DP] = value.coerceIn(40f, 200f)
    }

    suspend fun setFloatingPointerRippleDurationMs(value: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RIPPLE_DURATION_MS] = value.coerceIn(100, 1500)
    }

    suspend fun setFloatingPointerTrailType(type: FloatingPointerTrailType) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_TYPE] = type.id
    }

    suspend fun setFloatingPointerTrailDurationMs(value: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_DURATION] = value.coerceIn(50, 500)
    }

    suspend fun setFloatingPointerTrailColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_TRAIL_COLOR] = argb
    }

    suspend fun setFloatingPointerHideWhenJoystickReleased(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_ON_RELEASE] = enabled
    }

    suspend fun setFloatingPointerJoystickInnerColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_INNER_COLOR] = argb
    }

    suspend fun setFloatingPointerJoystickOuterColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_OUTER_COLOR] = argb
    }

    suspend fun setFloatingPointerJoystickGradientRadiusFraction(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_GRADIENT] = value.coerceIn(0.5f, 1f)
    }

    suspend fun setFloatingPointerHideOnOutsideClick(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_OUTSIDE_CLICK] = enabled
    }

    suspend fun setFloatingPointerHideOnQuickSwipe(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_QUICK_SWIPE] = enabled
    }

    suspend fun setFloatingPointerHideWhenIdle(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_IDLE] = enabled
    }

    suspend fun setFloatingPointerIdleHideDelayMs(value: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_IDLE_DELAY] = value.coerceIn(1000, 10000)
    }

    suspend fun setFloatingPointerRadialMenuEnabled(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ENABLED] = enabled
    }

    suspend fun setFloatingPointerRadialAlwaysVisible(enabled: Boolean) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ALWAYS_VISIBLE] = enabled
    }

    suspend fun setFloatingPointerRadialLongPressMs(value: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_LONG_PRESS_MS] = value.coerceIn(200, 2000)
    }

    suspend fun setFloatingPointerRadialOuterDiameterPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_SIZE] = value.coerceIn(240f, 720f)
    }

    suspend fun setFloatingPointerRadialInnerDiameterPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_SIZE] = value.coerceIn(80f, 480f)
    }

    suspend fun setFloatingPointerRadialOuterColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialInnerColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialDividerThicknessPx(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_SIZE] = value.coerceIn(1f, 12f)
    }

    suspend fun setFloatingPointerRadialDividerColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialIconSizeFraction(value: Float) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_SIZE] = value.coerceIn(0.2f, 0.9f)
    }

    suspend fun setFloatingPointerRadialIconColor(argb: Int) = edit {
        it[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialSlotAction(index: Int, action: GestureAction) = edit { prefs ->
        val current = FloatingPointerRadialMenuCodec.decode(prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_SLOTS] ?: emptySet())
        val updated = current.toMutableList()
        if (index in 0 until FloatingPointerRadialMenuCodec.SLOT_COUNT) {
            updated[index] = action
            prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_SLOTS] = FloatingPointerRadialMenuCodec.encode(updated)
        }
    }

    suspend fun resetFloatingPointerRadialDesignDefaults() = edit { prefs ->
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_SIZE] = 440f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_SIZE] = 192f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_OUTER_COLOR] = 0xE62B3D4F.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_INNER_COLOR] = 0xE61A1A28.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_SIZE] = 4f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_DIVIDER_COLOR] = 0x22FFFFFF
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_SIZE] = 0.85f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_RADIAL_ICON_COLOR] = 0xFFFFFFFF.toInt()
    }

    suspend fun setOtpCopyToClipboard(enabled: Boolean) = edit { it[SettingsPreferenceKeys.OTP_COPY_TO_CLIPBOARD] = enabled }

    suspend fun setOtpKeywordsRegex(value: String) = edit {
        it[SettingsPreferenceKeys.OTP_KEYWORDS_REGEX] = value.ifBlank {
            OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
    }

    suspend fun setOtpUserMatchRules(rules: List<com.slideindex.app.otp.OtpMatchRule>) = edit {
        it[SettingsPreferenceKeys.OTP_USER_MATCH_RULES] = OtpMatchRuleCodec.encodeAll(rules)
    }

    suspend fun setOtpDisabledOfficialRuleIds(ids: Set<String>) = edit {
        it[SettingsPreferenceKeys.OTP_DISABLED_OFFICIAL_RULE_IDS] = ids
    }

    suspend fun setOtpOfficialRuleEnabled(ruleId: String, enabled: Boolean) = edit { prefs ->
        val current = prefs[SettingsPreferenceKeys.OTP_DISABLED_OFFICIAL_RULE_IDS]?.toMutableSet() ?: mutableSetOf()
        if (enabled) {
            current.remove(ruleId)
        } else {
            current.add(ruleId)
        }
        prefs[SettingsPreferenceKeys.OTP_DISABLED_OFFICIAL_RULE_IDS] = current
    }

    suspend fun setOtpAutoInputEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.OTP_AUTO_INPUT_ENABLED] = enabled }

    suspend fun setOtpAutoConfirmEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.OTP_AUTO_CONFIRM_ENABLED] = enabled }

    suspend fun setOtpAutoInputDelayMs(value: Int) = edit {
        it[SettingsPreferenceKeys.OTP_AUTO_INPUT_DELAY_MS] = value.coerceIn(0, 5000)
    }

    suspend fun setOtpAutoInputIntervalMs(value: Int) = edit {
        it[SettingsPreferenceKeys.OTP_AUTO_INPUT_INTERVAL_MS] = value.coerceIn(0, 500)
    }

    suspend fun setOtpLsposedSmsCaptureEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.OTP_LSPOSED_SMS_CAPTURE_ENABLED] = enabled }

    suspend fun setOtpLsposedSystemInjectEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.OTP_LSPOSED_SYSTEM_INJECT_ENABLED] = enabled }

    suspend fun setShakeGesturesEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.SHAKE_GESTURES_ENABLED] = enabled }

    suspend fun setDebugPerformanceMonitorEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.DEBUG_PERFORMANCE_MONITOR] = enabled }

    suspend fun setShakeGestureAction(type: ShakeGestureType, action: GestureAction) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        val updated = current.basicActions.toMutableMap().apply { put(type, action) }
        prefs[SettingsPreferenceKeys.SHAKE_GESTURE_ACTIONS] = ShakeGestureCodec.encodeAllActions(updated)
    }

    suspend fun setLockScreenShakeAction(type: ShakeGestureType, action: GestureAction) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        val updated = current.lockScreenActions.toMutableMap().apply { put(type, action) }
        prefs[SettingsPreferenceKeys.SHAKE_LOCK_SCREEN_ACTIONS] = ShakeGestureCodec.encodeAllActions(updated)
    }

    suspend fun setPerAppShakeAction(packageName: String, type: ShakeGestureType, action: GestureAction) =
        edit { prefs ->
            val current = readShakeGestureSettings(prefs)
            val perApp = current.perAppActions.toMutableMap()
            val appActions = perApp[packageName].orEmpty().toMutableMap().apply { put(type, action) }
            perApp[packageName] = appActions
            prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
        }

    suspend fun addPerAppShakeConfig(packageName: String) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        if (packageName in current.perAppActions) return@edit
        val perApp = current.perAppActions.toMutableMap()
        perApp[packageName] = emptyMap()
        prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
    }

    suspend fun removePerAppShakeConfig(packageName: String) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        val perApp = current.perAppActions.toMutableMap()
        perApp.remove(packageName)
        prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
    }

    suspend fun setShakeDirectionSensitivity(type: ShakeGestureType, value: Float) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        val updated = current.perDirectionSensitivity.toMutableMap().apply {
            put(type, value.coerceIn(1f, 10f))
        }
        prefs[SettingsPreferenceKeys.SHAKE_PER_DIRECTION_SENSITIVITY] = ShakeGestureCodec.encodePerDirectionSensitivity(updated)
    }

    suspend fun setLockScreenShakeEnabled(enabled: Boolean) = edit { it[SettingsPreferenceKeys.LOCK_SCREEN_SHAKE_ENABLED] = enabled }

    suspend fun setIndependentAppShakeEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.INDEPENDENT_APP_SHAKE_ENABLED] = enabled }

    suspend fun setShakeGlobalSensitivity(value: Float) = edit {
        it[SettingsPreferenceKeys.SHAKE_GLOBAL_SENSITIVITY] = value.coerceIn(1f, 10f)
    }

    suspend fun setShakeIndependentSensitivityEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.SHAKE_INDEPENDENT_SENSITIVITY_ENABLED] = enabled }

    suspend fun setShakeVibrationFeedbackEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.SHAKE_VIBRATION_FEEDBACK_ENABLED] = enabled }

    suspend fun setShakeAnimationFeedbackEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.SHAKE_ANIMATION_FEEDBACK_ENABLED] = enabled }

    suspend fun setShakeAnimationColor(argb: Int) = edit { it[SettingsPreferenceKeys.SHAKE_ANIMATION_COLOR] = argb }

    suspend fun setShakeDisableInLandscape(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.SHAKE_DISABLE_IN_LANDSCAPE] = enabled }

    suspend fun addShakeBlacklistedApp(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES] = current
    }

    suspend fun removeShakeBlacklistedApp(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES] = current
    }

    suspend fun setMessageReminderEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.MESSAGE_REMINDER_ENABLED] = enabled }

    suspend fun setMessageStyleId(styleId: String) =
        edit { it[SettingsPreferenceKeys.MESSAGE_STYLE_ID] = styleId }

    suspend fun setMessagePrimaryStyleEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.MESSAGE_PRIMARY_STYLE_ENABLED] = enabled }

    suspend fun setMessageDanmakuEnabled(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_ENABLED] = enabled }

    suspend fun setMessageThemeId(themeId: String) =
        edit { it[SettingsPreferenceKeys.MESSAGE_THEME_ID] = themeId }

    suspend fun setMessageDanmakuThemeId(themeId: String) =
        edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_THEME_ID] = themeId }

    suspend fun setMessageFloatIconOpacity(opacity: Float) =
        edit { it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_OPACITY] = opacity.coerceIn(0f, 1f) }

    suspend fun setMessageCardOpacity(opacity: Float) =
        edit { it[SettingsPreferenceKeys.MESSAGE_CARD_OPACITY] = opacity.coerceIn(0.2f, 1f) }

    suspend fun setMessageSideBubbleOpacity(opacity: Float) =
        edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_OPACITY] = opacity.coerceIn(0.2f, 1f) }

    suspend fun setMessageFloatIconSizeDp(sizeDp: Float) =
        edit { it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_SIZE_DP] = sizeDp.coerceIn(32f, 64f) }

    suspend fun setMessageDanmakuOpacity(opacity: Float) =
        edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_OPACITY] = opacity.coerceIn(0.2f, 1f) }

    suspend fun setMessageCardMaxLines(lines: Int) =
        edit { it[SettingsPreferenceKeys.MESSAGE_CARD_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageDanmakuMaxLines(lines: Int) =
        edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageSideMaxCount(count: Int) =
        edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_COUNT] = count.coerceIn(1, 5) }

    suspend fun setMessageSideMaxWidthDp(widthDp: Float) =
        edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_WIDTH_DP] = widthDp.coerceIn(120f, 320f) }

    suspend fun setMessageSideMaxLines(lines: Int) =
        edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageAutoDismissSeconds(seconds: Int) =
        edit { it[SettingsPreferenceKeys.MESSAGE_AUTO_DISMISS_SECONDS] = seconds.coerceIn(0, 60) }

    suspend fun setMessageHideInLandscape(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.MESSAGE_HIDE_IN_LANDSCAPE] = enabled }

    suspend fun setMessagePortraitDanmaku(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.MESSAGE_PORTRAIT_DANMAKU] = enabled }

    suspend fun setMessageLandscapeDanmaku(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.MESSAGE_LANDSCAPE_DANMAKU] = enabled }

    suspend fun setMessageGestureAction(slot: String, action: MessageAction) = edit { prefs ->
        val current = MessageSettingsCodec.decodeGestureActions(
            prefs[SettingsPreferenceKeys.MESSAGE_GESTURE_ACTIONS] ?: emptySet(),
        ).toMutableMap()
        current[slot] = action
        val encoded = current.map { (key, value) ->
            MessageSettingsCodec.encodeGestureAction(key, value)
        }.toSet()
        prefs[SettingsPreferenceKeys.MESSAGE_GESTURE_ACTIONS] = encoded
    }

    suspend fun addMessageEnabledPackage(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES] = current
    }

    suspend fun removeMessageEnabledPackage(packageName: String) = edit { prefs ->
        val current = prefs[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES] = current
        val rules = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        rules.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(rules.values)
    }

    suspend fun addMessageDisabledPackage(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES] = current
    }

    suspend fun removeMessageDisabledPackage(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES] = current
    }

    suspend fun addMessageDndPackage(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES] = current
    }

    suspend fun removeMessageDndPackage(packageName: String) = edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES] = current
    }

    suspend fun setMessageSuppressWhenSystemDnd(enabled: Boolean) =
        edit { it[SettingsPreferenceKeys.MESSAGE_SUPPRESS_WHEN_SYSTEM_DND] = enabled }

    suspend fun upsertMessageAppFilterRule(rule: MessageAppFilterRule) = edit { prefs ->
        val current = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        if (rule.hasCustomFilter()) {
            current[rule.packageName] = rule
        } else {
            current.remove(rule.packageName)
        }
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(current.values)
    }

    suspend fun removeMessageAppFilterRule(packageName: String) = edit { prefs ->
        val current = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        current.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(current.values)
    }

    suspend fun resetFloatingPointerVisualDefaults() = edit { prefs ->
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

    suspend fun resetFloatingPointerJoystickVisualDefaults() = edit { prefs ->
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_SIZE] = 275f
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_INNER_COLOR] = 0x80FFFFFF.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_OUTER_COLOR] = 0x80C0C0C0.toInt()
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_GRADIENT] = 1f
    }

    suspend fun resetFloatingPointerJoystickBehaviorDefaults() = edit { prefs ->
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_OUTSIDE_CLICK] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_QUICK_SWIPE] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_IDLE] = true
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_IDLE_DELAY] = 3000
    }

    suspend fun upsertGestureRule(rule: GestureRule) = edit { prefs ->
        val current = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet())
            .filterNot { it.id == rule.id }
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(current + rule)
    }

    suspend fun removeGestureRule(id: String) = edit { prefs ->
        val current = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet())
            .filterNot { it.id == id }
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(current)
    }

    suspend fun setSlotAction(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
    ) = edit { prefs ->
        val current = AppSettings(
            gestureRules = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet()),
        )
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(
            current.withSlotAction(side, trigger, action).gestureRules,
        )
    }

    suspend fun setSlotTriggerMode(
        side: PanelSide,
        trigger: GestureTriggerType,
        triggerMode: GestureTriggerMode,
    ) = edit { prefs ->
        val current = AppSettings(
            gestureRules = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet()),
        )
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(
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
            gestureRules = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet()),
        )
        val updated = if (action.type == GestureActionType.NONE) {
            current.withSlotAction(side, trigger, action, handleId)
        } else {
            current
                .withSlotAction(side, trigger, action, handleId)
                .withSlotTriggerMode(side, trigger, triggerMode, handleId)
        }
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(updated.gestureRules)
    }

    private fun readTriggerSettings(prefs: Preferences): AppSettings {
        val legacyTop = prefs[SettingsPreferenceKeys.TRIGGER_TOP] ?: 0.30f
        val legacyHeight = prefs[SettingsPreferenceKeys.TRIGGER_HEIGHT] ?: 0.38f
        val legacyShortSwipe = prefs[SettingsPreferenceKeys.SHORT_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP
        val legacyLongSwipe = prefs[SettingsPreferenceKeys.LONG_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP
        return AppSettings(
            leftTriggerHandles = prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES]?.let {
                TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
            } ?: listOf(
                TriggerHandle.default(
                    prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] ?: legacyTop,
                    prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] ?: legacyHeight,
                ),
            ),
            rightTriggerHandles = prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES]?.let {
                TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
            } ?: listOf(
                TriggerHandle.default(
                    prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] ?: legacyTop,
                    prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] ?: legacyHeight,
                ),
            ),
            gestureRules = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet()),
        )
    }

    private fun writeTriggerHandles(prefs: MutablePreferences, settings: AppSettings) {
        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES] = TriggerHandleCodec.encodeAll(settings.leftTriggerHandles)
        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES] = TriggerHandleCodec.encodeAll(settings.rightTriggerHandles)
    }

    suspend fun setQuickLauncherItems(
        items: List<com.slideindex.app.launcher.QuickLauncherItem>,
    ) = edit { prefs ->
        prefs[SettingsPreferenceKeys.QUICK_LAUNCHER] = QuickLauncherItemCodec.encodeAll(items)
    }

    private fun readQuickLauncherItems(prefs: Preferences): List<com.slideindex.app.launcher.QuickLauncherItem> {
        val unified = QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER] ?: emptySet())
        if (unified.isNotEmpty()) return unified
        val left = QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_LEFT] ?: emptySet())
        if (left.isNotEmpty()) return left
        return QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_RIGHT] ?: emptySet())
    }

    suspend fun setShellCommands(items: List<ShellCommand>) = edit { prefs ->
        prefs[SettingsPreferenceKeys.SHELL_COMMANDS] = ShellCommandCodec.encodeAll(items)
    }

    suspend fun setWidgetPanelPages(
        pages: List<com.slideindex.app.widget.WidgetPanelPage>,
    ) = edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_PAGES] = WidgetPanelCodec.encodeAll(pages)
    }

    suspend fun setWidgetPanelBlurEnabled(enabled: Boolean) = edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_BLUR] = enabled
    }

    suspend fun setWidgetPanelWidthFraction(fraction: Float) = edit { prefs ->
        prefs[SettingsPreferenceKeys.WIDGET_PANEL_WIDTH] = fraction.coerceIn(0.5f, 0.95f)
    }

    private fun legacyLaunchPolicy(prefs: Preferences): Int {
        return if (prefs[SettingsPreferenceKeys.FREE_WINDOW_ENABLED] == true) {
            AppLaunchPolicy.ALWAYS_FREE_WINDOW.id
        } else {
            AppLaunchPolicy.ALWAYS_FULLSCREEN.id
        }
    }

    private fun readGestureAngleConfig(prefs: Preferences): GestureAngleConfig =
        GestureAngleConfig(
            upDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_UP] ?: GestureAngleConfig.DEFAULT_UP,
            upRightDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_UP_RIGHT] ?: GestureAngleConfig.DEFAULT_UP_RIGHT,
            inDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_IN] ?: GestureAngleConfig.DEFAULT_IN,
            downRightDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_DOWN_RIGHT] ?: GestureAngleConfig.DEFAULT_DOWN_RIGHT,
            downDegrees = prefs[SettingsPreferenceKeys.GESTURE_ANGLE_DOWN] ?: GestureAngleConfig.DEFAULT_DOWN,
        ).normalized()

    private fun readMessageReminderSettings(prefs: Preferences): MessageSettings {
        val base = MessageSettings()
        val withGestures = MessageSettingsCodec.applyGestureActions(
            base,
            prefs[SettingsPreferenceKeys.MESSAGE_GESTURE_ACTIONS] ?: emptySet(),
        )
        return withGestures.copy(
            enabled = prefs[SettingsPreferenceKeys.MESSAGE_REMINDER_ENABLED] ?: false,
            styleId = prefs[SettingsPreferenceKeys.MESSAGE_STYLE_ID] ?: base.styleId,
            primaryStyleEnabled = prefs[SettingsPreferenceKeys.MESSAGE_PRIMARY_STYLE_ENABLED] ?: true,
            danmakuEnabled = prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_ENABLED] ?: true,
            themeId = MessageThemeIds.normalizeThemeId(
                prefs[SettingsPreferenceKeys.MESSAGE_THEME_ID] ?: base.themeId,
            ),
            danmakuThemeId = MessageThemeIds.normalizeThemeId(
                prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_THEME_ID] ?: base.danmakuThemeId,
            ),
            floatIconOpacity = prefs[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_OPACITY]
                ?: prefs[SettingsPreferenceKeys.MESSAGE_OPACITY]
                ?: base.floatIconOpacity,
            cardOpacity = prefs[SettingsPreferenceKeys.MESSAGE_CARD_OPACITY]
                ?: prefs[SettingsPreferenceKeys.MESSAGE_OPACITY]
                ?: base.cardOpacity,
            sideBubbleOpacity = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_OPACITY]
                ?: prefs[SettingsPreferenceKeys.MESSAGE_OPACITY]
                ?: base.sideBubbleOpacity,
            danmakuOpacity = prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_OPACITY] ?: base.danmakuOpacity,
            cardMaxLines = prefs[SettingsPreferenceKeys.MESSAGE_CARD_MAX_LINES] ?: base.cardMaxLines,
            danmakuMaxLines = prefs[SettingsPreferenceKeys.MESSAGE_DANMAKU_MAX_LINES] ?: base.danmakuMaxLines,
            sideMaxCount = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_COUNT] ?: base.sideMaxCount,
            sideMaxWidthDp = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_WIDTH_DP] ?: base.sideMaxWidthDp,
            sideMaxLines = prefs[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_LINES] ?: base.sideMaxLines,
            floatIconSizeDp = prefs[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_SIZE_DP] ?: base.floatIconSizeDp,
            autoDismissSeconds = prefs[SettingsPreferenceKeys.MESSAGE_AUTO_DISMISS_SECONDS] ?: base.autoDismissSeconds,
            hideInLandscape = prefs[SettingsPreferenceKeys.MESSAGE_HIDE_IN_LANDSCAPE] ?: false,
            portraitDanmaku = prefs[SettingsPreferenceKeys.MESSAGE_PORTRAIT_DANMAKU] ?: true,
            landscapeDanmaku = prefs[SettingsPreferenceKeys.MESSAGE_LANDSCAPE_DANMAKU] ?: true,
            enabledPackages = prefs[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES] ?: emptySet(),
            disabledPackages = prefs[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES] ?: emptySet(),
            dndPackages = prefs[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES] ?: emptySet(),
            suppressWhenSystemDnd = prefs[SettingsPreferenceKeys.MESSAGE_SUPPRESS_WHEN_SYSTEM_DND] ?: false,
            appFilterRules = MessageAppFilterCodec.decodeAll(
                prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet(),
            ),
        )
    }

    private fun readShakeGestureSettings(prefs: Preferences): ShakeGestureSettings =
        ShakeGestureSettings(
            enabled = prefs[SettingsPreferenceKeys.SHAKE_GESTURES_ENABLED] ?: true,
            basicActions = ShakeGestureCodec.decodeAllActions(prefs[SettingsPreferenceKeys.SHAKE_GESTURE_ACTIONS] ?: emptySet()),
            lockScreenShakeEnabled = prefs[SettingsPreferenceKeys.LOCK_SCREEN_SHAKE_ENABLED] ?: false,
            lockScreenActions = ShakeGestureCodec.decodeAllActions(prefs[SettingsPreferenceKeys.SHAKE_LOCK_SCREEN_ACTIONS] ?: emptySet()),
            independentAppShakeEnabled = prefs[SettingsPreferenceKeys.INDEPENDENT_APP_SHAKE_ENABLED] ?: false,
            perAppActions = ShakeGestureCodec.decodePerAppActions(prefs[SettingsPreferenceKeys.SHAKE_PER_APP_ACTIONS] ?: emptySet()),
            globalSensitivity = prefs[SettingsPreferenceKeys.SHAKE_GLOBAL_SENSITIVITY] ?: 6.0f,
            independentSensitivityEnabled = prefs[SettingsPreferenceKeys.SHAKE_INDEPENDENT_SENSITIVITY_ENABLED] ?: false,
            perDirectionSensitivity = ShakeGestureCodec.decodePerDirectionSensitivity(
                prefs[SettingsPreferenceKeys.SHAKE_PER_DIRECTION_SENSITIVITY] ?: emptySet(),
            ),
            vibrationFeedbackEnabled = prefs[SettingsPreferenceKeys.SHAKE_VIBRATION_FEEDBACK_ENABLED] ?: true,
            animationFeedbackEnabled = prefs[SettingsPreferenceKeys.SHAKE_ANIMATION_FEEDBACK_ENABLED] ?: false,
            animationColorArgb = prefs[SettingsPreferenceKeys.SHAKE_ANIMATION_COLOR] ?: 0xFF424242.toInt(),
            disableInLandscape = prefs[SettingsPreferenceKeys.SHAKE_DISABLE_IN_LANDSCAPE] ?: false,
            blacklistedPackages = prefs[SettingsPreferenceKeys.SHAKE_BLACKLIST_PACKAGES] ?: emptySet(),
        )

    private fun resolveOtpKeywordsRegex(stored: String?): String {
        if (stored == null) {
            return OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
        if (stored == OtpKeywords.LEGACY_DEFAULT_KEYWORDS_REGEX) {
            return OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
        return stored
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            block(prefs)
        }
    }
}
