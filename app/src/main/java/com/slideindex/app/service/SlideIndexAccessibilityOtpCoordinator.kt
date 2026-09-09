package com.slideindex.app.service

import android.content.BroadcastReceiver
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.otp.OtpAutoFillController
import com.slideindex.app.otp.OtpAutoInputBroadcastReceiver

internal class SlideIndexAccessibilityOtpCoordinator(
    private val service: SlideIndexAccessibilityService,
    private val deps: AppDependencies
) {
    private var lastOtpCheckUptime = 0L
    private var otpAutoInputReceiver: BroadcastReceiver? = null

    fun maybeAutoFillOtp() {
        val now = SystemClock.uptimeMillis()
        if (now - lastOtpCheckUptime < 300L) return
        lastOtpCheckUptime = now
        if (OtpAutoFillController.isFillingActive()) return
        val settings = deps.settingsRepository.readSnapshot()
        if (!settings.otpAutoInputEnabled || !OtpAutoFillController.hasPendingCode()) return
        OtpAutoFillController.scheduleAutoFill(service, settings)
    }

    fun registerReceiver() {
        if (otpAutoInputReceiver != null) return
        otpAutoInputReceiver = OtpAutoInputBroadcastReceiver.register(service)
        Log.i(TAG, "OTP auto-input broadcast receiver registered")
    }

    fun unregisterReceiver() {
        OtpAutoInputBroadcastReceiver.unregister(service, otpAutoInputReceiver)
        otpAutoInputReceiver = null
    }

    fun scheduleAutoFill() {
        val settings = deps.settingsRepository.readSnapshot()
        if (!settings.otpAutoInputEnabled) return
        OtpAutoFillController.scheduleAutoFill(service, settings)
    }

    companion object {
        private const val TAG = "SlideIndexA11y"
    }
}
