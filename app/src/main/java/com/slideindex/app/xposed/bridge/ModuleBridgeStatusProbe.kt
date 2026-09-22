package com.slideindex.app.xposed.bridge

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * 「检测模块状态」：向 system_server 模块发状态请求，短时间内读回响应。
 *
 * 模块未安装/未启用时不会有响应，按超时处理为“未生效”。
 */
object ModuleBridgeStatusProbe {
  enum class Status { Ready, NotReady }

  private const val TIMEOUT_MS = 2_500L

  private val mainHandler = Handler(Looper.getMainLooper())

  fun probe(context: Context, callback: (Status, String) -> Unit) {
    val appContext = context.applicationContext
    val requestedAtMs = System.currentTimeMillis()
    appContext.sendBroadcast(
      Intent(ModuleHookBridgeContract.ACTION_MODULE_STATUS_REQUEST),
    )
    val deadline = android.os.SystemClock.elapsedRealtime() + TIMEOUT_MS
    val poll = object : Runnable {
      override fun run() {
        val snapshot = ModuleBridgeStatusStore.read(appContext)
        if (snapshot.updatedAtMs >= requestedAtMs) {
          callback(if (snapshot.active) Status.Ready else Status.NotReady, snapshot.detail)
          return
        }
        if (android.os.SystemClock.elapsedRealtime() >= deadline) {
          callback(Status.NotReady, NO_RESPONSE)
          return
        }
        mainHandler.postDelayed(this, POLL_INTERVAL_MS)
      }
    }
    mainHandler.postDelayed(poll, POLL_INTERVAL_MS)
  }

  const val NO_RESPONSE = "no-response"

  private const val POLL_INTERVAL_MS = 250L
}