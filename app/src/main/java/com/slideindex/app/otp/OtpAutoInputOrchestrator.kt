package com.slideindex.app.otp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.autofill.OtpAutoInputFallbackPolicy
import com.slideindex.app.otp.OtpCaptureDeduplicator
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings

@SuppressLint("StaticFieldLeak") // Pending context held only for in-flight auto-input attempt
object OtpAutoInputOrchestrator {
    private const val TAG = "OtpAutoInput"
    private const val RESULT_TIMEOUT_MS = 3_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var resultReceiverRegistered = false
    private var pendingAttemptId: Long? = null
    private var pendingCode: String? = null
    private var pendingSettings: AppSettings? = null
    private var pendingContext: Context? = null

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT_RESULT) return
            val attemptId = intent.getLongExtra(OtpAutoInputBroadcastContract.EXTRA_ATTEMPT_ID, -1L)
            if (attemptId != pendingAttemptId) return
            val success = intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_SUCCESS, false)
            val strategy = intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_STRATEGY).orEmpty()
            val reason = intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_REASON).orEmpty()
            Log.i(TAG, "Auto-input result: success=$success strategy=$strategy reason=$reason")
            clearPendingAttempt()
            if (success) {
                OtpAutoFillController.clearPending()
                return
            }
            handleFailure(context, reason, strategy)
        }
    }

    fun requestAutoFill(context: Context, code: String, settings: AppSettings) {
        if (!settings.otpAutoInputEnabled) return
        if (!OtpCaptureDeduplicator.tryConsumeAutoFillRequest(code)) {
            Log.d(TAG, "Skipping duplicate auto-fill request")
            return
        }
        val appContext = context.applicationContext
        ensureResultReceiver(appContext)
        val attemptId = SystemClock.elapsedRealtimeNanos()
        pendingAttemptId = attemptId
        pendingCode = code
        pendingSettings = settings
        pendingContext = appContext
        val request = OtpAutoInputBroadcastContract.Request(
            code = code,
            autoEnter = settings.otpAutoConfirmEnabled,
            inputIntervalMs = settings.otpAutoInputIntervalMs.toLong(),
            attemptId = attemptId,
            allowSystemInject = settings.otpLsposedSystemInjectEnabled,
        )
        val delayMs = settings.otpAutoInputDelayMs.coerceAtLeast(0).toLong()
        mainHandler.postDelayed({
            appContext.sendOrderedBroadcast(
                OtpAutoInputBroadcastContract.buildRequestIntent(request),
                null,
            )
            mainHandler.postDelayed({
                if (pendingAttemptId == attemptId) {
                    Log.w(TAG, "Auto-input timed out, trying fallbacks")
                    clearPendingAttempt()
                    handleFailure(appContext, "timeout", "none")
                }
            }, RESULT_TIMEOUT_MS)
        }, delayMs)
        Log.i(TAG, "Dispatched ordered auto-input broadcast attemptId=$attemptId")
    }

    private fun handleFailure(context: Context, reason: String, strategy: String) {
        pendingCode ?: return
        pendingSettings ?: return
        when {
            OtpAutoInputFallbackPolicy.shouldRetryAccessibility(reason) -> {
                SlideIndexAccessibilityService.scheduleOtpAutoFill()
            }
            else -> Log.w(TAG, "Auto-input failed without fallback: reason=$reason strategy=$strategy")
        }
        pendingCode = null
        pendingSettings = null
        pendingContext = null
    }

    private fun ensureResultReceiver(context: Context) {
        if (resultReceiverRegistered) return
        val filter = IntentFilter(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT_RESULT)
        ContextCompat.registerReceiver(context, resultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        resultReceiverRegistered = true
    }

    private fun clearPendingAttempt() {
        pendingAttemptId = null
        mainHandler.removeCallbacksAndMessages(null)
    }
}
