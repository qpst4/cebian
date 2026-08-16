package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.gesture.GestureRuleCodec
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleCodec
import com.slideindex.app.overlay.PanelSide

internal object SettingsTriggerStore {
    fun readTriggerSettings(prefs: Preferences): AppSettings {
        val legacyTop = prefs[SettingsPreferenceKeys.TRIGGER_TOP] ?: 0.30f
        val legacyHeight = prefs[SettingsPreferenceKeys.TRIGGER_HEIGHT] ?: 0.38f
        val legacyWidth = prefs[SettingsPreferenceKeys.EDGE_TRIGGER_WIDTH] ?: 20f
        val legacyShortSwipe = prefs[SettingsPreferenceKeys.SHORT_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_SHORT_SWIPE_DISTANCE_DP
        val legacyLongSwipe = prefs[SettingsPreferenceKeys.LONG_SWIPE_DISTANCE_DP] ?: TriggerHandle.DEFAULT_LONG_SWIPE_DISTANCE_DP
        val leftWidth = prefs[SettingsPreferenceKeys.LEFT_EDGE_TRIGGER_WIDTH] ?: legacyWidth
        val rightWidth = prefs[SettingsPreferenceKeys.RIGHT_EDGE_TRIGGER_WIDTH] ?: legacyWidth
        return AppSettings(
            edgeTrigger = EdgeTriggerSettings(
                leftEdgeTriggerWidthDp = leftWidth,
                rightEdgeTriggerWidthDp = rightWidth,
                leftTriggerHandles = prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES]?.let {
                    TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
                } ?: listOf(
                    TriggerHandle.default(
                        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_TOP] ?: legacyTop,
                        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HEIGHT] ?: legacyHeight,
                    ).copy(edgeWidthDp = leftWidth),
                ),
                rightTriggerHandles = prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES]?.let {
                    TriggerHandleCodec.decodeAll(it, legacyShortSwipe, legacyLongSwipe)
                } ?: listOf(
                    TriggerHandle.default(
                        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_TOP] ?: legacyTop,
                        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HEIGHT] ?: legacyHeight,
                    ).copy(edgeWidthDp = rightWidth),
                ),
                bottomTriggerHandles = prefs[SettingsPreferenceKeys.BOTTOM_TRIGGER_HANDLES]?.let { raw ->
                    if (raw.isEmpty()) {
                        emptyList()
                    } else {
                        TriggerHandleCodec.decodeAll(raw, legacyShortSwipe, legacyLongSwipe)
                    }
                } ?: listOf(TriggerHandle.bottomDefault()),
                topTriggerHandles = prefs[SettingsPreferenceKeys.TOP_TRIGGER_HANDLES]?.let { raw ->
                    if (raw.isEmpty()) {
                        emptyList()
                    } else {
                        TriggerHandleCodec.decodeAll(raw, legacyShortSwipe, legacyLongSwipe)
                    }
                } ?: listOf(TriggerHandle.topDefault()),
            ),
            launcher = LauncherSettings(
                gestureRules = GestureRuleCodec.decodeAll(
                    prefs[SettingsPreferenceKeys.GESTURE_RULES] ?: emptySet(),
                ),
            ),
        ).withResolvedHandleEdgeWidths()
    }

    fun writeTriggerHandles(prefs: MutablePreferences, settings: AppSettings) {
        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES] = TriggerHandleCodec.encodeAll(settings.leftTriggerHandles)
        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES] = TriggerHandleCodec.encodeAll(settings.rightTriggerHandles)
        prefs[SettingsPreferenceKeys.BOTTOM_TRIGGER_HANDLES] = TriggerHandleCodec.encodeAll(settings.bottomTriggerHandles)
        prefs[SettingsPreferenceKeys.TOP_TRIGGER_HANDLES] = TriggerHandleCodec.encodeAll(settings.topTriggerHandles)
    }

    fun writeLandscapeTriggerHandles(prefs: MutablePreferences, settings: AppSettings) {
        prefs[SettingsPreferenceKeys.LEFT_TRIGGER_HANDLES_LANDSCAPE] =
            TriggerHandleCodec.encodeAll(settings.leftTriggerHandlesLandscape)
        prefs[SettingsPreferenceKeys.RIGHT_TRIGGER_HANDLES_LANDSCAPE] =
            TriggerHandleCodec.encodeAll(settings.rightTriggerHandlesLandscape)
        prefs[SettingsPreferenceKeys.BOTTOM_TRIGGER_HANDLES_LANDSCAPE] =
            TriggerHandleCodec.encodeAll(settings.bottomTriggerHandlesLandscape)
        prefs[SettingsPreferenceKeys.TOP_TRIGGER_HANDLES_LANDSCAPE] =
            TriggerHandleCodec.encodeAll(settings.topTriggerHandlesLandscape)
    }

    fun writeLandscapeGestureSettings(prefs: MutablePreferences, settings: AppSettings) {
        prefs[SettingsPreferenceKeys.LANDSCAPE_TRIGGERS_INITIALIZED] = settings.landscapeTriggersInitialized
        prefs[SettingsPreferenceKeys.GESTURE_RULES_LANDSCAPE] =
            GestureRuleCodec.encodeAll(settings.gestureRulesLandscape)
        prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE_LANDSCAPE] =
            settings.leftDefaultTriggerModeLandscape.id
        prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE_LANDSCAPE] =
            settings.rightDefaultTriggerModeLandscape.id
        prefs[SettingsPreferenceKeys.BOTTOM_DEFAULT_TRIGGER_MODE_LANDSCAPE] =
            settings.bottomDefaultTriggerModeLandscape.id
        prefs[SettingsPreferenceKeys.TOP_DEFAULT_TRIGGER_MODE_LANDSCAPE] =
            settings.topDefaultTriggerModeLandscape.id
    }

    fun writeLandscapeSettings(prefs: MutablePreferences, settings: AppSettings) {
        writeLandscapeTriggerHandles(prefs, settings)
        writeLandscapeGestureSettings(prefs, settings)
    }

    fun mergeLandscapeFromEditing(base: AppSettings, edited: AppSettings): AppSettings =
        base.mergeLandscapeEdits(edited)

    fun updateTriggerSwipeDistances(
        prefs: MutablePreferences,
        side: PanelSide,
        handleId: String,
        shortSwipeDistanceDp: Float? = null,
        longSwipeDistanceDp: Float? = null,
        landscape: Boolean = false,
    ) {
        val snapshot = SettingsSnapshotReader.read(prefs)
        val current = if (landscape) snapshot.forLandscapeHandleEditing() else snapshot
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
        if (landscape) {
            writeLandscapeTriggerHandles(prefs, mergeLandscapeFromEditing(snapshot, updated))
        } else {
            writeTriggerHandles(prefs, updated)
        }
    }
}
