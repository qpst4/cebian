package com.slideindex.app.di

import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 把设置里的「记录分类上限」推给 [OtpRecordsRepository]。
 *
 * 与 [OtpAutoFillStatsInstaller] 同思路：仓库在 `:feature:otp`，不依赖设置模块，
 * 由 App 侧单向推入。
 */
@Singleton
class OtpRecordLimitsInstaller @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val otpRecordsRepository: OtpRecordsRepository,
    private val applicationScope: CoroutineScope,
) {
    fun install() {
        applicationScope.launch {
            settingsRepository.settings
                .map { settings ->
                    Triple(
                        settings.otpRecordCodeLimit,
                        settings.otpRecordPlainSmsLimit,
                        settings.otpRecordAppNotifyLimit,
                    )
                }
                .distinctUntilChanged()
                .collect { (code, plainSms, appNotify) ->
                    otpRecordsRepository.setCategoryLimits(
                        codeLimit = code,
                        plainSmsLimit = plainSms,
                        appNotifyLimit = appNotify,
                    )
                }
        }
    }
}
