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
  private var pendingCallback: ((Status, String) -> Unit)? = null

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
        if (success && strategy == "system_inject" && reason == "probe") {
          Status.Ready to "系统注入 Hook 已就绪"
        } else if (success) {
          Status.Ready to "收到响应：$strategy/$reason"
        } else {
          Status.NotReady to "系统注入未响应：$reason"
        },
      )
    }
  }

  fun probe(context: Context, callback: (Status, String) -> Unit) {
    val appContext = context.applicationContext
    ensureReceiver(appContext)
    if (pendingCallback != null) {
      callback(Status.NotReady, "正在检测，请稍候")
      return
    }
    val attemptId = SystemClock.elapsedRealtimeNanos()
    pendingAttemptId = attemptId
    pendingCallback = callback
    Log.i(TAG, "Sending LSPosed probe attemptId=$attemptId")
    appContext.sendOrderedBroadcast(
      OtpAutoInputBroadcastContract.buildProbeIntent(attemptId),
      null,
    )
    mainHandler.postDelayed({
      if (pendingAttemptId == attemptId) {
        finish(Status.Timeout to "超时：LSPosed 未在 system_server 注册注入接收器")
      }
    }, PROBE_TIMEOUT_MS)
  }

  private fun finish(result: Pair<Status, String>) {
    pendingAttemptId = null
    mainHandler.removeCallbacksAndMessages(null)
    val callback = pendingCallback
    pendingCallback = null
    mainHandler.post { callback?.invoke(result.first, result.second) }
  }

  private fun ensureReceiver(context: Context) {
    if (receiverRegistered) return
    val filter = IntentFilter(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT_RESULT)
    ContextCompat.registerReceiver(context, resultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    receiverRegistered = true
  }
}
