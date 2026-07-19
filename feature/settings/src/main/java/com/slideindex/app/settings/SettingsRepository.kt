package com.slideindex.app.settings

import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.shell.ShellCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val editor: SettingsPreferencesEditor,
    private val backupManager: SettingsBackupManager,
    private val edge: EdgeSettingsMutator,
    private val overlay: OverlaySettingsMutator,
    private val shake: ShakeSettingsMutator,
    private val message: MessageSettingsMutator,
    private val otp: OtpSettingsMutator,
) {
    @Volatile
    private var cachedSettings: AppSettings = AppSettings()

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: Flow<AppSettings> = editor.settings

    init {
        cacheScope.launch {
            settings.collect { cachedSettings = it }
        }
    }

    fun readSnapshot(): AppSettings = cachedSettings

    suspend fun exportSettings(
        appVersionName: String,
        sensitive: SensitiveBackupSections? = null,
    ): Result<String> =
        backupManager.exportSettings(appVersionName, sensitive)

    suspend fun importSettings(
        rawJson: String,
        replaceExisting: Boolean = true,
    ): Result<SettingsBackupImportResult> =
        backupManager.importSettings(rawJson, replaceExisting)

    suspend fun previewImport(rawJson: String): Result<SettingsBackupPreview> =
        backupManager.previewImport(rawJson)

    suspend fun setOnboardingCompleted(completed: Boolean) = edge.setOnboardingCompleted(completed)

    suspend fun setServiceEnabled(enabled: Boolean) = edge.setServiceEnabled(enabled)
    suspend fun setLeftEdgeEnabled(enabled: Boolean) = edge.setLeftEdgeEnabled(enabled)
    suspend fun setRightEdgeEnabled(enabled: Boolean) = edge.setRightEdgeEnabled(enabled)
    suspend fun setEdgeTriggerWidthDp(side: PanelSide, value: Float) = edge.setEdgeTriggerWidthDp(side, value)
    suspend fun setTriggerEdgeWidthDp(side: PanelSide, handleId: String, value: Float) =
        edge.setTriggerEdgeWidthDp(side, handleId, value)
    suspend fun setTriggerTopFraction(side: PanelSide, value: Float) = edge.setTriggerTopFraction(side, value)
    suspend fun setTriggerHeightFraction(side: PanelSide, value: Float) = edge.setTriggerHeightFraction(side, value)
    suspend fun setTriggerVerticalRange(side: PanelSide, handleId: String, topFraction: Float, bottomFraction: Float) =
        edge.setTriggerVerticalRange(side, handleId, topFraction, bottomFraction)
    suspend fun addTriggerHandlePair() = edge.addTriggerHandlePair()
    suspend fun removeTriggerHandle(side: PanelSide, handleId: String) = edge.removeTriggerHandle(side, handleId)
    suspend fun setTriggerAlignOppositeSide(handleId: String, sourceSide: PanelSide, enabled: Boolean) =
        edge.setTriggerAlignOppositeSide(handleId, sourceSide, enabled)
    suspend fun setTriggerAlignOppositeDesign(handleId: String, sourceSide: PanelSide, enabled: Boolean) =
        edge.setTriggerAlignOppositeDesign(handleId, sourceSide, enabled)
    suspend fun setTriggerHandleDesign(side: PanelSide, handleId: String, design: TriggerHandleDesign) =
        edge.setTriggerHandleDesign(side, handleId, design)
    suspend fun applyTriggerDesignPreset(side: PanelSide, handleId: String, preset: TriggerDesignPreset) =
        edge.applyTriggerDesignPreset(side, handleId, preset)
    suspend fun setInterceptSystemBackGesture(enabled: Boolean) = edge.setInterceptSystemBackGesture(enabled)
    suspend fun setLimitMaxInterceptLength(enabled: Boolean) = edge.setLimitMaxInterceptLength(enabled)
    suspend fun setDefaultTriggerMode(side: PanelSide, mode: GestureTriggerMode) = edge.setDefaultTriggerMode(side, mode)
    suspend fun setShortSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) =
        edge.setShortSwipeDistanceDp(side, handleId, value)
    suspend fun setLongSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) =
        edge.setLongSwipeDistanceDp(side, handleId, value)
    suspend fun setGestureHintEnabled(enabled: Boolean) = edge.setGestureHintEnabled(enabled)
    suspend fun setGestureHintStyle(style: GestureHintStyle) = edge.setGestureHintStyle(style)
    suspend fun setAnimationStyles(styles: AnimationStyles) = edge.setAnimationStyles(styles)
    suspend fun updateWaveStyle(style: WaveStyle) = edge.updateWaveStyle(style)
    suspend fun updateCapsuleStyle(style: CapsuleStyle) = edge.updateCapsuleStyle(style)
    suspend fun updateBubbleStyle(style: BubbleStyle) = edge.updateBubbleStyle(style)
    suspend fun setGestureAngleConfig(config: GestureAngleConfig) = edge.setGestureAngleConfig(config)
    suspend fun setIndexHeightFraction(value: Float) = edge.setIndexHeightFraction(value)
    suspend fun setAppsPerRow(value: Int) = edge.setAppsPerRow(value)
    suspend fun setQuickLauncherColumnsPerPage(value: Int) = edge.setQuickLauncherColumnsPerPage(value)
    suspend fun setQuickLauncherRowsPerPage(value: Int) = edge.setQuickLauncherRowsPerPage(value)
    suspend fun setPanelOpacity(value: Float) = edge.setPanelOpacity(value)
    suspend fun setHapticEnabled(enabled: Boolean) = edge.setHapticEnabled(enabled)
    suspend fun setHideFromRecents(enabled: Boolean) = edge.setHideFromRecents(enabled)
    suspend fun setAccessibilityKeepAliveEnabled(enabled: Boolean) = edge.setAccessibilityKeepAliveEnabled(enabled)
    suspend fun setHapticStrengthLevel(level: Int) = edge.setHapticStrengthLevel(level)
    suspend fun addHiddenApp(packageName: String) = edge.addHiddenApp(packageName)
    suspend fun removeHiddenApp(packageName: String) = edge.removeHiddenApp(packageName)
    suspend fun addExcludedTriggerApp(packageName: String) = edge.addExcludedTriggerApp(packageName)
    suspend fun removeExcludedTriggerApp(packageName: String) = edge.removeExcludedTriggerApp(packageName)
    suspend fun setHideTriggerInLandscape(enabled: Boolean) = edge.setHideTriggerInLandscape(enabled)
    suspend fun setHideTriggerOnLockScreen(enabled: Boolean) = edge.setHideTriggerOnLockScreen(enabled)
    suspend fun setHideTriggerOnLauncher(enabled: Boolean) = edge.setHideTriggerOnLauncher(enabled)
    suspend fun upsertGestureRule(rule: GestureRule) = edge.upsertGestureRule(rule)
    suspend fun removeGestureRule(id: String) = edge.removeGestureRule(id)
    suspend fun setSlotAction(side: PanelSide, trigger: GestureTriggerType, action: GestureAction) =
        edge.setSlotAction(side, trigger, action)
    suspend fun setSlotTriggerMode(side: PanelSide, trigger: GestureTriggerType, triggerMode: GestureTriggerMode) =
        edge.setSlotTriggerMode(side, trigger, triggerMode)
    suspend fun setSlotConfig(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
        triggerMode: GestureTriggerMode,
        handleId: String = TriggerHandle.DEFAULT_ID,
    ) = edge.setSlotConfig(side, trigger, action, triggerMode, handleId)

    suspend fun setThemeColor(argb: Int) = overlay.setThemeColor(argb)
    suspend fun setDynamicColorEnabled(enabled: Boolean) = overlay.setDynamicColorEnabled(enabled)
    suspend fun setFreeWindowEnabled(enabled: Boolean) = overlay.setFreeWindowEnabled(enabled)
    suspend fun setFreeWindowModeId(id: Int) = overlay.setFreeWindowModeId(id)
    suspend fun setFreeWindowLayout(widthFraction: Float, heightFraction: Float, leftFraction: Float, topFraction: Float) =
        overlay.setFreeWindowLayout(widthFraction, heightFraction, leftFraction, topFraction)
    suspend fun setAppLaunchPolicyId(id: Int) = overlay.setAppLaunchPolicyId(id)
    suspend fun setLongPressLaunchDurationMs(value: Int) = overlay.setLongPressLaunchDurationMs(value)
    suspend fun setFloatingPointerSensitivityFraction(value: Float) =
        overlay.setFloatingPointerSensitivityFraction(value)
    suspend fun setFloatingPointerJoystickDiameterPx(value: Float) = overlay.setFloatingPointerJoystickDiameterPx(value)
    suspend fun setFloatingPointerPointerDiameterPx(value: Float) = overlay.setFloatingPointerPointerDiameterPx(value)
    suspend fun setFloatingPointerDesignId(designId: String) = overlay.setFloatingPointerDesignId(designId)
    suspend fun setFloatingPointerRingThicknessPx(value: Float) = overlay.setFloatingPointerRingThicknessPx(value)
    suspend fun setFloatingPointerDotDiameterPx(value: Float) = overlay.setFloatingPointerDotDiameterPx(value)
    suspend fun setFloatingPointerRingColor(argb: Int) = overlay.setFloatingPointerRingColor(argb)
    suspend fun setFloatingPointerFillColor(argb: Int) = overlay.setFloatingPointerFillColor(argb)
    suspend fun setFloatingPointerDotColor(argb: Int) = overlay.setFloatingPointerDotColor(argb)
    suspend fun setFloatingPointerClickVisualFeedbackEnabled(enabled: Boolean) =
        overlay.setFloatingPointerClickVisualFeedbackEnabled(enabled)
    suspend fun setFloatingPointerClickHapticEnabled(enabled: Boolean) = overlay.setFloatingPointerClickHapticEnabled(enabled)
    suspend fun setFloatingPointerRippleColor(argb: Int) = overlay.setFloatingPointerRippleColor(argb)
    suspend fun setFloatingPointerRippleSizeDp(value: Float) = overlay.setFloatingPointerRippleSizeDp(value)
    suspend fun setFloatingPointerRippleDurationMs(value: Int) = overlay.setFloatingPointerRippleDurationMs(value)
    suspend fun setFloatingPointerTrailType(type: FloatingPointerTrailType) = overlay.setFloatingPointerTrailType(type)
    suspend fun setFloatingPointerTrailDurationMs(value: Int) = overlay.setFloatingPointerTrailDurationMs(value)
    suspend fun setFloatingPointerTrailColor(argb: Int) = overlay.setFloatingPointerTrailColor(argb)
    suspend fun setFloatingPointerHideWhenJoystickReleased(enabled: Boolean) =
        overlay.setFloatingPointerHideWhenJoystickReleased(enabled)

    suspend fun setFloatingPointerClickDistanceThresholdDp(value: Float) =
        overlay.setFloatingPointerClickDistanceThresholdDp(value)
    suspend fun setFloatingPointerJoystickInnerColor(argb: Int) = overlay.setFloatingPointerJoystickInnerColor(argb)
    suspend fun setFloatingPointerJoystickOuterColor(argb: Int) = overlay.setFloatingPointerJoystickOuterColor(argb)
    suspend fun setFloatingPointerJoystickGradientRadiusFraction(value: Float) =
        overlay.setFloatingPointerJoystickGradientRadiusFraction(value)
    suspend fun setFloatingPointerHideOnOutsideClick(enabled: Boolean) = overlay.setFloatingPointerHideOnOutsideClick(enabled)
    suspend fun setFloatingPointerHideOnQuickSwipe(enabled: Boolean) = overlay.setFloatingPointerHideOnQuickSwipe(enabled)
    suspend fun setFloatingPointerHideWhenIdle(enabled: Boolean) = overlay.setFloatingPointerHideWhenIdle(enabled)
    suspend fun setFloatingPointerIdleHideDelayMs(value: Int) = overlay.setFloatingPointerIdleHideDelayMs(value)
    suspend fun setFloatingPointerJoystickLongPressAction(action: GestureAction) = overlay.setFloatingPointerJoystickLongPressAction(action)
    suspend fun setFloatingPointerRadialAlwaysVisible(enabled: Boolean) = overlay.setFloatingPointerRadialAlwaysVisible(enabled)
    suspend fun setFloatingPointerRadialLongPressMs(value: Int) = overlay.setFloatingPointerRadialLongPressMs(value)
    suspend fun setFloatingPointerRadialOuterDiameterPx(value: Float) = overlay.setFloatingPointerRadialOuterDiameterPx(value)
    suspend fun setFloatingPointerRadialInnerDiameterPx(value: Float) = overlay.setFloatingPointerRadialInnerDiameterPx(value)
    suspend fun setFloatingPointerRadialOuterColor(argb: Int) = overlay.setFloatingPointerRadialOuterColor(argb)
    suspend fun setFloatingPointerRadialInnerColor(argb: Int) = overlay.setFloatingPointerRadialInnerColor(argb)
    suspend fun setFloatingPointerRadialDividerThicknessPx(value: Float) =
        overlay.setFloatingPointerRadialDividerThicknessPx(value)
    suspend fun setFloatingPointerRadialDividerColor(argb: Int) = overlay.setFloatingPointerRadialDividerColor(argb)
    suspend fun setFloatingPointerRadialIconSizeFraction(value: Float) = overlay.setFloatingPointerRadialIconSizeFraction(value)
    suspend fun setFloatingPointerRadialIconColor(argb: Int) = overlay.setFloatingPointerRadialIconColor(argb)
    suspend fun setFloatingPointerRadialSlotAction(index: Int, action: GestureAction) =
        overlay.setFloatingPointerRadialSlotAction(index, action)
    suspend fun resetFloatingPointerRadialDesignDefaults() = overlay.resetFloatingPointerRadialDesignDefaults()
    suspend fun setFloatingPointerEdgeThresholdDp(value: Float) = overlay.setFloatingPointerEdgeThresholdDp(value)
    suspend fun setFloatingPointerEdgePreviewSensitivity(value: Int) =
        overlay.setFloatingPointerEdgePreviewSensitivity(value)
    suspend fun setFloatingPointerEdgePreviewGlowSize(value: Int) = overlay.setFloatingPointerEdgePreviewGlowSize(value)
    suspend fun setFloatingPointerEdgePreviewShowIcon(enabled: Boolean) =
        overlay.setFloatingPointerEdgePreviewShowIcon(enabled)
    suspend fun setFloatingPointerEdgeVisualSizeDp(value: Float) = overlay.setFloatingPointerEdgeVisualSizeDp(value)
    suspend fun setFloatingPointerEdgeVisualOpacity(value: Int) = overlay.setFloatingPointerEdgeVisualOpacity(value)
    suspend fun setFloatingPointerEdgeVisualColor(argb: Int) = overlay.setFloatingPointerEdgeVisualColor(argb)
    suspend fun setFloatingPointerEdgeBarEnabled(side: FloatingPointerEdgeSide, enabled: Boolean) =
        overlay.setFloatingPointerEdgeBarEnabled(side, enabled)
    suspend fun setFloatingPointerEdgeBarSlotAction(
        side: FloatingPointerEdgeSide,
        slotIndex: Int,
        action: GestureAction,
    ) = overlay.setFloatingPointerEdgeBarSlotAction(side, slotIndex, action)
    suspend fun addFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide) =
        overlay.addFloatingPointerEdgeBarSlot(side)
    suspend fun removeFloatingPointerEdgeBarSlot(side: FloatingPointerEdgeSide, slotIndex: Int) =
        overlay.removeFloatingPointerEdgeBarSlot(side, slotIndex)
    suspend fun resetFloatingPointerEdgeDefaults() = overlay.resetFloatingPointerEdgeDefaults()
    suspend fun resetFloatingPointerVisualDefaults() = overlay.resetFloatingPointerVisualDefaults()
    suspend fun resetFloatingPointerJoystickVisualDefaults() = overlay.resetFloatingPointerJoystickVisualDefaults()
    suspend fun resetFloatingPointerJoystickBehaviorDefaults() = overlay.resetFloatingPointerJoystickBehaviorDefaults()
    suspend fun setQuickLauncherItems(items: List<com.slideindex.app.launcher.QuickLauncherItem>) =
        overlay.setQuickLauncherItems(items)
    suspend fun setShellCommands(items: List<ShellCommand>) = overlay.setShellCommands(items)
    suspend fun setWidgetPanelPages(pages: List<com.slideindex.app.widget.WidgetPanelPage>) = overlay.setWidgetPanelPages(pages)
    suspend fun setWidgetPanelBlurEnabled(enabled: Boolean) = overlay.setWidgetPanelBlurEnabled(enabled)
    suspend fun setWidgetPanelWidthFraction(fraction: Float) = overlay.setWidgetPanelWidthFraction(fraction)
    suspend fun setDebugPerformanceMonitorEnabled(enabled: Boolean) = overlay.setDebugPerformanceMonitorEnabled(enabled)

    suspend fun setFloatBallEnabled(enabled: Boolean) = overlay.setFloatBallEnabled(enabled)
    suspend fun setFloatBallSizeDp(value: Float) = overlay.setFloatBallSizeDp(value)
    suspend fun setFloatBallOpacity(value: Float) = overlay.setFloatBallOpacity(value)
    suspend fun setFloatBallPosition(xFraction: Float, yFraction: Float) =
        overlay.setFloatBallPosition(xFraction, yFraction)

    suspend fun setFloatBallPositionYFraction(yFraction: Float) =
        overlay.setFloatBallPositionYFraction(yFraction)

    suspend fun setFloatBallPositionXFraction(xFraction: Float) =
        overlay.setFloatBallPositionXFraction(xFraction)

    suspend fun setFloatBallOcrFallbackEnabled(enabled: Boolean) =
        overlay.setFloatBallOcrFallbackEnabled(enabled)

    suspend fun setFloatBallOcrModelId(modelId: String) =
        overlay.setFloatBallOcrModelId(modelId)

    suspend fun setOcrDownloadWifiOnly(enabled: Boolean) =
        overlay.setOcrDownloadWifiOnly(enabled)

    suspend fun setFloatBallPointerSpeedFraction(value: Float) =
        overlay.setFloatBallPointerSpeedFraction(value)

    suspend fun setFloatBallPositionMode(mode: FloatBallPositionMode) =
        overlay.setFloatBallPositionMode(mode)

    suspend fun setFloatBallActiveSide(side: FloatBallSide) =
        overlay.setFloatBallActiveSide(side)

    suspend fun setFloatBallLineHeightFraction(value: Float) =
        overlay.setFloatBallLineHeightFraction(value)

    suspend fun setFloatBallLineWidthFraction(value: Float) =
        overlay.setFloatBallLineWidthFraction(value)

    suspend fun setFloatBallLineOpacity(value: Float) =
        overlay.setFloatBallLineOpacity(value)

    suspend fun setFloatBallGestureAction(type: FloatBallGestureType, action: GestureAction) =
        overlay.setFloatBallGestureAction(type, action)

    suspend fun setFloatBallStyleType(type: FloatBallStyleType) =
        overlay.setFloatBallStyleType(type)

    suspend fun setFloatBallCustomImageUri(uri: String) =
        overlay.setFloatBallCustomImageUri(uri)

    suspend fun setFloatBallSlideshowUris(uris: List<String>) =
        overlay.setFloatBallSlideshowUris(uris)

    suspend fun setFloatBallGifUri(uri: String) =
        overlay.setFloatBallGifUri(uri)

    suspend fun setFloatBallPickOffsetDp(value: Float) =
        overlay.setFloatBallPickOffsetDp(value)

    suspend fun setFloatBallPickTextSizeSp(value: Float) =
        overlay.setFloatBallPickTextSizeSp(value)

    suspend fun setFloatBallPickBottomTransitionFraction(value: Float) =
        overlay.setFloatBallPickBottomTransitionFraction(value)

    suspend fun setFloatBallPointerSlopDp(value: Float) =
        overlay.setFloatBallPointerSlopDp(value)

    suspend fun setFloatBallDownSwipeShortPercent(value: Float) =
        overlay.setFloatBallDownSwipeShortPercent(value)

    suspend fun setFloatBallSideSwipeShortPercent(value: Float) =
        overlay.setFloatBallSideSwipeShortPercent(value)

    suspend fun setFloatBallUpSwipeShortPercent(value: Float) =
        overlay.setFloatBallUpSwipeShortPercent(value)

    suspend fun setFloatBallInstantTranslate(enabled: Boolean) =
        overlay.setFloatBallInstantTranslate(enabled)

    suspend fun setFloatBallTranslateEngine(engine: FloatBallTranslateEngine) =
        overlay.setFloatBallTranslateEngine(engine)

    suspend fun setFloatBallTranslateTargetLang(languageCode: String) =
        overlay.setFloatBallTranslateTargetLang(languageCode)

    suspend fun setFloatBallTranslatePickPanelTransparency(value: Float) =
        overlay.setFloatBallTranslatePickPanelTransparency(value)

    suspend fun setShareImageOcrHistoryEnabled(enabled: Boolean) =
        overlay.setShareImageOcrHistoryEnabled(enabled)

    suspend fun setSearchEngines(engines: List<SearchEngineConfig>) =
        overlay.setSearchEngines(engines)

    suspend fun setSearchEngineGridColumns(value: Int) =
        overlay.setSearchEngineGridColumns(value)

    suspend fun setSearchEngineGridRows(value: Int) =
        overlay.setSearchEngineGridRows(value)

    suspend fun setSearchEngineShowLabels(enabled: Boolean) =
        overlay.setSearchEngineShowLabels(enabled)

    suspend fun setAggregatedImageSearchEngines(configs: List<AggregatedImageSearchEngineConfig>) =
        overlay.setAggregatedImageSearchEngines(configs)

    suspend fun setOtpCopyToClipboard(enabled: Boolean) = otp.setOtpCopyToClipboard(enabled)
    suspend fun setOtpKeywordsRegex(value: String) = otp.setOtpKeywordsRegex(value)
    suspend fun setOtpUserMatchRules(rules: List<com.slideindex.app.otp.OtpMatchRule>) = otp.setOtpUserMatchRules(rules)
    suspend fun setOtpDisabledOfficialRuleIds(ids: Set<String>) = otp.setOtpDisabledOfficialRuleIds(ids)
    suspend fun setOtpOfficialRuleEnabled(ruleId: String, enabled: Boolean) = otp.setOtpOfficialRuleEnabled(ruleId, enabled)
    suspend fun setOtpAutoInputEnabled(enabled: Boolean) = otp.setOtpAutoInputEnabled(enabled)
    suspend fun setOtpAutoConfirmEnabled(enabled: Boolean) = otp.setOtpAutoConfirmEnabled(enabled)
    suspend fun setOtpAutoInputDelayMs(value: Int) = otp.setOtpAutoInputDelayMs(value)
    suspend fun setOtpAutoInputIntervalMs(value: Int) = otp.setOtpAutoInputIntervalMs(value)
    suspend fun setOtpLsposedSmsCaptureEnabled(enabled: Boolean) = otp.setOtpLsposedSmsCaptureEnabled(enabled)
    suspend fun setOtpLsposedSystemInjectEnabled(enabled: Boolean) = otp.setOtpLsposedSystemInjectEnabled(enabled)

    suspend fun setShakeGesturesEnabled(enabled: Boolean) = shake.setShakeGesturesEnabled(enabled)
    suspend fun setShakeGestureAction(type: ShakeGestureType, action: GestureAction) = shake.setShakeGestureAction(type, action)
    suspend fun setLockScreenShakeAction(type: ShakeGestureType, action: GestureAction) = shake.setLockScreenShakeAction(type, action)
    suspend fun setPerAppShakeAction(packageName: String, type: ShakeGestureType, action: GestureAction) =
        shake.setPerAppShakeAction(packageName, type, action)
    suspend fun addPerAppShakeConfig(packageName: String) = shake.addPerAppShakeConfig(packageName)
    suspend fun removePerAppShakeConfig(packageName: String) = shake.removePerAppShakeConfig(packageName)
    suspend fun setShakeDirectionSensitivity(type: ShakeGestureType, value: Float) =
        shake.setShakeDirectionSensitivity(type, value)
    suspend fun setLockScreenShakeEnabled(enabled: Boolean) = shake.setLockScreenShakeEnabled(enabled)
    suspend fun setIndependentAppShakeEnabled(enabled: Boolean) = shake.setIndependentAppShakeEnabled(enabled)
    suspend fun setShakeGlobalSensitivity(value: Float) = shake.setShakeGlobalSensitivity(value)
    suspend fun setShakeIndependentSensitivityEnabled(enabled: Boolean) = shake.setShakeIndependentSensitivityEnabled(enabled)
    suspend fun setShakeVibrationFeedbackEnabled(enabled: Boolean) = shake.setShakeVibrationFeedbackEnabled(enabled)
    suspend fun setShakeAnimationFeedbackEnabled(enabled: Boolean) = shake.setShakeAnimationFeedbackEnabled(enabled)
    suspend fun setShakeAnimationColor(argb: Int) = shake.setShakeAnimationColor(argb)
    suspend fun setShakeDisableInLandscape(enabled: Boolean) = shake.setShakeDisableInLandscape(enabled)
    suspend fun addShakeBlacklistedApp(packageName: String) = shake.addShakeBlacklistedApp(packageName)
    suspend fun removeShakeBlacklistedApp(packageName: String) = shake.removeShakeBlacklistedApp(packageName)

    suspend fun setMessageReminderEnabled(enabled: Boolean) = message.setMessageReminderEnabled(enabled)
    suspend fun setMessageStyleId(styleId: String) = message.setMessageStyleId(styleId)
    suspend fun setMessagePrimaryStyleEnabled(enabled: Boolean) = message.setMessagePrimaryStyleEnabled(enabled)
    suspend fun setMessageDanmakuEnabled(enabled: Boolean) = message.setMessageDanmakuEnabled(enabled)
    suspend fun setMessageThemeId(themeId: String) = message.setMessageThemeId(themeId)
    suspend fun setMessageDanmakuThemeId(themeId: String) = message.setMessageDanmakuThemeId(themeId)
    suspend fun setMessageFloatIconOpacity(opacity: Float) = message.setMessageFloatIconOpacity(opacity)
    suspend fun setMessageCardOpacity(opacity: Float) = message.setMessageCardOpacity(opacity)
    suspend fun setMessageSideBubbleOpacity(opacity: Float) = message.setMessageSideBubbleOpacity(opacity)
    suspend fun setMessageFloatIconSizeDp(sizeDp: Float) = message.setMessageFloatIconSizeDp(sizeDp)
    suspend fun setMessageDanmakuOpacity(opacity: Float) = message.setMessageDanmakuOpacity(opacity)
    suspend fun setMessageCardMaxLines(lines: Int) = message.setMessageCardMaxLines(lines)
    suspend fun setMessageDanmakuMaxLines(lines: Int) = message.setMessageDanmakuMaxLines(lines)
    suspend fun setMessageSideMaxCount(count: Int) = message.setMessageSideMaxCount(count)
    suspend fun setMessageSideMaxWidthDp(widthDp: Float) = message.setMessageSideMaxWidthDp(widthDp)
    suspend fun setMessageSideMaxLines(lines: Int) = message.setMessageSideMaxLines(lines)
    suspend fun setMessageAutoDismissSeconds(seconds: Int) = message.setMessageAutoDismissSeconds(seconds)
    suspend fun setMessageHideInLandscape(enabled: Boolean) = message.setMessageHideInLandscape(enabled)
    suspend fun setMessagePortraitDanmaku(enabled: Boolean) = message.setMessagePortraitDanmaku(enabled)
    suspend fun setMessageLandscapeDanmaku(enabled: Boolean) = message.setMessageLandscapeDanmaku(enabled)
    suspend fun setMessageGestureAction(slot: String, action: MessageAction) = message.setMessageGestureAction(slot, action)
    suspend fun addMessageEnabledPackage(packageName: String) = message.addMessageEnabledPackage(packageName)
    suspend fun removeMessageEnabledPackage(packageName: String) = message.removeMessageEnabledPackage(packageName)
    suspend fun addMessageDisabledPackage(packageName: String) = message.addMessageDisabledPackage(packageName)
    suspend fun removeMessageDisabledPackage(packageName: String) = message.removeMessageDisabledPackage(packageName)
    suspend fun addMessageDndPackage(packageName: String) = message.addMessageDndPackage(packageName)
    suspend fun removeMessageDndPackage(packageName: String) = message.removeMessageDndPackage(packageName)
    suspend fun setMessageSuppressWhenSystemDnd(enabled: Boolean) = message.setMessageSuppressWhenSystemDnd(enabled)
    suspend fun upsertMessageAppFilterRule(rule: MessageAppFilterRule) = message.upsertMessageAppFilterRule(rule)
    suspend fun removeMessageAppFilterRule(packageName: String) = message.removeMessageAppFilterRule(packageName)
}
