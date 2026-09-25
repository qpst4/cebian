package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.R
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.otp.OtpOfficialRulesLoader
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.otp.SmsBlacklistRuleSet
import com.slideindex.app.otp.OtpUserRulesCodec
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class OtpSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    private val otpOfficialRulesLoader: OtpOfficialRulesLoader,
    private val otpRecordsRepository: OtpRecordsRepository,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val _officialRules = MutableStateFlow(otpOfficialRulesLoader.getRules())
    val officialRules: StateFlow<List<OtpMatchRule>> = _officialRules.asStateFlow()

    fun refreshOfficialRules() {
        _officialRules.value = otpOfficialRulesLoader.refresh()
    }

    fun setOtpCopyToClipboard(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpCopyToClipboard(enabled)
    }

    fun setOtpKeywordsRegex(value: String) = launchSettingsWrite {
        settingsRepository.setOtpKeywordsRegex(value)
    }

    fun setOtpOfficialRuleEnabled(ruleId: String, enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpOfficialRuleEnabled(ruleId, enabled)
    }

    fun setOtpUserMatchRules(rules: List<OtpMatchRule>) = launchSettingsWrite {
        settingsRepository.setOtpUserMatchRules(rules)
    }

    fun setOtpAutoInputEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpAutoInputEnabled(enabled)
    }

    fun setOtpAutoConfirmEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpAutoConfirmEnabled(enabled)
    }

    fun setOtpAutoInputDelayMs(value: Int) = launchSettingsWrite {
        settingsRepository.setOtpAutoInputDelayMs(value)
    }

    fun setOtpAutoInputIntervalMs(value: Int) = launchSettingsWrite {
        settingsRepository.setOtpAutoInputIntervalMs(value)
    }

    fun setOtpLsposedSmsCaptureEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpLsposedSmsCaptureEnabled(enabled)
    }

    fun setOtpLsposedSystemInjectEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpLsposedSystemInjectEnabled(enabled)
    }

    fun setOtpCodeNotificationEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpCodeNotificationEnabled(enabled)
    }

    fun setOtpCodeNotificationRetentionSeconds(value: Int) = launchSettingsWrite {
        settingsRepository.setOtpCodeNotificationRetentionSeconds(value)
    }

    fun setOtpShowCodeToast(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpShowCodeToast(enabled)
    }

    /** 用户规则表导出（JSON 文本，交给系统文件选择器写盘）。 */
    fun exportUserRulesJson(): String =
        OtpUserRulesCodec.encode(settingsRepository.readSnapshot().otpUserMatchRules)

    fun notifyRulesExported(success: Boolean) {
        if (success) {
            userMessageBus.showSuccess(appContext.getString(R.string.otp_rules_export_done))
        } else {
            userMessageBus.showError(appContext.getString(R.string.otp_rules_export_failed))
        }
    }

    /** 导入用户规则表：按 id 与内容指纹去重后合并。 */
    fun importUserRulesJson(raw: String) = launchSettingsWrite(R.string.otp_rules_import_failed) {
        val parsed = OtpUserRulesCodec.decode(raw)
            ?: return@launchSettingsWrite Result.failure(IllegalArgumentException("invalid rules table"))
        val current = settingsRepository.readSnapshot().otpUserMatchRules
        val merged = OtpUserRulesCodec.merge(current, parsed)
        settingsRepository.setOtpUserMatchRules(merged).onSuccess {
            userMessageBus.showSuccess(
                appContext.getString(R.string.otp_rules_import_done, merged.size - current.size),
            )
        }
    }

    fun addOtpBlockedApp(packageName: String) = launchSettingsWrite {
        settingsRepository.setOtpBlockedPackages(
            settingsRepository.readSnapshot().otpBlockedPackages + packageName,
        )
    }

    fun removeOtpBlockedApp(packageName: String) = launchSettingsWrite {
        settingsRepository.setOtpBlockedPackages(
            settingsRepository.readSnapshot().otpBlockedPackages - packageName,
        )
    }

    fun setOtpSmsBlacklist(rules: SmsBlacklistRuleSet) = launchSettingsWrite {
        settingsRepository.setOtpSmsBlacklist(rules)
    }

    fun setOtpBlockCodeSmsEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpBlockCodeSmsEnabled(enabled)
    }

    fun setOtpMarkSmsReadEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpMarkSmsReadEnabled(enabled)
    }

    fun setOtpDeleteSmsAfterExtractEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpDeleteSmsAfterExtractEnabled(enabled)
    }

    fun setOtpRecordCodeEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpRecordCodeEnabled(enabled)
    }

    fun setOtpRecordPlainSmsEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpRecordPlainSmsEnabled(enabled)
    }

    fun setOtpRecordAppNotifyEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOtpRecordAppNotifyEnabled(enabled)
    }

    fun setOtpRecordCodeLimit(value: Int) = launchSettingsWrite {
        settingsRepository.setOtpRecordCodeLimit(value)
    }

    fun setOtpRecordPlainSmsLimit(value: Int) = launchSettingsWrite {
        settingsRepository.setOtpRecordPlainSmsLimit(value)
    }

    fun setOtpRecordAppNotifyLimit(value: Int) = launchSettingsWrite {
        settingsRepository.setOtpRecordAppNotifyLimit(value)
    }

    fun recordTestOtp(code: String, sampleText: String, ruleName: String?) = launchRepositoryWrite {
        otpRecordsRepository.recordSuspend(
            code = code,
            packageName = "com.test.sms",
            title = "",
            text = sampleText,
            ruleName = ruleName,
            isTest = true,
        ).map { }
    }
}
