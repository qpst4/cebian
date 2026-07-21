package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.floatball.FloatBallGestureCodec
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.GestureRuleCodec
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleCodec
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.message.MessageAppFilterCodec
import com.slideindex.app.message.MessageThemeIds
import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.otp.OtpMatchRuleCodec
import com.slideindex.app.shake.ShakeGestureCodec
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.shell.ShellCommandCodec
import com.slideindex.app.widget.WidgetPanelCodec

internal object SettingsSnapshotReader {
    fun read(prefs: Preferences): AppSettings {
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
        return AppSettings(
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
            floatingPointerSensitivityFraction = readFloatingPointerSensitivityFraction(prefs),
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
            floatingPointerClickDistanceThresholdDp =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_CLICK_DISTANCE_THRESHOLD_DP] ?: 6f,
            floatingPointerJoystickInnerColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_INNER_COLOR] ?: 0x80FFFFFF.toInt(),
            floatingPointerJoystickOuterColorArgb = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_OUTER_COLOR] ?: 0x80C0C0C0.toInt(),
            floatingPointerJoystickGradientRadiusFraction = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_GRADIENT] ?: 1f,
            floatingPointerHideOnOutsideClick = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_OUTSIDE_CLICK] ?: true,
            floatingPointerHideOnQuickSwipe = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_QUICK_SWIPE] ?: true,
            floatingPointerHideWhenIdle = prefs[SettingsPreferenceKeys.FLOATING_POINTER_HIDE_IDLE] ?: true,
            floatingPointerIdleHideDelayMs = prefs[SettingsPreferenceKeys.FLOATING_POINTER_IDLE_DELAY] ?: 3000,
            floatingPointerJoystickLongPressAction = readFloatingPointerJoystickLongPressAction(prefs),
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
            floatingPointerEdgeThresholdDp = prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_THRESHOLD_DP] ?: 30f,
            floatingPointerEdgePreviewSensitivity =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_SENSITIVITY] ?: 3,
            floatingPointerEdgePreviewGlowSize =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_GLOW_SIZE] ?: 4,
            floatingPointerEdgePreviewShowIcon =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_PREVIEW_SHOW_ICON] ?: true,
            floatingPointerEdgeVisualSizeDp =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_SIZE_DP] ?: 0f,
            floatingPointerEdgeVisualOpacity =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_OPACITY] ?: 75,
            floatingPointerEdgeVisualColorArgb =
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_VISUAL_COLOR] ?: 0xFFFD746C.toInt(),
            floatingPointerEdgeActionsConfig = FloatingPointerEdgeActionsCodec.decode(
                prefs[SettingsPreferenceKeys.FLOATING_POINTER_EDGE_ACTIONS] ?: emptySet(),
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
            onboardingCompleted = prefs[SettingsPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
            floatBallEnabled = prefs[SettingsPreferenceKeys.FLOAT_BALL_ENABLED] ?: false,
            floatBallSizeDp = prefs[SettingsPreferenceKeys.FLOAT_BALL_SIZE_DP] ?: 48f,
            floatBallOpacity = prefs[SettingsPreferenceKeys.FLOAT_BALL_OPACITY] ?: 0.88f,
            floatBallVisibleFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_VISIBLE_FRACTION] ?: 1f,
            floatBallCustomCenterXFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_POSITION_X_FRACTION] ?: 0.92f,
            floatBallPositionYFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_POSITION_Y_FRACTION] ?: 0.55f,
            floatBallOcrFallbackEnabled = prefs[SettingsPreferenceKeys.FLOAT_BALL_OCR_FALLBACK_ENABLED] ?: true,
            floatBallOcrModelId = prefs[SettingsPreferenceKeys.FLOAT_BALL_OCR_MODEL_ID].orEmpty(),
            ocrDownloadWifiOnly = prefs[SettingsPreferenceKeys.OCR_DOWNLOAD_WIFI_ONLY] ?: true,
            floatBallPointerSpeedFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SPEED_FRACTION]
                ?: 0.35f,
            floatBallPositionMode = FloatBallPositionMode.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_POSITION_MODE],
            ),
            floatBallActiveSide = FloatBallSide.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_ACTIVE_SIDE],
            ),
            floatBallLineHeightFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_LINE_HEIGHT_FRACTION] ?: 0.08f,
            floatBallLineWidthFraction = prefs[SettingsPreferenceKeys.FLOAT_BALL_LINE_WIDTH_FRACTION] ?: 0.30f,
            floatBallLineOpacity = prefs[SettingsPreferenceKeys.FLOAT_BALL_LINE_OPACITY] ?: 0.9f,
            floatBallGestureActions = readFloatBallGestureActions(prefs),
            floatBallStyleType = FloatBallStyleType.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_STYLE_TYPE],
            ),
            floatBallCustomImageUri = prefs[SettingsPreferenceKeys.FLOAT_BALL_CUSTOM_IMAGE_URI].orEmpty(),
            floatBallSlideshowUris = prefs[SettingsPreferenceKeys.FLOAT_BALL_SLIDESHOW_URIS]
                ?.filter { it.isNotBlank() }
                ?.toList()
                ?: emptyList(),
            floatBallGifUri = prefs[SettingsPreferenceKeys.FLOAT_BALL_GIF_URI].orEmpty(),
            floatBallPickOffsetDp = prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_OFFSET_DP] ?: 48f,
            floatBallPickTextSizeSp =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_TEXT_SIZE_SP]?.coerceIn(12f, 22f) ?: 15f,
            floatBallPickBottomTransitionFraction =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_PICK_BOTTOM_TRANSITION_FRACTION]?.coerceIn(0.04f, 0.25f)
                    ?: 0.22f,
            floatBallPointerSlopDp = prefs[SettingsPreferenceKeys.FLOAT_BALL_POINTER_SLOP_DP] ?: 8f,
            floatBallDownSwipeShortPercent =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_DOWN_SWIPE_SHORT_PERCENT] ?: 200f,
            floatBallSideSwipeShortPercent =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_SIDE_SWIPE_SHORT_PERCENT] ?: 320f,
            floatBallUpSwipeShortPercent =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_UP_SWIPE_SHORT_PERCENT] ?: 200f,
            floatBallInstantTranslate = prefs[SettingsPreferenceKeys.FLOAT_BALL_INSTANT_TRANSLATE] ?: false,
            floatBallTranslateEngine = FloatBallTranslateEngine.fromStorageKey(
                prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_ENGINE],
            ),
            floatBallTranslateTargetLang = prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_TARGET_LANG]
                ?: "zh-CN",
            floatBallImageSearchPickPanelTransparency =
                prefs[SettingsPreferenceKeys.FLOAT_BALL_IMAGE_SEARCH_PICK_PANEL_TRANSPARENCY]?.coerceIn(0f, 1f)
                    ?: prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_PICK_PANEL_TRANSPARENCY]?.coerceIn(0f, 1f)
                    ?: prefs[SettingsPreferenceKeys.FLOAT_BALL_TRANSLATE_PICK_PANEL_ALPHA]
                        ?.let { alpha -> (1f - alpha).coerceIn(0f, 1f) }
                    ?: 0.65f,
            shareImageOcrHistoryEnabled = prefs[SettingsPreferenceKeys.SHARE_IMAGE_OCR_HISTORY_ENABLED] ?: true,
            defaultImageViewerPackage = prefs[SettingsPreferenceKeys.DEFAULT_IMAGE_VIEWER_PACKAGE],
            searchEngines = readSearchEngines(prefs),
            searchEngineGridColumns = prefs[SettingsPreferenceKeys.SEARCH_ENGINE_GRID_COLUMNS]?.coerceIn(3, 7) ?: 5,
            searchEngineGridRows = prefs[SettingsPreferenceKeys.SEARCH_ENGINE_GRID_ROWS]?.coerceIn(1, 4) ?: 2,
            searchEngineShowLabels = prefs[SettingsPreferenceKeys.SEARCH_ENGINE_SHOW_LABELS] ?: true,
            searchPanelDefaultEngineId = prefs[SettingsPreferenceKeys.SEARCH_PANEL_DEFAULT_ENGINE_ID],
            searchPanelInputBehavior = prefs[SettingsPreferenceKeys.SEARCH_PANEL_INPUT_BEHAVIOR]
                ?.let { name -> runCatching { SearchPanelInputBehavior.valueOf(name) }.getOrNull() }
                ?: SearchPanelInputBehavior.KEEP,
            aggregatedImageSearchEngines = readAggregatedImageSearchEngines(prefs),
        ).withResolvedHandleEdgeWidths()
    }

    private fun readAggregatedImageSearchEngines(prefs: Preferences): List<AggregatedImageSearchEngineConfig> {
        val initialized = prefs[SettingsPreferenceKeys.AGGREGATED_IMAGE_SEARCH_ENGINES_INITIALIZED] ?: false
        if (!initialized) return AggregatedImageSearchEngineCatalog.defaultConfigs()
        return AggregatedImageSearchEnginePreferencesStore.decode(
            prefs[SettingsPreferenceKeys.AGGREGATED_IMAGE_SEARCH_ENGINES_JSON],
        )
    }

    private fun readSearchEngines(prefs: Preferences): List<SearchEngineConfig> {
        val initialized = prefs[SettingsPreferenceKeys.SEARCH_ENGINES_INITIALIZED] ?: false
        if (!initialized) return SearchEngineCatalog.defaultEngines()
        return SearchEngineStore.decode(prefs[SettingsPreferenceKeys.SEARCH_ENGINES_JSON])
    }

    fun readFloatBallGestureActions(prefs: Preferences): Map<FloatBallGestureType, GestureAction> {
        val decoded = FloatBallGestureCodec.decodeAll(
            prefs[SettingsPreferenceKeys.FLOAT_BALL_GESTURE_ACTIONS] ?: emptySet(),
        )
        return decoded.ifEmpty { FloatBallGestureCodec.defaultActions() }
    }

    fun readShakeGestureSettings(prefs: Preferences): ShakeGestureSettings =
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

    private fun readFloatingPointerSensitivityFraction(prefs: Preferences): Float {
        prefs[SettingsPreferenceKeys.FLOATING_POINTER_SENSITIVITY]?.let { stored ->
            return stored.coerceIn(0.2f, 0.75f)
        }
        val legacyWidth = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_WIDTH] ?: 703f
        val legacyZoom = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_AREA_ZOOM] ?: 0.8f
        val legacyTravelPx = legacyWidth.coerceIn(120f, 800f) * legacyZoom.coerceIn(0.1f, 1f)
        val travelFraction =
            (legacyTravelPx / LEGACY_POINTER_TRAVEL_REFERENCE_WIDTH_PX).coerceIn(0.2f, 0.75f)
        return 0.2f + 0.75f - travelFraction
    }

    private fun readFloatingPointerJoystickLongPressAction(prefs: Preferences): com.slideindex.app.gesture.GestureAction {
        val encoded = prefs[SettingsPreferenceKeys.FLOATING_POINTER_JOYSTICK_LONG_PRESS_ACTION]
        if (encoded.isNullOrBlank()) return com.slideindex.app.gesture.GestureAction.OpenFloatingPointerRadialMenu
        return com.slideindex.app.launcher.QuickLauncherItemCodec.parseActionPayload(encoded)
            ?: com.slideindex.app.gesture.GestureAction.OpenFloatingPointerRadialMenu
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

    private fun resolveOtpKeywordsRegex(stored: String?): String {
        if (stored == null) {
            return OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
        if (stored == OtpKeywords.LEGACY_DEFAULT_KEYWORDS_REGEX) {
            return OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
        return stored
    }

    private fun readQuickLauncherItems(prefs: Preferences): List<com.slideindex.app.launcher.QuickLauncherItem> {
        val unified = QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER] ?: emptySet())
        if (unified.isNotEmpty()) return unified
        val left = QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_LEFT] ?: emptySet())
        if (left.isNotEmpty()) return left
        return QuickLauncherItemCodec.decodeAll(prefs[SettingsPreferenceKeys.QUICK_LAUNCHER_RIGHT] ?: emptySet())
    }

    private const val LEGACY_POINTER_TRAVEL_REFERENCE_WIDTH_PX = 1080f
}
