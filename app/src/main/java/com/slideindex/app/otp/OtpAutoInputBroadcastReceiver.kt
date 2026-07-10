package com.slideindex.app.otp

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract

class OtpAutoInputBroadcastReceiver(
    private val service: AccessibilityService,
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        OtpAutoInputBroadcastHandler.onReceive(service, intent)
    }

    companion object {
        fun register(service: AccessibilityService): OtpAutoInputBroadcastReceiver {
            val receiver = OtpAutoInputBroadcastReceiver(service)
            val filter = IntentFilter(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT).apply {
                priority = OtpAutoInputBroadcastContract.RECEIVER_PRIORITY_ACCESSIBILITY
            }
            ContextCompat.registerReceiver(service, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            return receiver
        }

        fun unregister(service: AccessibilityService, receiver: BroadcastReceiver?) {
            if (receiver == null) return
            runCatching { service.unregisterReceiver(receiver) }
        }
    }
}
