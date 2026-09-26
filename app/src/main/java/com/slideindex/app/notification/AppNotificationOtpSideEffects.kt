package com.slideindex.app.notification

import android.content.Context
import com.slideindex.app.otp.OtpAutoFillController
import com.slideindex.app.otp.OtpAutoInputOrchestrator
import com.slideindex.app.otp.OtpCodeAlertPresenter
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationOtpSideEffects @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val otpCodeAlertPresenter: OtpCodeAlertPresenter,
) : NotificationOtpSideEffects {
    override fun onVerificationCodeExtracted(
        context: Context,
        code: String,
        packageName: String,
        title: String,
        text: String,
        postedAtMs: Long,
        ruleName: String?,
        copyToClipboard: Boolean,
        autoInputEnabled: Boolean,
        recordId: String?
    ) {
        val appContext = context.applicationContext
        val settings = settingsRepository.readSnapshot()
        if (autoInputEnabled) {
            OtpAutoInputOrchestrator.requestAutoFill(appContext, code, settings, recordId)
        }
        if (copyToClipboard) {
            runCatching { OtpClipboardHelper.copyCode(appContext, code) }
                .onFailure { android.util.Log.e("OtpSideEffects", "Clipboard copy failed", it) }
        }
        otpCodeAlertPresenter.present(
            code = code,
            sourceLabel = title.ifBlank { packageName },
            settings = settings,
        )
    }
}
