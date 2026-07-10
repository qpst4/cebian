package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.otp.OtpOfficialRulesLoader
import com.slideindex.app.otp.OtpRecordsRepository
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

    fun recordTestOtp(code: String, sampleText: String, ruleName: String?) = launchRepositoryWrite {
        otpRecordsRepository.recordSuspend(
            code = code,
            packageName = "com.test.sms",
            title = "",
            text = sampleText,
            ruleName = ruleName,
            isTest = true,
        )
    }
}
