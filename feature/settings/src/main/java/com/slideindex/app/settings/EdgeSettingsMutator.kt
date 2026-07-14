package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureAngleConfig
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureRuleCodec
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerRectanglePresetLogic
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.coerceInLimits
import com.slideindex.app.overlay.PanelSide
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeSettingsMutator @Inject constructor(
    private val editor: SettingsPreferencesEditor,
) {
    suspend fun setServiceEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.SERVICE_ENABLED] = enabled }
    suspend fun setOnboardingCompleted(completed: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.ONBOARDING_COMPLETED] = completed }
    suspend fun setLeftEdgeEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.LEFT_EDGE_ENABLED] = enabled }
    suspend fun setRightEdgeEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.RIGHT_EDGE_ENABLED] = enabled }

    suspend fun setEdgeTriggerWidthDp(side: PanelSide, value: Float) = editor.edit { prefs ->
        val width = value.coerceIn(12f, 36f)
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_EDGE_TRIGGER_WIDTH] = width
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_EDGE_TRIGGER_WIDTH] = width
        }
    }

    suspend fun setTriggerEdgeWidthDp(side: PanelSide, handleId: String, value: Float) = editor.edit { prefs ->
        val current = SettingsTriggerStore.readTriggerSettings(prefs)
        if (current.triggerHandle(side, handleId) == null) return@edit
        val updated = current.withUpdatedTriggerHandleEdgeWidth(side, handleId, value)
        SettingsTriggerStore.writeTriggerHandles(prefs, updated)
    }

    suspend fun setTriggerTopFraction(side: PanelSide, value: Float) = editor.edit { prefs ->
        val top = value.coerceIn(0.05f, 0.80f)
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = top
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = top
        }
    }

    suspend fun setTriggerHeightFraction(side: PanelSide, value: Float) = editor.edit { prefs ->
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
    ) = editor.edit { prefs ->
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
        val current = SettingsTriggerStore.readTriggerSettings(prefs)
        val sourceHandle = current.triggerHandle(side, handleId)
        var updated = current.withUpdatedTriggerHandle(side, handleId, top, height)
        if (sourceHandle?.alignOppositeSide != false) {
            val otherSide = side.opposite()
            if (updated.triggerHandle(otherSide, handleId) != null) {
                updated = updated.withUpdatedTriggerHandle(otherSide, handleId, top, height)
            }
        }
        SettingsTriggerStore.writeTriggerHandles(prefs, updated)
        val primaryLeft = updated.leftTriggerHandles.first()
        val primaryRight = updated.rightTriggerHandles.first()
        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] = primaryLeft.topFraction
        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] = primaryRight.topFraction
        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] = primaryLeft.heightFraction
        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] = primaryRight.heightFraction
    }

    suspend fun addTriggerHandlePair() = editor.edit { prefs ->
        val current = SettingsTriggerStore.readTriggerSettings(prefs)
        val updated = current.withAddedTriggerHandlePair()
        SettingsTriggerStore.writeTriggerHandles(prefs, updated)
    }

    suspend fun removeTriggerHandle(side: PanelSide, handleId: String) = editor.edit { prefs ->
        val current = SettingsTriggerStore.readTriggerSettings(prefs)
        val updated = current.withRemovedTriggerHandle(side, handleId)
        SettingsTriggerStore.writeTriggerHandles(prefs, updated)
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
    ) = editor.edit { prefs ->
        var current = SettingsTriggerStore.readTriggerSettings(prefs).withTriggerAlignOppositeSide(handleId, enabled)
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
                    current = current.withSyncedTriggerHandle(
                        sourceSide = sourceSide,
                        handleId = handleId,
                        handle = source,
                    )
                }
            }
        }
        SettingsTriggerStore.writeTriggerHandles(prefs, current)
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

    suspend fun setTriggerAlignOppositeDesign(
        handleId: String,
        sourceSide: PanelSide,
        enabled: Boolean,
    ) = editor.edit { prefs ->
        var current = SettingsTriggerStore.readTriggerSettings(prefs)
            .withTriggerAlignOppositeDesign(handleId, enabled)
        if (enabled) {
            val source = current.triggerHandle(sourceSide, handleId) ?: return@edit
            current = current.withSyncedTriggerHandleDesignState(
                sourceSide = sourceSide,
                handleId = handleId,
                sourceHandle = source,
            )
        }
        SettingsTriggerStore.writeTriggerHandles(prefs, current)
    }

    suspend fun setTriggerHandleDesign(
        side: PanelSide,
        handleId: String,
        design: TriggerHandleDesign,
    ) = editor.edit { prefs ->
        val current = SettingsTriggerStore.readTriggerSettings(prefs)
        val sourceHandle = current.triggerHandle(side, handleId) ?: return@edit
        val updatedHandle = TriggerRectanglePresetLogic.updateDesign(sourceHandle, design)
        val updated = current.withSyncedTriggerHandleDesignState(
            sourceSide = side,
            handleId = handleId,
            sourceHandle = updatedHandle,
        )
        SettingsTriggerStore.writeTriggerHandles(prefs, updated)
    }

    suspend fun applyTriggerDesignPreset(
        side: PanelSide,
        handleId: String,
        preset: TriggerDesignPreset,
    ) = editor.edit { prefs ->
        val current = SettingsTriggerStore.readTriggerSettings(prefs)
        val sourceHandle = current.triggerHandle(side, handleId) ?: return@edit
        val updatedHandle = TriggerRectanglePresetLogic.switchPreset(sourceHandle, preset)
        val updated = current.withSyncedTriggerHandleDesignState(
            sourceSide = side,
            handleId = handleId,
            sourceHandle = updatedHandle,
        )
        SettingsTriggerStore.writeTriggerHandles(prefs, updated)
    }

    suspend fun setInterceptSystemBackGesture(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.INTERCEPT_SYSTEM_BACK] = enabled }
    suspend fun setLimitMaxInterceptLength(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.LIMIT_MAX_INTERCEPT_LENGTH] = enabled }

    suspend fun setDefaultTriggerMode(side: PanelSide, mode: GestureTriggerMode) = editor.edit { prefs ->
        val resolved = if (mode == GestureTriggerMode.DEFAULT) GestureTriggerMode.ON_RELEASE else mode
        when (side) {
            PanelSide.LEFT -> prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE] = resolved.id
            PanelSide.RIGHT -> prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE] = resolved.id
        }
    }

    suspend fun setShortSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) = editor.edit { prefs ->
        SettingsTriggerStore.updateTriggerSwipeDistances(prefs, side, handleId, shortSwipeDistanceDp = value)
    }

    suspend fun setLongSwipeDistanceDp(side: PanelSide, handleId: String, value: Float) = editor.edit { prefs ->
        SettingsTriggerStore.updateTriggerSwipeDistances(prefs, side, handleId, longSwipeDistanceDp = value)
    }

    suspend fun setGestureHintEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.GESTURE_HINT_ENABLED] = enabled }

    suspend fun setGestureHintStyle(style: GestureHintStyle) = editor.edit {
        it[SettingsPreferenceKeys.GESTURE_HINT_STYLE] = style.id
        style.toAnimationType()?.let { type ->
            val current = AnimationStyleCodec.decode(it[SettingsPreferenceKeys.ANIMATION_STYLES])
            it[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(current.selectType(type))
        }
    }

    suspend fun setAnimationStyles(styles: AnimationStyles) = editor.edit {
        it[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(styles)
    }

    suspend fun updateWaveStyle(style: WaveStyle) = editor.edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_WAVE, AnimationStyleCodec.encodeWave(style)),
        )
    }

    suspend fun updateCapsuleStyle(style: CapsuleStyle) = editor.edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_CAPSULE, AnimationStyleCodec.encodeCapsule(style)),
        )
    }

    suspend fun updateBubbleStyle(style: BubbleStyle) = editor.edit { prefs ->
        val current = AnimationStyleCodec.decode(prefs[SettingsPreferenceKeys.ANIMATION_STYLES])
        prefs[SettingsPreferenceKeys.ANIMATION_STYLES] = AnimationStyleCodec.encode(
            current.updateStyle(AnimationStyles.TYPE_BUBBLE, AnimationStyleCodec.encodeBubble(style)),
        )
    }

    suspend fun setGestureAngleConfig(config: GestureAngleConfig) = editor.edit { prefs ->
        val normalized = config.normalized()
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_UP] = normalized.upDegrees
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_UP_RIGHT] = normalized.upRightDegrees
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_IN] = normalized.inDegrees
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_DOWN_RIGHT] = normalized.downRightDegrees
        prefs[SettingsPreferenceKeys.GESTURE_ANGLE_DOWN] = normalized.downDegrees
    }

    suspend fun setIndexHeightFraction(value: Float) = editor.edit { it[SettingsPreferenceKeys.INDEX_HEIGHT] = value }
    suspend fun setAppsPerRow(value: Int) = editor.edit { it[SettingsPreferenceKeys.APPS_PER_ROW] = value.coerceIn(2, 5) }

    suspend fun setQuickLauncherColumnsPerPage(value: Int) =
        editor.edit { it[SettingsPreferenceKeys.QUICK_LAUNCHER_COLUMNS_PER_PAGE] = value.coerceIn(2, 5) }

    suspend fun setQuickLauncherRowsPerPage(value: Int) =
        editor.edit { it[SettingsPreferenceKeys.QUICK_LAUNCHER_ROWS_PER_PAGE] = value.coerceIn(2, 6) }

    suspend fun setPanelOpacity(value: Float) = editor.edit { it[SettingsPreferenceKeys.PANEL_OPACITY] = value }
    suspend fun setHapticEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.HAPTIC_ENABLED] = enabled }
    suspend fun setHideFromRecents(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.HIDE_FROM_RECENTS] = enabled }

    suspend fun setAccessibilityKeepAliveEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.ACCESSIBILITY_KEEP_ALIVE] = enabled }

    suspend fun setHapticStrengthLevel(level: Int) = editor.edit {
        it[SettingsPreferenceKeys.HAPTIC_STRENGTH] = level.coerceIn(
            HapticStrength.LIGHT.level,
            HapticStrength.STRONG.level,
        )
    }

    suspend fun addHiddenApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES] = current
    }

    suspend fun removeHiddenApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.HIDDEN_APP_PACKAGES] = current
    }

    suspend fun addExcludedTriggerApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] = current
    }

    suspend fun removeExcludedTriggerApp(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.EXCLUDED_TRIGGER_APP_PACKAGES] = current
    }

    suspend fun setHideTriggerInLandscape(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LANDSCAPE] = enabled }

    suspend fun setHideTriggerOnLockScreen(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LOCK_SCREEN] = enabled }

    suspend fun setHideTriggerOnLauncher(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.HIDE_TRIGGER_LAUNCHER] = enabled }

    suspend fun upsertGestureRule(rule: GestureRule) = editor.edit { prefs ->
        val current = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet())
            .filterNot { it.id == rule.id }
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(current + rule)
    }

    suspend fun removeGestureRule(id: String) = editor.edit { prefs ->
        val current = GestureRuleCodec.decodeAll(prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet())
            .filterNot { it.id == id }
        prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(current)
    }

    suspend fun setSlotAction(
        side: PanelSide,
        trigger: GestureTriggerType,
        action: GestureAction,
    ) = editor.edit { prefs ->
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
    ) = editor.edit { prefs ->
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
    ) = editor.edit { prefs ->
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
}
