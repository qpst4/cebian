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
            widgetPanelPages = WidgetPanelCodec.decodeAll(prefs[WIDGET_PANEL_PAGES] ?: emptySet()),
            widgetPanelWidthFraction = prefs[WIDGET_PANEL_WIDTH] ?: 0.8f,
            widgetPanelHeightFraction = prefs[WIDGET_PANEL_HEIGHT] ?: 0.55f,
            widgetPanelTopFraction = prefs[WIDGET_PANEL_TOP] ?: 0.15f,
            widgetPanelBlurEnabled = prefs[WIDGET_PANEL_BLUR] ?: true,
            floatingPointerJoystickAreaWidthPx = prefs[FLOATING_POINTER_JOYSTICK_AREA_WIDTH] ?: 703f,
            floatingPointerJoystickAreaHeightPx = prefs[FLOATING_POINTER_JOYSTICK_AREA_HEIGHT] ?: 711f,
            floatingPointerJoystickAreaZoomFraction = prefs[FLOATING_POINTER_JOYSTICK_AREA_ZOOM] ?: 0.8f,
            floatingPointerMatchJoystickToScreenAspect = prefs[FLOATING_POINTER_JOYSTICK_MATCH_ASPECT] ?: false,
            floatingPointerJoystickDiameterPx = prefs[FLOATING_POINTER_JOYSTICK_SIZE] ?: 275f,
            floatingPointerPointerDiameterPx = prefs[FLOATING_POINTER_POINTER_SIZE] ?: 100f,
            floatingPointerDesignId = prefs[FLOATING_POINTER_DESIGN_ID] ?: FloatingPointerDesignIds.RING,
            floatingPointerRingThicknessPx = prefs[FLOATING_POINTER_RING_THICKNESS] ?: 12f,
            floatingPointerDotDiameterPx = prefs[FLOATING_POINTER_DOT_DIAMETER] ?: 15f,
            floatingPointerRingColorArgb = prefs[FLOATING_POINTER_RING_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerFillColorArgb = prefs[FLOATING_POINTER_FILL_COLOR] ?: 0x19000000,
            floatingPointerDotColorArgb = prefs[FLOATING_POINTER_DOT_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerClickVisualFeedbackEnabled = prefs[FLOATING_POINTER_CLICK_VISUAL_FEEDBACK] ?: true,
            floatingPointerClickHapticEnabled = prefs[FLOATING_POINTER_CLICK_HAPTIC] ?: true,
            floatingPointerRippleColorArgb = prefs[FLOATING_POINTER_RIPPLE_COLOR] ?: 0xFFFD746C.toInt(),
            floatingPointerRippleSizeDp = prefs[FLOATING_POINTER_RIPPLE_SIZE_DP] ?: 80f,
            floatingPointerRippleDurationMs = prefs[FLOATING_POINTER_RIPPLE_DURATION_MS] ?: 500,
            floatingPointerTrailTypeId = prefs[FLOATING_POINTER_TRAIL_TYPE] ?: FloatingPointerTrailType.HIGH_DETAIL.id,
            floatingPointerTrailDurationMs = prefs[FLOATING_POINTER_TRAIL_DURATION] ?: 150,
            floatingPointerTrailColorArgb = prefs[FLOATING_POINTER_TRAIL_COLOR] ?: 0x66FF5252,
            floatingPointerHideWhenJoystickReleased = prefs[FLOATING_POINTER_HIDE_ON_RELEASE] ?: false,
            floatingPointerJoystickInnerColorArgb = prefs[FLOATING_POINTER_JOYSTICK_INNER_COLOR] ?: 0x80FFFFFF.toInt(),
            floatingPointerJoystickOuterColorArgb = prefs[FLOATING_POINTER_JOYSTICK_OUTER_COLOR] ?: 0x80C0C0C0.toInt(),
            floatingPointerJoystickGradientRadiusFraction = prefs[FLOATING_POINTER_JOYSTICK_GRADIENT] ?: 1f,
            floatingPointerHideOnOutsideClick = prefs[FLOATING_POINTER_HIDE_OUTSIDE_CLICK] ?: true,
            floatingPointerHideOnQuickSwipe = prefs[FLOATING_POINTER_HIDE_QUICK_SWIPE] ?: true,
            floatingPointerHideWhenIdle = prefs[FLOATING_POINTER_HIDE_IDLE] ?: true,
            floatingPointerIdleHideDelayMs = prefs[FLOATING_POINTER_IDLE_DELAY] ?: 3000,
            floatingPointerRadialMenuEnabled = prefs[FLOATING_POINTER_RADIAL_ENABLED] ?: true,
            floatingPointerRadialAlwaysVisible = prefs[FLOATING_POINTER_RADIAL_ALWAYS_VISIBLE] ?: false,
            floatingPointerRadialLongPressMs = prefs[FLOATING_POINTER_RADIAL_LONG_PRESS_MS] ?: 500,
            floatingPointerRadialOuterDiameterPx = prefs[FLOATING_POINTER_RADIAL_OUTER_SIZE] ?: 440f,
            floatingPointerRadialInnerDiameterPx = prefs[FLOATING_POINTER_RADIAL_INNER_SIZE] ?: 192f,
            floatingPointerRadialOuterColorArgb = prefs[FLOATING_POINTER_RADIAL_OUTER_COLOR] ?: 0xE62B3D4F.toInt(),
            floatingPointerRadialInnerColorArgb = prefs[FLOATING_POINTER_RADIAL_INNER_COLOR] ?: 0xE61A1A28.toInt(),
            floatingPointerRadialDividerThicknessPx = prefs[FLOATING_POINTER_RADIAL_DIVIDER_SIZE] ?: 4f,
            floatingPointerRadialDividerColorArgb = prefs[FLOATING_POINTER_RADIAL_DIVIDER_COLOR] ?: 0x22FFFFFF,
            floatingPointerRadialIconSizeFraction = prefs[FLOATING_POINTER_RADIAL_ICON_SIZE] ?: 0.85f,
            floatingPointerRadialIconColorArgb = prefs[FLOATING_POINTER_RADIAL_ICON_COLOR] ?: 0xFFFFFFFF.toInt(),
            floatingPointerRadialSlotActions = FloatingPointerRadialMenuCodec.decode(
                prefs[FLOATING_POINTER_RADIAL_SLOTS] ?: emptySet(),
            ),
            otpCopyToClipboard = prefs[OTP_COPY_TO_CLIPBOARD] ?: false,
            otpKeywordsRegex = resolveOtpKeywordsRegex(prefs[OTP_KEYWORDS_REGEX]),
            otpUserMatchRules = OtpMatchRuleCodec.decodeAll(prefs[OTP_USER_MATCH_RULES] ?: emptySet()),
            otpDisabledOfficialRuleIds = prefs[OTP_DISABLED_OFFICIAL_RULE_IDS] ?: emptySet(),
            otpAutoInputEnabled = prefs[OTP_AUTO_INPUT_ENABLED] ?: false,
            otpAutoConfirmEnabled = prefs[OTP_AUTO_CONFIRM_ENABLED] ?: false,
            otpAutoInputDelayMs = prefs[OTP_AUTO_INPUT_DELAY_MS] ?: 0,
            otpAutoInputIntervalMs = prefs[OTP_AUTO_INPUT_INTERVAL_MS] ?: 0,
            otpLsposedSmsCaptureEnabled = prefs[OTP_LSPOSED_SMS_CAPTURE_ENABLED] ?: false,
            otpLsposedSystemInjectEnabled = prefs[OTP_LSPOSED_SYSTEM_INJECT_ENABLED] ?: true,
            shakeGestureSettings = readShakeGestureSettings(prefs),
            messageReminderSettings = readMessageReminderSettings(prefs),
            debugPerformanceMonitorEnabled = prefs[DEBUG_PERFORMANCE_MONITOR] ?: false,
        )
    }

    init {
        cacheScope.launch {
            settings.collect { cachedSettings = it }
        }
    }

    fun readSnapshot(): AppSettings = cachedSettings

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
            prefs[LEFT_TRIGGER_TOP] = it.topFraction
            prefs[LEFT_TRIGGER_HEIGHT] = it.heightFraction
        }
        primaryRight?.let {
            prefs[RIGHT_TRIGGER_TOP] = it.topFraction
            prefs[RIGHT_TRIGGER_HEIGHT] = it.heightFraction
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

    suspend fun setFloatingPointerJoystickAreaWidthPx(value: Float) = edit {
        it[FLOATING_POINTER_JOYSTICK_AREA_WIDTH] = value.coerceIn(120f, 800f)
    }

    suspend fun setFloatingPointerJoystickAreaHeightPx(value: Float) = edit {
        it[FLOATING_POINTER_JOYSTICK_AREA_HEIGHT] = value.coerceIn(120f, 1400f)
    }

    suspend fun setFloatingPointerJoystickAreaZoomFraction(value: Float) = edit {
        it[FLOATING_POINTER_JOYSTICK_AREA_ZOOM] = value.coerceIn(0.1f, 1f)
    }

    suspend fun setFloatingPointerMatchJoystickToScreenAspect(enabled: Boolean) = edit {
        it[FLOATING_POINTER_JOYSTICK_MATCH_ASPECT] = enabled
    }

    suspend fun setFloatingPointerJoystickDiameterPx(value: Float) = edit {
        it[FLOATING_POINTER_JOYSTICK_SIZE] = value.coerceIn(180f, 360f)
    }

    suspend fun setFloatingPointerPointerDiameterPx(value: Float) = edit {
        it[FLOATING_POINTER_POINTER_SIZE] = value.coerceIn(48f, 120f)
    }

    suspend fun setFloatingPointerDesignId(designId: String) = edit {
        it[FLOATING_POINTER_DESIGN_ID] = designId.ifBlank { FloatingPointerDesignIds.RING }
    }

    suspend fun setFloatingPointerRingThicknessPx(value: Float) = edit {
        it[FLOATING_POINTER_RING_THICKNESS] = value.coerceIn(4f, 24f)
    }

    suspend fun setFloatingPointerDotDiameterPx(value: Float) = edit {
        it[FLOATING_POINTER_DOT_DIAMETER] = value.coerceIn(2f, 24f)
    }

    suspend fun setFloatingPointerRingColor(argb: Int) = edit {
        it[FLOATING_POINTER_RING_COLOR] = argb
    }

    suspend fun setFloatingPointerFillColor(argb: Int) = edit {
        it[FLOATING_POINTER_FILL_COLOR] = argb
    }

    suspend fun setFloatingPointerDotColor(argb: Int) = edit {
        it[FLOATING_POINTER_DOT_COLOR] = argb
    }

    suspend fun setFloatingPointerClickVisualFeedbackEnabled(enabled: Boolean) = edit {
        it[FLOATING_POINTER_CLICK_VISUAL_FEEDBACK] = enabled
    }

    suspend fun setFloatingPointerClickHapticEnabled(enabled: Boolean) = edit {
        it[FLOATING_POINTER_CLICK_HAPTIC] = enabled
    }

    suspend fun setFloatingPointerRippleColor(argb: Int) = edit {
        it[FLOATING_POINTER_RIPPLE_COLOR] = argb
    }

    suspend fun setFloatingPointerRippleSizeDp(value: Float) = edit {
        it[FLOATING_POINTER_RIPPLE_SIZE_DP] = value.coerceIn(40f, 200f)
    }

    suspend fun setFloatingPointerRippleDurationMs(value: Int) = edit {
        it[FLOATING_POINTER_RIPPLE_DURATION_MS] = value.coerceIn(100, 1500)
    }

    suspend fun setFloatingPointerTrailType(type: FloatingPointerTrailType) = edit {
        it[FLOATING_POINTER_TRAIL_TYPE] = type.id
    }

    suspend fun setFloatingPointerTrailDurationMs(value: Int) = edit {
        it[FLOATING_POINTER_TRAIL_DURATION] = value.coerceIn(50, 500)
    }

    suspend fun setFloatingPointerTrailColor(argb: Int) = edit {
        it[FLOATING_POINTER_TRAIL_COLOR] = argb
    }

    suspend fun setFloatingPointerHideWhenJoystickReleased(enabled: Boolean) = edit {
        it[FLOATING_POINTER_HIDE_ON_RELEASE] = enabled
    }

    suspend fun setFloatingPointerJoystickInnerColor(argb: Int) = edit {
        it[FLOATING_POINTER_JOYSTICK_INNER_COLOR] = argb
    }

    suspend fun setFloatingPointerJoystickOuterColor(argb: Int) = edit {
        it[FLOATING_POINTER_JOYSTICK_OUTER_COLOR] = argb
    }

    suspend fun setFloatingPointerJoystickGradientRadiusFraction(value: Float) = edit {
        it[FLOATING_POINTER_JOYSTICK_GRADIENT] = value.coerceIn(0.5f, 1f)
    }

    suspend fun setFloatingPointerHideOnOutsideClick(enabled: Boolean) = edit {
        it[FLOATING_POINTER_HIDE_OUTSIDE_CLICK] = enabled
    }

    suspend fun setFloatingPointerHideOnQuickSwipe(enabled: Boolean) = edit {
        it[FLOATING_POINTER_HIDE_QUICK_SWIPE] = enabled
    }

    suspend fun setFloatingPointerHideWhenIdle(enabled: Boolean) = edit {
        it[FLOATING_POINTER_HIDE_IDLE] = enabled
    }

    suspend fun setFloatingPointerIdleHideDelayMs(value: Int) = edit {
        it[FLOATING_POINTER_IDLE_DELAY] = value.coerceIn(1000, 10000)
    }

    suspend fun setFloatingPointerRadialMenuEnabled(enabled: Boolean) = edit {
        it[FLOATING_POINTER_RADIAL_ENABLED] = enabled
    }

    suspend fun setFloatingPointerRadialAlwaysVisible(enabled: Boolean) = edit {
        it[FLOATING_POINTER_RADIAL_ALWAYS_VISIBLE] = enabled
    }

    suspend fun setFloatingPointerRadialLongPressMs(value: Int) = edit {
        it[FLOATING_POINTER_RADIAL_LONG_PRESS_MS] = value.coerceIn(200, 2000)
    }

    suspend fun setFloatingPointerRadialOuterDiameterPx(value: Float) = edit {
        it[FLOATING_POINTER_RADIAL_OUTER_SIZE] = value.coerceIn(240f, 720f)
    }

    suspend fun setFloatingPointerRadialInnerDiameterPx(value: Float) = edit {
        it[FLOATING_POINTER_RADIAL_INNER_SIZE] = value.coerceIn(80f, 480f)
    }

    suspend fun setFloatingPointerRadialOuterColor(argb: Int) = edit {
        it[FLOATING_POINTER_RADIAL_OUTER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialInnerColor(argb: Int) = edit {
        it[FLOATING_POINTER_RADIAL_INNER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialDividerThicknessPx(value: Float) = edit {
        it[FLOATING_POINTER_RADIAL_DIVIDER_SIZE] = value.coerceIn(1f, 12f)
    }

    suspend fun setFloatingPointerRadialDividerColor(argb: Int) = edit {
        it[FLOATING_POINTER_RADIAL_DIVIDER_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialIconSizeFraction(value: Float) = edit {
        it[FLOATING_POINTER_RADIAL_ICON_SIZE] = value.coerceIn(0.2f, 0.9f)
    }

    suspend fun setFloatingPointerRadialIconColor(argb: Int) = edit {
        it[FLOATING_POINTER_RADIAL_ICON_COLOR] = argb
    }

    suspend fun setFloatingPointerRadialSlotAction(index: Int, action: GestureAction) = edit { prefs ->
        val current = FloatingPointerRadialMenuCodec.decode(prefs[FLOATING_POINTER_RADIAL_SLOTS] ?: emptySet())
        val updated = current.toMutableList()
        if (index in 0 until FloatingPointerRadialMenuCodec.SLOT_COUNT) {
            updated[index] = action
            prefs[FLOATING_POINTER_RADIAL_SLOTS] = FloatingPointerRadialMenuCodec.encode(updated)
        }
    }

    suspend fun resetFloatingPointerRadialDesignDefaults() = edit { prefs ->
        prefs[FLOATING_POINTER_RADIAL_OUTER_SIZE] = 440f
        prefs[FLOATING_POINTER_RADIAL_INNER_SIZE] = 192f
        prefs[FLOATING_POINTER_RADIAL_OUTER_COLOR] = 0xE62B3D4F.toInt()
        prefs[FLOATING_POINTER_RADIAL_INNER_COLOR] = 0xE61A1A28.toInt()
        prefs[FLOATING_POINTER_RADIAL_DIVIDER_SIZE] = 4f
        prefs[FLOATING_POINTER_RADIAL_DIVIDER_COLOR] = 0x22FFFFFF
        prefs[FLOATING_POINTER_RADIAL_ICON_SIZE] = 0.85f
        prefs[FLOATING_POINTER_RADIAL_ICON_COLOR] = 0xFFFFFFFF.toInt()
    }

    suspend fun setOtpCopyToClipboard(enabled: Boolean) = edit { it[OTP_COPY_TO_CLIPBOARD] = enabled }

    suspend fun setOtpKeywordsRegex(value: String) = edit {
        it[OTP_KEYWORDS_REGEX] = value.ifBlank {
            OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
    }

    suspend fun setOtpUserMatchRules(rules: List<com.slideindex.app.otp.OtpMatchRule>) = edit {
        it[OTP_USER_MATCH_RULES] = OtpMatchRuleCodec.encodeAll(rules)
    }

    suspend fun setOtpDisabledOfficialRuleIds(ids: Set<String>) = edit {
        it[OTP_DISABLED_OFFICIAL_RULE_IDS] = ids
    }

    suspend fun setOtpOfficialRuleEnabled(ruleId: String, enabled: Boolean) = edit { prefs ->
        val current = prefs[OTP_DISABLED_OFFICIAL_RULE_IDS]?.toMutableSet() ?: mutableSetOf()
        if (enabled) {
            current.remove(ruleId)
        } else {
            current.add(ruleId)
        }
        prefs[OTP_DISABLED_OFFICIAL_RULE_IDS] = current
    }

    suspend fun setOtpAutoInputEnabled(enabled: Boolean) = edit { it[OTP_AUTO_INPUT_ENABLED] = enabled }

    suspend fun setOtpAutoConfirmEnabled(enabled: Boolean) = edit { it[OTP_AUTO_CONFIRM_ENABLED] = enabled }

    suspend fun setOtpAutoInputDelayMs(value: Int) = edit {
        it[OTP_AUTO_INPUT_DELAY_MS] = value.coerceIn(0, 5000)
    }

    suspend fun setOtpAutoInputIntervalMs(value: Int) = edit {
        it[OTP_AUTO_INPUT_INTERVAL_MS] = value.coerceIn(0, 500)
    }

    suspend fun setOtpLsposedSmsCaptureEnabled(enabled: Boolean) =
        edit { it[OTP_LSPOSED_SMS_CAPTURE_ENABLED] = enabled }

    suspend fun setOtpLsposedSystemInjectEnabled(enabled: Boolean) =
        edit { it[OTP_LSPOSED_SYSTEM_INJECT_ENABLED] = enabled }

    suspend fun setShakeGesturesEnabled(enabled: Boolean) = edit { it[SHAKE_GESTURES_ENABLED] = enabled }

    suspend fun setDebugPerformanceMonitorEnabled(enabled: Boolean) =
        edit { it[DEBUG_PERFORMANCE_MONITOR] = enabled }

    suspend fun setShakeGestureAction(type: ShakeGestureType, action: GestureAction) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        val updated = current.basicActions.toMutableMap().apply { put(type, action) }
        prefs[SHAKE_GESTURE_ACTIONS] = ShakeGestureCodec.encodeAllActions(updated)
    }

    suspend fun setLockScreenShakeAction(type: ShakeGestureType, action: GestureAction) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        val updated = current.lockScreenActions.toMutableMap().apply { put(type, action) }
        prefs[SHAKE_LOCK_SCREEN_ACTIONS] = ShakeGestureCodec.encodeAllActions(updated)
    }

    suspend fun setPerAppShakeAction(packageName: String, type: ShakeGestureType, action: GestureAction) =
        edit { prefs ->
            val current = readShakeGestureSettings(prefs)
            val perApp = current.perAppActions.toMutableMap()
            val appActions = perApp[packageName].orEmpty().toMutableMap().apply { put(type, action) }
            perApp[packageName] = appActions
            prefs[SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
        }

    suspend fun addPerAppShakeConfig(packageName: String) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        if (packageName in current.perAppActions) return@edit
        val perApp = current.perAppActions.toMutableMap()
        perApp[packageName] = emptyMap()
        prefs[SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
    }

    suspend fun removePerAppShakeConfig(packageName: String) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        val perApp = current.perAppActions.toMutableMap()
        perApp.remove(packageName)
        prefs[SHAKE_PER_APP_ACTIONS] = ShakeGestureCodec.encodePerAppActions(perApp)
    }

    suspend fun setShakeDirectionSensitivity(type: ShakeGestureType, value: Float) = edit { prefs ->
        val current = readShakeGestureSettings(prefs)
        val updated = current.perDirectionSensitivity.toMutableMap().apply {
            put(type, value.coerceIn(1f, 10f))
        }
        prefs[SHAKE_PER_DIRECTION_SENSITIVITY] = ShakeGestureCodec.encodePerDirectionSensitivity(updated)
    }

    suspend fun setLockScreenShakeEnabled(enabled: Boolean) = edit { it[LOCK_SCREEN_SHAKE_ENABLED] = enabled }

    suspend fun setIndependentAppShakeEnabled(enabled: Boolean) =
        edit { it[INDEPENDENT_APP_SHAKE_ENABLED] = enabled }

    suspend fun setShakeGlobalSensitivity(value: Float) = edit {
        it[SHAKE_GLOBAL_SENSITIVITY] = value.coerceIn(1f, 10f)
    }

    suspend fun setShakeIndependentSensitivityEnabled(enabled: Boolean) =
        edit { it[SHAKE_INDEPENDENT_SENSITIVITY_ENABLED] = enabled }

    suspend fun setShakeVibrationFeedbackEnabled(enabled: Boolean) =
        edit { it[SHAKE_VIBRATION_FEEDBACK_ENABLED] = enabled }

    suspend fun setShakeAnimationFeedbackEnabled(enabled: Boolean) =
        edit { it[SHAKE_ANIMATION_FEEDBACK_ENABLED] = enabled }

    suspend fun setShakeAnimationColor(argb: Int) = edit { it[SHAKE_ANIMATION_COLOR] = argb }

    suspend fun setShakeDisableInLandscape(enabled: Boolean) =
        edit { it[SHAKE_DISABLE_IN_LANDSCAPE] = enabled }

    suspend fun addShakeBlacklistedApp(packageName: String) = edit {
        val current = it[SHAKE_BLACKLIST_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SHAKE_BLACKLIST_PACKAGES] = current
    }

    suspend fun removeShakeBlacklistedApp(packageName: String) = edit {
        val current = it[SHAKE_BLACKLIST_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SHAKE_BLACKLIST_PACKAGES] = current
    }

    suspend fun setMessageReminderEnabled(enabled: Boolean) =
        edit { it[MESSAGE_REMINDER_ENABLED] = enabled }

    suspend fun setMessageStyleId(styleId: String) =
        edit { it[MESSAGE_STYLE_ID] = styleId }

    suspend fun setMessagePrimaryStyleEnabled(enabled: Boolean) =
        edit { it[MESSAGE_PRIMARY_STYLE_ENABLED] = enabled }

    suspend fun setMessageDanmakuEnabled(enabled: Boolean) =
        edit { it[MESSAGE_DANMAKU_ENABLED] = enabled }

    suspend fun setMessageThemeId(themeId: String) =
        edit { it[MESSAGE_THEME_ID] = themeId }

    suspend fun setMessageDanmakuThemeId(themeId: String) =
        edit { it[MESSAGE_DANMAKU_THEME_ID] = themeId }

    suspend fun setMessageFloatIconOpacity(opacity: Float) =
        edit { it[MESSAGE_FLOAT_ICON_OPACITY] = opacity.coerceIn(0f, 1f) }

    suspend fun setMessageCardOpacity(opacity: Float) =
        edit { it[MESSAGE_CARD_OPACITY] = opacity.coerceIn(0.2f, 1f) }

    suspend fun setMessageSideBubbleOpacity(opacity: Float) =
        edit { it[MESSAGE_SIDE_BUBBLE_OPACITY] = opacity.coerceIn(0.2f, 1f) }

    suspend fun setMessageFloatIconSizeDp(sizeDp: Float) =
        edit { it[MESSAGE_FLOAT_ICON_SIZE_DP] = sizeDp.coerceIn(32f, 64f) }

    suspend fun setMessageDanmakuOpacity(opacity: Float) =
        edit { it[MESSAGE_DANMAKU_OPACITY] = opacity.coerceIn(0.2f, 1f) }

    suspend fun setMessageCardMaxLines(lines: Int) =
        edit { it[MESSAGE_CARD_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageDanmakuMaxLines(lines: Int) =
        edit { it[MESSAGE_DANMAKU_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageSideMaxCount(count: Int) =
        edit { it[MESSAGE_SIDE_MAX_COUNT] = count.coerceIn(1, 5) }

    suspend fun setMessageSideMaxWidthDp(widthDp: Float) =
        edit { it[MESSAGE_SIDE_MAX_WIDTH_DP] = widthDp.coerceIn(120f, 320f) }

    suspend fun setMessageSideMaxLines(lines: Int) =
        edit { it[MESSAGE_SIDE_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageAutoDismissSeconds(seconds: Int) =
        edit { it[MESSAGE_AUTO_DISMISS_SECONDS] = seconds.coerceIn(0, 60) }

    suspend fun setMessageHideInLandscape(enabled: Boolean) =
        edit { it[MESSAGE_HIDE_IN_LANDSCAPE] = enabled }

    suspend fun setMessagePortraitDanmaku(enabled: Boolean) =
        edit { it[MESSAGE_PORTRAIT_DANMAKU] = enabled }

    suspend fun setMessageLandscapeDanmaku(enabled: Boolean) =
        edit { it[MESSAGE_LANDSCAPE_DANMAKU] = enabled }

    suspend fun setMessageGestureAction(slot: String, action: MessageAction) = edit { prefs ->
        val current = MessageSettingsCodec.decodeGestureActions(
            prefs[MESSAGE_GESTURE_ACTIONS] ?: emptySet(),
        ).toMutableMap()
        current[slot] = action
        val encoded = current.map { (key, value) ->
            MessageSettingsCodec.encodeGestureAction(key, value)
        }.toSet()
        prefs[MESSAGE_GESTURE_ACTIONS] = encoded
    }

    suspend fun addMessageEnabledPackage(packageName: String) = edit {
        val current = it[MESSAGE_ENABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[MESSAGE_ENABLED_PACKAGES] = current
    }

    suspend fun removeMessageEnabledPackage(packageName: String) = edit { prefs ->
        val current = prefs[MESSAGE_ENABLED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        prefs[MESSAGE_ENABLED_PACKAGES] = current
        val rules = MessageAppFilterCodec.decodeAll(prefs[MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        rules.remove(packageName)
        prefs[MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(rules.values)
    }

    suspend fun addMessageDisabledPackage(packageName: String) = edit {
        val current = it[MESSAGE_DISABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[MESSAGE_DISABLED_PACKAGES] = current
    }

    suspend fun removeMessageDisabledPackage(packageName: String) = edit {
        val current = it[MESSAGE_DISABLED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[MESSAGE_DISABLED_PACKAGES] = current
    }

    suspend fun addMessageDndPackage(packageName: String) = edit {
        val current = it[MESSAGE_DND_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[MESSAGE_DND_PACKAGES] = current
    }

    suspend fun removeMessageDndPackage(packageName: String) = edit {
        val current = it[MESSAGE_DND_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[MESSAGE_DND_PACKAGES] = current
    }

    suspend fun setMessageSuppressWhenSystemDnd(enabled: Boolean) =
        edit { it[MESSAGE_SUPPRESS_WHEN_SYSTEM_DND] = enabled }

    suspend fun upsertMessageAppFilterRule(rule: MessageAppFilterRule) = edit { prefs ->
        val current = MessageAppFilterCodec.decodeAll(prefs[MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        if (rule.hasCustomFilter()) {
            current[rule.packageName] = rule
        } else {
            current.remove(rule.packageName)
        }
        prefs[MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(current.values)
    }

    suspend fun removeMessageAppFilterRule(packageName: String) = edit { prefs ->
        val current = MessageAppFilterCodec.decodeAll(prefs[MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        current.remove(packageName)
        prefs[MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(current.values)
    }

    suspend fun resetFloatingPointerVisualDefaults() = edit { prefs ->
        prefs[FLOATING_POINTER_POINTER_SIZE] = 100f
        prefs[FLOATING_POINTER_RING_THICKNESS] = 12f
        prefs[FLOATING_POINTER_DOT_DIAMETER] = 15f
        prefs[FLOATING_POINTER_RING_COLOR] = 0xFFFFFFFF.toInt()
        prefs[FLOATING_POINTER_FILL_COLOR] = 0x19000000
        prefs[FLOATING_POINTER_DOT_COLOR] = 0xFFFFFFFF.toInt()
        prefs[FLOATING_POINTER_CLICK_VISUAL_FEEDBACK] = true
        prefs[FLOATING_POINTER_CLICK_HAPTIC] = true
        prefs[FLOATING_POINTER_RIPPLE_COLOR] = 0xFFFD746C.toInt()
        prefs[FLOATING_POINTER_RIPPLE_SIZE_DP] = 80f
        prefs[FLOATING_POINTER_RIPPLE_DURATION_MS] = 500
        prefs[FLOATING_POINTER_TRAIL_TYPE] = FloatingPointerTrailType.HIGH_DETAIL.id
        prefs[FLOATING_POINTER_TRAIL_DURATION] = 150
        prefs[FLOATING_POINTER_TRAIL_COLOR] = 0x66FF5252
        prefs[FLOATING_POINTER_HIDE_ON_RELEASE] = false
    }

    suspend fun resetFloatingPointerJoystickVisualDefaults() = edit { prefs ->
        prefs[FLOATING_POINTER_JOYSTICK_SIZE] = 275f
        prefs[FLOATING_POINTER_JOYSTICK_INNER_COLOR] = 0x80FFFFFF.toInt()
        prefs[FLOATING_POINTER_JOYSTICK_OUTER_COLOR] = 0x80C0C0C0.toInt()
        prefs[FLOATING_POINTER_JOYSTICK_GRADIENT] = 1f
    }

    suspend fun resetFloatingPointerJoystickBehaviorDefaults() = edit { prefs ->
        prefs[FLOATING_POINTER_HIDE_OUTSIDE_CLICK] = true
        prefs[FLOATING_POINTER_HIDE_QUICK_SWIPE] = true
        prefs[FLOATING_POINTER_HIDE_IDLE] = true
        prefs[FLOATING_POINTER_IDLE_DELAY] = 3000
    }

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

    suspend fun setWidgetPanelPages(
        pages: List<com.slideindex.app.widget.WidgetPanelPage>,
    ) = edit { prefs ->
        prefs[WIDGET_PANEL_PAGES] = WidgetPanelCodec.encodeAll(pages)
    }

    suspend fun setWidgetPanelBlurEnabled(enabled: Boolean) = edit { prefs ->
        prefs[WIDGET_PANEL_BLUR] = enabled
    }

    suspend fun setWidgetPanelWidthFraction(fraction: Float) = edit { prefs ->
        prefs[WIDGET_PANEL_WIDTH] = fraction.coerceIn(0.5f, 0.95f)
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

    private fun readMessageReminderSettings(prefs: Preferences): MessageSettings {
        val base = MessageSettings()
        val withGestures = MessageSettingsCodec.applyGestureActions(
            base,
            prefs[MESSAGE_GESTURE_ACTIONS] ?: emptySet(),
        )
        return withGestures.copy(
            enabled = prefs[MESSAGE_REMINDER_ENABLED] ?: false,
            styleId = prefs[MESSAGE_STYLE_ID] ?: base.styleId,
            primaryStyleEnabled = prefs[MESSAGE_PRIMARY_STYLE_ENABLED] ?: true,
            danmakuEnabled = prefs[MESSAGE_DANMAKU_ENABLED] ?: true,
            themeId = MessageThemeIds.normalizeThemeId(
                prefs[MESSAGE_THEME_ID] ?: base.themeId,
            ),
            danmakuThemeId = MessageThemeIds.normalizeThemeId(
                prefs[MESSAGE_DANMAKU_THEME_ID] ?: base.danmakuThemeId,
            ),
            floatIconOpacity = prefs[MESSAGE_FLOAT_ICON_OPACITY]
                ?: prefs[MESSAGE_OPACITY]
                ?: base.floatIconOpacity,
            cardOpacity = prefs[MESSAGE_CARD_OPACITY]
                ?: prefs[MESSAGE_OPACITY]
                ?: base.cardOpacity,
            sideBubbleOpacity = prefs[MESSAGE_SIDE_BUBBLE_OPACITY]
                ?: prefs[MESSAGE_OPACITY]
                ?: base.sideBubbleOpacity,
            danmakuOpacity = prefs[MESSAGE_DANMAKU_OPACITY] ?: base.danmakuOpacity,
            cardMaxLines = prefs[MESSAGE_CARD_MAX_LINES] ?: base.cardMaxLines,
            danmakuMaxLines = prefs[MESSAGE_DANMAKU_MAX_LINES] ?: base.danmakuMaxLines,
            sideMaxCount = prefs[MESSAGE_SIDE_MAX_COUNT] ?: base.sideMaxCount,
            sideMaxWidthDp = prefs[MESSAGE_SIDE_MAX_WIDTH_DP] ?: base.sideMaxWidthDp,
            sideMaxLines = prefs[MESSAGE_SIDE_MAX_LINES] ?: base.sideMaxLines,
            floatIconSizeDp = prefs[MESSAGE_FLOAT_ICON_SIZE_DP] ?: base.floatIconSizeDp,
            autoDismissSeconds = prefs[MESSAGE_AUTO_DISMISS_SECONDS] ?: base.autoDismissSeconds,
            hideInLandscape = prefs[MESSAGE_HIDE_IN_LANDSCAPE] ?: false,
            portraitDanmaku = prefs[MESSAGE_PORTRAIT_DANMAKU] ?: true,
            landscapeDanmaku = prefs[MESSAGE_LANDSCAPE_DANMAKU] ?: true,
            enabledPackages = prefs[MESSAGE_ENABLED_PACKAGES] ?: emptySet(),
            disabledPackages = prefs[MESSAGE_DISABLED_PACKAGES] ?: emptySet(),
            dndPackages = prefs[MESSAGE_DND_PACKAGES] ?: emptySet(),
            suppressWhenSystemDnd = prefs[MESSAGE_SUPPRESS_WHEN_SYSTEM_DND] ?: false,
            appFilterRules = MessageAppFilterCodec.decodeAll(
                prefs[MESSAGE_APP_FILTER_RULES] ?: emptySet(),
            ),
        )
    }

    private fun readShakeGestureSettings(prefs: Preferences): ShakeGestureSettings =
        ShakeGestureSettings(
            enabled = prefs[SHAKE_GESTURES_ENABLED] ?: true,
            basicActions = ShakeGestureCodec.decodeAllActions(prefs[SHAKE_GESTURE_ACTIONS] ?: emptySet()),
            lockScreenShakeEnabled = prefs[LOCK_SCREEN_SHAKE_ENABLED] ?: false,
            lockScreenActions = ShakeGestureCodec.decodeAllActions(prefs[SHAKE_LOCK_SCREEN_ACTIONS] ?: emptySet()),
            independentAppShakeEnabled = prefs[INDEPENDENT_APP_SHAKE_ENABLED] ?: false,
            perAppActions = ShakeGestureCodec.decodePerAppActions(prefs[SHAKE_PER_APP_ACTIONS] ?: emptySet()),
            globalSensitivity = prefs[SHAKE_GLOBAL_SENSITIVITY] ?: 6.0f,
            independentSensitivityEnabled = prefs[SHAKE_INDEPENDENT_SENSITIVITY_ENABLED] ?: false,
            perDirectionSensitivity = ShakeGestureCodec.decodePerDirectionSensitivity(
                prefs[SHAKE_PER_DIRECTION_SENSITIVITY] ?: emptySet(),
            ),
            vibrationFeedbackEnabled = prefs[SHAKE_VIBRATION_FEEDBACK_ENABLED] ?: true,
            animationFeedbackEnabled = prefs[SHAKE_ANIMATION_FEEDBACK_ENABLED] ?: false,
            animationColorArgb = prefs[SHAKE_ANIMATION_COLOR] ?: 0xFF424242.toInt(),
            disableInLandscape = prefs[SHAKE_DISABLE_IN_LANDSCAPE] ?: false,
            blacklistedPackages = prefs[SHAKE_BLACKLIST_PACKAGES] ?: emptySet(),
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
        private val WIDGET_PANEL_PAGES = stringSetPreferencesKey("widget_panel_pages")
        private val WIDGET_PANEL_WIDTH = floatPreferencesKey("widget_panel_width_fraction")
        private val WIDGET_PANEL_HEIGHT = floatPreferencesKey("widget_panel_height_fraction")
        private val WIDGET_PANEL_TOP = floatPreferencesKey("widget_panel_top_fraction")
        private val WIDGET_PANEL_BLUR = booleanPreferencesKey("widget_panel_blur_enabled")
        private val FLOATING_POINTER_JOYSTICK_AREA_WIDTH = floatPreferencesKey("floating_pointer_joystick_area_width")
        private val FLOATING_POINTER_JOYSTICK_AREA_HEIGHT = floatPreferencesKey("floating_pointer_joystick_area_height")
        private val FLOATING_POINTER_JOYSTICK_AREA_ZOOM = floatPreferencesKey("floating_pointer_joystick_area_zoom")
        private val FLOATING_POINTER_JOYSTICK_MATCH_ASPECT = booleanPreferencesKey("floating_pointer_joystick_match_aspect")
        private val FLOATING_POINTER_JOYSTICK_SIZE = floatPreferencesKey("floating_pointer_joystick_size_px")
        private val FLOATING_POINTER_POINTER_SIZE = floatPreferencesKey("floating_pointer_pointer_size_px")
        private val FLOATING_POINTER_DESIGN_ID = stringPreferencesKey("floating_pointer_design_id")
        private val FLOATING_POINTER_RING_THICKNESS = floatPreferencesKey("floating_pointer_ring_thickness_px")
        private val FLOATING_POINTER_DOT_DIAMETER = floatPreferencesKey("floating_pointer_dot_diameter_px")
        private val FLOATING_POINTER_RING_COLOR = intPreferencesKey("floating_pointer_ring_color")
        private val FLOATING_POINTER_FILL_COLOR = intPreferencesKey("floating_pointer_fill_color")
        private val FLOATING_POINTER_DOT_COLOR = intPreferencesKey("floating_pointer_dot_color")
        private val FLOATING_POINTER_CLICK_VISUAL_FEEDBACK =
            booleanPreferencesKey("floating_pointer_click_visual_feedback")
        private val FLOATING_POINTER_CLICK_HAPTIC = booleanPreferencesKey("floating_pointer_click_haptic")
        private val FLOATING_POINTER_RIPPLE_COLOR = intPreferencesKey("floating_pointer_ripple_color")
        private val FLOATING_POINTER_RIPPLE_SIZE_DP = floatPreferencesKey("floating_pointer_ripple_size_dp")
        private val FLOATING_POINTER_RIPPLE_DURATION_MS = intPreferencesKey("floating_pointer_ripple_duration_ms")
        private val FLOATING_POINTER_TRAIL_TYPE = intPreferencesKey("floating_pointer_trail_type")
        private val FLOATING_POINTER_TRAIL_DURATION = intPreferencesKey("floating_pointer_trail_duration_ms")
        private val FLOATING_POINTER_TRAIL_COLOR = intPreferencesKey("floating_pointer_trail_color")
        private val FLOATING_POINTER_HIDE_ON_RELEASE = booleanPreferencesKey("floating_pointer_hide_on_release")
        private val FLOATING_POINTER_JOYSTICK_INNER_COLOR = intPreferencesKey("floating_pointer_joystick_inner_color")
        private val FLOATING_POINTER_JOYSTICK_OUTER_COLOR = intPreferencesKey("floating_pointer_joystick_outer_color")
        private val FLOATING_POINTER_JOYSTICK_GRADIENT = floatPreferencesKey("floating_pointer_joystick_gradient")
        private val FLOATING_POINTER_HIDE_OUTSIDE_CLICK = booleanPreferencesKey("floating_pointer_hide_outside_click")
        private val FLOATING_POINTER_HIDE_QUICK_SWIPE = booleanPreferencesKey("floating_pointer_hide_quick_swipe")
        private val FLOATING_POINTER_HIDE_IDLE = booleanPreferencesKey("floating_pointer_hide_idle")
        private val FLOATING_POINTER_IDLE_DELAY = intPreferencesKey("floating_pointer_idle_delay_ms")
        private val FLOATING_POINTER_RADIAL_ENABLED = booleanPreferencesKey("floating_pointer_radial_enabled")
        private val FLOATING_POINTER_RADIAL_ALWAYS_VISIBLE =
            booleanPreferencesKey("floating_pointer_radial_always_visible")
        private val FLOATING_POINTER_RADIAL_LONG_PRESS_MS = intPreferencesKey("floating_pointer_radial_long_press_ms")
        private val FLOATING_POINTER_RADIAL_OUTER_SIZE = floatPreferencesKey("floating_pointer_radial_outer_size_px")
        private val FLOATING_POINTER_RADIAL_INNER_SIZE = floatPreferencesKey("floating_pointer_radial_inner_size_px")
        private val FLOATING_POINTER_RADIAL_OUTER_COLOR = intPreferencesKey("floating_pointer_radial_outer_color")
        private val FLOATING_POINTER_RADIAL_INNER_COLOR = intPreferencesKey("floating_pointer_radial_inner_color")
        private val FLOATING_POINTER_RADIAL_DIVIDER_SIZE = floatPreferencesKey("floating_pointer_radial_divider_size_px")
        private val FLOATING_POINTER_RADIAL_DIVIDER_COLOR = intPreferencesKey("floating_pointer_radial_divider_color")
        private val FLOATING_POINTER_RADIAL_ICON_SIZE = floatPreferencesKey("floating_pointer_radial_icon_size_fraction")
        private val FLOATING_POINTER_RADIAL_ICON_COLOR = intPreferencesKey("floating_pointer_radial_icon_color")
        private val FLOATING_POINTER_RADIAL_SLOTS = stringSetPreferencesKey("floating_pointer_radial_slots")
        private val OTP_COPY_TO_CLIPBOARD = booleanPreferencesKey("otp_copy_to_clipboard")
        private val OTP_KEYWORDS_REGEX = stringPreferencesKey("otp_keywords_regex")
        private val OTP_USER_MATCH_RULES = stringSetPreferencesKey("otp_user_match_rules")
        private val OTP_DISABLED_OFFICIAL_RULE_IDS = stringSetPreferencesKey("otp_disabled_official_rule_ids")
        private val OTP_AUTO_INPUT_ENABLED = booleanPreferencesKey("otp_auto_input_enabled")
        private val OTP_AUTO_CONFIRM_ENABLED = booleanPreferencesKey("otp_auto_confirm_enabled")
        private val OTP_AUTO_INPUT_DELAY_MS = intPreferencesKey("otp_auto_input_delay_ms")
        private val OTP_AUTO_INPUT_INTERVAL_MS = intPreferencesKey("otp_auto_input_interval_ms")
        private val OTP_LSPOSED_SMS_CAPTURE_ENABLED = booleanPreferencesKey("otp_lsposed_sms_capture_enabled")
        private val OTP_LSPOSED_SYSTEM_INJECT_ENABLED = booleanPreferencesKey("otp_lsposed_system_inject_enabled")
        private val SHAKE_GESTURES_ENABLED = booleanPreferencesKey("shake_gestures_enabled")
        private val SHAKE_GESTURE_ACTIONS = stringSetPreferencesKey("shake_gesture_actions")
        private val SHAKE_MENU_ACTIONS = stringSetPreferencesKey("shake_menu_actions")
        private val SHAKE_HOLD_SCREEN_ACTIONS = stringSetPreferencesKey("shake_hold_screen_actions")
        private val SHAKE_HOLD_GESTURE_BAR_ACTIONS = stringSetPreferencesKey("shake_hold_gesture_bar_actions")
        private val SHAKE_LOCK_SCREEN_ACTIONS = stringSetPreferencesKey("shake_lock_screen_actions")
        private val SHAKE_PER_APP_ACTIONS = stringSetPreferencesKey("shake_per_app_actions")
        private val SHAKE_PER_DIRECTION_SENSITIVITY = stringSetPreferencesKey("shake_per_direction_sensitivity")
        private val SHAKE_MENU_ENABLED = booleanPreferencesKey("shake_menu_enabled")
        private val HOLD_SHAKE_ENABLED = booleanPreferencesKey("hold_shake_enabled")
        private val LOCK_SCREEN_SHAKE_ENABLED = booleanPreferencesKey("lock_screen_shake_enabled")
        private val INDEPENDENT_APP_SHAKE_ENABLED = booleanPreferencesKey("independent_app_shake_enabled")
        private val SHAKE_GLOBAL_SENSITIVITY = floatPreferencesKey("shake_global_sensitivity")
        private val SHAKE_INDEPENDENT_SENSITIVITY_ENABLED =
            booleanPreferencesKey("shake_independent_sensitivity_enabled")
        private val SHAKE_VIBRATION_FEEDBACK_ENABLED = booleanPreferencesKey("shake_vibration_feedback_enabled")
        private val SHAKE_VIBRATION_TYPE = intPreferencesKey("shake_vibration_type")
        private val SHAKE_VIBRATION_INTENSITY = intPreferencesKey("shake_vibration_intensity")
        private val SHAKE_ANIMATION_FEEDBACK_ENABLED = booleanPreferencesKey("shake_animation_feedback_enabled")
        private val SHAKE_ANIMATION_COLOR = intPreferencesKey("shake_animation_color")
        private val SHAKE_DISABLE_IN_LANDSCAPE = booleanPreferencesKey("shake_disable_in_landscape")
        private val SHAKE_BLACKLIST_PACKAGES = stringSetPreferencesKey("shake_blacklist_packages")
        private val MESSAGE_REMINDER_ENABLED = booleanPreferencesKey("message_reminder_enabled")
        private val MESSAGE_STYLE_ID = stringPreferencesKey("message_style_id")
        private val MESSAGE_PRIMARY_STYLE_ENABLED = booleanPreferencesKey("message_primary_style_enabled")
        private val MESSAGE_DANMAKU_ENABLED = booleanPreferencesKey("message_danmaku_enabled")
        private val MESSAGE_THEME_ID = stringPreferencesKey("message_theme_id")
        private val MESSAGE_DANMAKU_THEME_ID = stringPreferencesKey("message_danmaku_theme_id")
        private val MESSAGE_OPACITY = floatPreferencesKey("message_opacity")
        private val MESSAGE_FLOAT_ICON_OPACITY = floatPreferencesKey("message_float_icon_opacity")
        private val MESSAGE_CARD_OPACITY = floatPreferencesKey("message_card_opacity")
        private val MESSAGE_SIDE_BUBBLE_OPACITY = floatPreferencesKey("message_side_bubble_opacity")
        private val MESSAGE_DANMAKU_OPACITY = floatPreferencesKey("message_danmaku_opacity")
        private val MESSAGE_CARD_MAX_LINES = intPreferencesKey("message_card_max_lines")
        private val MESSAGE_DANMAKU_MAX_LINES = intPreferencesKey("message_danmaku_max_lines")
        private val MESSAGE_SIDE_MAX_COUNT = intPreferencesKey("message_side_max_count")
        private val MESSAGE_SIDE_MAX_WIDTH_DP = floatPreferencesKey("message_side_max_width_dp")
        private val MESSAGE_SIDE_MAX_LINES = intPreferencesKey("message_side_max_lines")
        private val MESSAGE_FLOAT_ICON_SIZE_DP = floatPreferencesKey("message_float_icon_size_dp")
        private val MESSAGE_AUTO_DISMISS_SECONDS = intPreferencesKey("message_auto_dismiss_seconds")
        private val MESSAGE_HIDE_IN_LANDSCAPE = booleanPreferencesKey("message_hide_in_landscape")
        private val MESSAGE_PORTRAIT_DANMAKU = booleanPreferencesKey("message_portrait_danmaku")
        private val MESSAGE_LANDSCAPE_DANMAKU = booleanPreferencesKey("message_landscape_danmaku")
        private val MESSAGE_GESTURE_ACTIONS = stringSetPreferencesKey("message_gesture_actions")
        private val MESSAGE_ENABLED_PACKAGES = stringSetPreferencesKey("message_enabled_packages")
        private val MESSAGE_DISABLED_PACKAGES = stringSetPreferencesKey("message_disabled_packages")
        private val MESSAGE_DND_PACKAGES = stringSetPreferencesKey("message_dnd_packages")
        private val MESSAGE_SUPPRESS_WHEN_SYSTEM_DND =
            booleanPreferencesKey("message_suppress_when_system_dnd")
        private val MESSAGE_APP_FILTER_RULES = stringSetPreferencesKey("message_app_filter_rules")
        private val DEBUG_PERFORMANCE_MONITOR = booleanPreferencesKey("debug_performance_monitor")
    }
}
