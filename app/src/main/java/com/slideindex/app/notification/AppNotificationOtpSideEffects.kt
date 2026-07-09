package com.slideindex.app.notification

import android.content.Context
import com.slideindex.app.otp.OtpAutoFillController
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.service.SlideIndexAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationOtpSideEffects @Inject constructor() : NotificationOtpSideEffects {
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
        if (copyToClipboard) {
            OtpClipboardHelper.copyCode(context, code)
        }
        if (autoInputEnabled) {
            OtpAutoFillController.queueCode(code)
            SlideIndexAccessibilityService.scheduleOtpAutoFill()
        }
    }
}
