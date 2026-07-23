package com.slideindex.app.settings

import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageAppFilterCodec
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.message.MessageSettingsCodec
import com.slideindex.app.message.SideBubbleFontSize
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageSettingsMutator @Inject constructor(
    private val editor: SettingsPreferencesEditor,
) {
    suspend fun setMessageReminderEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_REMINDER_ENABLED] = enabled }

    suspend fun setMessageInterceptNotifications(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_INTERCEPT_NOTIFICATIONS] = enabled }

    suspend fun setMessageFloatIconEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_ENABLED] = enabled }

    suspend fun setMessageSideBubbleEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_ENABLED] = enabled }

    suspend fun setMessageSideThemeId(themeId: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_THEME_ID] = themeId }

    suspend fun setMessageSideHorizontalEdge(edge: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_HORIZONTAL_EDGE] = edge }

    suspend fun setMessageSideVerticalAnchor(anchor: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_VERTICAL_ANCHOR] = anchor }

    suspend fun setMessageSideFontSizeLevel(level: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_SIDE_FONT_SIZE_LEVEL] =
                SideBubbleFontSize.coerce(level)
        }

    suspend fun setMessageDanmakuSpeedLevel(level: Int) =
        editor.edit {
            it[SettingsPreferenceKeys.MESSAGE_DANMAKU_SPEED_LEVEL] = level.coerceIn(0, 2)
        }

    suspend fun setMessageStyleId(styleId: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_STYLE_ID] = styleId }

    suspend fun setMessagePrimaryStyleEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_PRIMARY_STYLE_ENABLED] = enabled }

    suspend fun setMessageDanmakuEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_ENABLED] = enabled }

    suspend fun setMessageThemeId(themeId: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_THEME_ID] = themeId }

    suspend fun setMessageDanmakuThemeId(themeId: String) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_THEME_ID] = themeId }

    suspend fun setMessageFloatIconOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_OPACITY] = opacity.coerceIn(0f, 1f) }

    suspend fun setMessageSideBubbleOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_BUBBLE_OPACITY] = opacity.coerceIn(0.1f, 1f) }

    suspend fun setMessageFloatIconSizeDp(sizeDp: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_FLOAT_ICON_SIZE_DP] = sizeDp.coerceIn(32f, 64f) }

    suspend fun setMessageDanmakuOpacity(opacity: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_OPACITY] = opacity.coerceIn(0.2f, 1f) }

    suspend fun setMessageDanmakuMaxLines(lines: Int) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_DANMAKU_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageSideMaxCount(count: Int) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_COUNT] = count.coerceIn(1, 9) }

    suspend fun setMessageSideMaxWidthDp(widthDp: Float) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_WIDTH_DP] = widthDp.coerceIn(120f, 320f) }

    suspend fun setMessageSideMaxLines(lines: Int) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SIDE_MAX_LINES] = lines.coerceIn(1, 3) }

    suspend fun setMessageAutoDismissSeconds(seconds: Int) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_AUTO_DISMISS_SECONDS] = seconds.coerceIn(0, 60) }

    suspend fun setMessageHideInLandscape(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_HIDE_IN_LANDSCAPE] = enabled }

    suspend fun setMessagePortraitDanmaku(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_PORTRAIT_DANMAKU] = enabled }

    suspend fun setMessageLandscapeDanmaku(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_LANDSCAPE_DANMAKU] = enabled }

    suspend fun setMessageGestureAction(slot: String, action: MessageAction) = editor.edit { prefs ->
        val current = MessageSettingsCodec.decodeGestureActions(
            prefs[SettingsPreferenceKeys.MESSAGE_GESTURE_ACTIONS] ?: emptySet(),
        ).toMutableMap()
        current[slot] = action
        val encoded = current.map { (key, value) ->
            MessageSettingsCodec.encodeGestureAction(key, value)
        }.toSet()
        prefs[SettingsPreferenceKeys.MESSAGE_GESTURE_ACTIONS] = encoded
    }

    suspend fun addMessageEnabledPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES] = current
    }

    suspend fun removeMessageEnabledPackage(packageName: String) = editor.edit { prefs ->
        val current = prefs[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_ENABLED_PACKAGES] = current
        val rules = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        rules.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(rules.values)
    }

    suspend fun addMessageDisabledPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES] = current
    }

    suspend fun removeMessageDisabledPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DISABLED_PACKAGES] = current
    }

    suspend fun addMessageDndPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES]?.toMutableSet() ?: mutableSetOf()
        current.add(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES] = current
    }

    suspend fun removeMessageDndPackage(packageName: String) = editor.edit {
        val current = it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES]?.toMutableSet() ?: return@edit
        current.remove(packageName)
        it[SettingsPreferenceKeys.MESSAGE_DND_PACKAGES] = current
    }

    suspend fun setMessageSuppressWhenSystemDnd(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.MESSAGE_SUPPRESS_WHEN_SYSTEM_DND] = enabled }

    suspend fun upsertMessageAppFilterRule(rule: MessageAppFilterRule) = editor.edit { prefs ->
        val current = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        if (rule.hasCustomFilter()) {
            current[rule.packageName] = rule
        } else {
            current.remove(rule.packageName)
        }
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(current.values)
    }

    suspend fun removeMessageAppFilterRule(packageName: String) = editor.edit { prefs ->
        val current = MessageAppFilterCodec.decodeAll(prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] ?: emptySet())
            .toMutableMap()
        current.remove(packageName)
        prefs[SettingsPreferenceKeys.MESSAGE_APP_FILTER_RULES] = MessageAppFilterCodec.encodeAll(current.values)
    }
}
