package com.slideindex.app.notification

import android.content.Context

/** OTP clipboard / auto-fill side effects implemented in :app. */
interface NotificationOtpSideEffects {
    fun onVerificationCodeExtracted(
        context: Context,
        code: String,
        packageName: String,
        title: String,
        text: String,
        postedAtMs: Long,
        ruleName: String?,
        copyToClipboard: Boolean,
        autoInputEnabled: Boolean,
    )
}
