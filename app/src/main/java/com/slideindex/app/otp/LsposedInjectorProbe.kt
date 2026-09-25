package com.slideindex.app.otp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.slideindex.app.R
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract

object LsposedInjectorProbe {
  private const val TAG = "LsposedInjectorProbe"
  private const val PROBE_TIMEOUT_MS = 2_500L

  enum class Status {
    Ready,
    NotReady,
    Timeout,
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private var receiverRegistered = false
  private var pendingAttemptId: Long? = null
  private var pendingCallbacks = mutableListOf<(Status, String) -> Unit>()

  private val resultReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT_RESULT) return
      val attemptId = intent.getLongExtra(OtpAutoInputBroadcastContract.EXTRA_ATTEMPT_ID, -1L)
      if (attemptId != pendingAttemptId) return
      val success = intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_SUCCESS, false)
      val strategy = intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_STRATEGY).orEmpty()
      val reason = intent.getStringExtra(OtpAutoInputBroadcastContract.EXTRA_REASON).orEmpty()
      Log.i(TAG, "Probe result: success=$success strategy=$strategy reason=$reason")
      finish(
        when {
          success &&
            strategy == OtpAutoInputBroadcastContract.STRATEGY_SYSTEM_INJECT &&
            reason == OtpAutoInputBroadcastContract.SystemInjectReason.PROBE ->
            Status.Ready to context.getString(R.string.otp_lsposed_probe_ready)
          success ->
            Status.Ready to context.getString(
              R.string.otp_lsposed_probe_response,
              strategy,
              OtpAutoFillUiLabels.formatReason(context, reason)
            )
          else ->
            Status.NotReady to context.getString(
              R.string.otp_lsposed_probe_failed,
              OtpAutoFillUiLabels.formatReason(context, reason)
            )
        }
      )
    }
  }

  fun probe(context: Context, callback: (Status, String) -> Unit) {
    val appContext = context.applicationContext
    ensureReceiver(appContext)
    if (pendingAttemptId != null) {
      // 已有一轮探测在途：复用它的结果。
      // 以前这里直接回调"检测中"并按未就绪处理，页面 2.5s 自动重测正好撞上上一轮的 2.5s 超时，
      // 会把"注入其实就绪、只是回得慢"误报成未就绪。
      pendingCallbacks += callback
      Log.i(TAG, "Probe already in flight, reusing result")
      return
    }
    val attemptId = SystemClock.elapsedRealtimeNanos()
    pendingAttemptId = attemptId
    pendingCallbacks = mutableListOf(callback)
    Log.i(TAG, "Sending LSPosed probe attemptId=$attemptId")
    appContext.sendOrderedBroadcast(
      OtpAutoInputBroadcastContract.buildProbeIntent(attemptId),
      null
    )
    mainHandler.postDelayed({
      if (pendingAttemptId == attemptId) {
        finish(
          Status.Timeout to appContext.getString(R.string.otp_lsposed_probe_timeout)
        )
      }
    }, PROBE_TIMEOUT_MS)
  }

  private fun finish(result: Pair<Status, String>) {
    pendingAttemptId = null
    mainHandler.removeCallbacksAndMessages(null)
    val callbacks = pendingCallbacks
    pendingCallbacks = mutableListOf()
    mainHandler.post { callbacks.forEach { it(result.first, result.second) } }
  }

  private fun ensureReceiver(context: Context) {
    if (receiverRegistered) return
    val filter = IntentFilter(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT_RESULT)
    ContextCompat.registerReceiver(context, resultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    receiverRegistered = true
  }
}
