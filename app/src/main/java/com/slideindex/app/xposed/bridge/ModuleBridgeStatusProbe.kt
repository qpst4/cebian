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
  enum class Status {
    /** 接管真的会生效：模块已连上事件桥，且 app 侧 overlay 宿主就绪。 */
    Ready,

    /** 模块已装好，但接管此刻跑不起来（事件桥未连 / app 宿主未就绪）。 */
    Armed,

    /** 模块已装好，三个接管开关全关——这不是故障，只是没启用。 */
    SwitchedOff,

    /** 模块没装齐，或压根没回应。 */
    NotReady,
  }

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
          callback(classify(snapshot), snapshot.detail)
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

  private fun classify(snapshot: ModuleBridgeStatusStore.Snapshot): Status = when {
    snapshot.active -> Status.Ready
    snapshot.state == ModuleHookBridgeContract.STATUS_STATE_ARMED ->
      if (snapshot.detail.contains(CONTROLLER_DISABLED_MARK)) Status.SwitchedOff else Status.Armed
    // 旧模块不带 state 字段，只能从状态串前缀推断；认不出来就按未就绪处理（旧行为）。
    snapshot.detail.startsWith(ARMED_DETAIL_PREFIX) ->
      if (snapshot.detail.contains(CONTROLLER_DISABLED_MARK)) Status.SwitchedOff else Status.Armed
    else -> Status.NotReady
  }

  private const val ARMED_DETAIL_PREFIX = "armed:"
  private const val CONTROLLER_DISABLED_MARK = "controller=disabled"
  private const val POLL_INTERVAL_MS = 250L
}
