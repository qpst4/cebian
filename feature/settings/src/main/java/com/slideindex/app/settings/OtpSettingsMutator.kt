package com.slideindex.app.settings

import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.otp.OtpCodeAlertPolicy
import com.slideindex.app.otp.OtpMatchRuleCodec
import com.slideindex.app.otp.OtpRecordLimits
import com.slideindex.app.otp.SmsBlacklistRuleSet
import com.slideindex.app.otp.SmsBlacklistRuleSetCodec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtpSettingsMutator @Inject constructor(
    private val editor: SettingsPreferencesEditor,
) {
    suspend fun setOtpCopyToClipboard(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.OTP_COPY_TO_CLIPBOARD] = enabled }

    suspend fun setOtpKeywordsRegex(value: String) = editor.edit {
        it[SettingsPreferenceKeys.OTP_KEYWORDS_REGEX] = value.ifBlank {
            OtpKeywords.DEFAULT_KEYWORDS_REGEX
        }
    }

    suspend fun setOtpUserMatchRules(rules: List<com.slideindex.app.otp.OtpMatchRule>) = editor.edit {
        it[SettingsPreferenceKeys.OTP_USER_MATCH_RULES] = OtpMatchRuleCodec.encodeAll(rules)
    }

    suspend fun setOtpDisabledOfficialRuleIds(ids: Set<String>) = editor.edit {
        it[SettingsPreferenceKeys.OTP_DISABLED_OFFICIAL_RULE_IDS] = ids
    }

    suspend fun setOtpOfficialRuleEnabled(ruleId: String, enabled: Boolean) = editor.edit { prefs ->
        val current = prefs[SettingsPreferenceKeys.OTP_DISABLED_OFFICIAL_RULE_IDS]?.toMutableSet() ?: mutableSetOf()
        if (enabled) {
            current.remove(ruleId)
        } else {
            current.add(ruleId)
        }
        prefs[SettingsPreferenceKeys.OTP_DISABLED_OFFICIAL_RULE_IDS] = current
    }

    suspend fun setOtpAutoInputEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.OTP_AUTO_INPUT_ENABLED] = enabled }

    suspend fun setOtpAutoConfirmEnabled(enabled: Boolean) = editor.edit { it[SettingsPreferenceKeys.OTP_AUTO_CONFIRM_ENABLED] = enabled }

    suspend fun setOtpAutoInputDelayMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.OTP_AUTO_INPUT_DELAY_MS] = value.coerceIn(0, 5000)
    }

    suspend fun setOtpAutoInputIntervalMs(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.OTP_AUTO_INPUT_INTERVAL_MS] = value.coerceIn(0, 500)
    }

    suspend fun setOtpLsposedSmsCaptureEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_LSPOSED_SMS_CAPTURE_ENABLED] = enabled }

    suspend fun setOtpLsposedSystemInjectEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_LSPOSED_SYSTEM_INJECT_ENABLED] = enabled }

    suspend fun setOtpCodeNotificationEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_CODE_NOTIFICATION_ENABLED] = enabled }

    suspend fun setOtpCodeNotificationRetentionSeconds(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.OTP_CODE_NOTIFICATION_RETENTION_SECONDS] =
            OtpCodeAlertPolicy.normalizeRetentionSeconds(value)
    }

    suspend fun setOtpShowCodeToast(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_SHOW_CODE_TOAST] = enabled }

    suspend fun setOtpSmsBlacklist(rules: SmsBlacklistRuleSet) = editor.edit {
        it[SettingsPreferenceKeys.OTP_SMS_BLACKLIST] = SmsBlacklistRuleSetCodec.encode(rules)
    }

    suspend fun setOtpBlockCodeSmsEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_BLOCK_CODE_SMS_ENABLED] = enabled }

    suspend fun setOtpMarkSmsReadEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_MARK_SMS_READ_ENABLED] = enabled }

    suspend fun setOtpDeleteSmsAfterExtractEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_DELETE_SMS_AFTER_EXTRACT_ENABLED] = enabled }

    suspend fun setOtpRecordCodeEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_RECORD_CODE_ENABLED] = enabled }

    suspend fun setOtpRecordPlainSmsEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_RECORD_PLAIN_SMS_ENABLED] = enabled }

    suspend fun setOtpRecordAppNotifyEnabled(enabled: Boolean) =
        editor.edit { it[SettingsPreferenceKeys.OTP_RECORD_APP_NOTIFY_ENABLED] = enabled }

    suspend fun setOtpRecordCodeLimit(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.OTP_RECORD_CODE_LIMIT] = OtpRecordLimits.normalize(value)
    }

    suspend fun setOtpRecordPlainSmsLimit(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.OTP_RECORD_PLAIN_SMS_LIMIT] = OtpRecordLimits.normalize(value)
    }

    suspend fun setOtpRecordAppNotifyLimit(value: Int) = editor.edit {
        it[SettingsPreferenceKeys.OTP_RECORD_APP_NOTIFY_LIMIT] = OtpRecordLimits.normalize(value)
    }

    suspend fun setOtpBlockedPackages(packages: Set<String>) = editor.edit {
        it[SettingsPreferenceKeys.OTP_BLOCKED_PACKAGES] = packages
    }
}
