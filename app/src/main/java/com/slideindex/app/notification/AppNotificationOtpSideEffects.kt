package com.slideindex.app.notification

import android.content.Context
import com.slideindex.app.otp.OtpAutoFillController
import com.slideindex.app.otp.OtpAutoInputOrchestrator
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Singleton
class AppNotificationOtpSideEffects @Inject constructor(
    private val settingsRepository: SettingsRepository,
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
    ) {
        val appContext = context.applicationContext
        if (autoInputEnabled) {
            val settings = runBlocking { settingsRepository.settings.first() }
            OtpAutoFillController.queueCode(code)
            OtpAutoInputOrchestrator.requestAutoFill(appContext, code, settings)
        }
        if (copyToClipboard) {
            runCatching { OtpClipboardHelper.copyCode(appContext, code) }
                .onFailure { android.util.Log.e("OtpSideEffects", "Clipboard copy failed", it) }
        }
    }
}
