package com.slideindex.app.xposed.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock

/**
 * 模块侧（system_server）到 app 的事件桥客户端。
 *
 * 绑定失败/断线时 `isConnected` 为 false，接管逻辑据此完全放行（fail-open），
 * 避免事件被吞掉却没人处理。
 */
class ModuleGestureBridgeClient(
  private val context: Context,
  private val log: (String) -> Unit = {},
) {
  @Volatile
  private var bridge: IModuleGestureBridge? = null

  @Volatile
  private var binding = false

  @Volatile
  private var lastBindAttemptAtMs = 0L

  private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      bridge = IModuleGestureBridge.Stub.asInterface(service)
      binding = false
      log("module gesture bridge connected")
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      bridge = null
      binding = false
      log("module gesture bridge disconnected")
    }

    override fun onBindingDied(name: ComponentName?) {
      bridge = null
      binding = false
      log("module gesture bridge binding died")
    }

    override fun onNullBinding(name: ComponentName?) {
      // app 侧宿主未就绪时会返回 null，这里复位以便稍后重试。
      bridge = null
      binding = false
      log("module gesture bridge null binding, will retry")
    }
  }

  val isConnected: Boolean get() = bridge != null

  fun ensureBound() {
    if (bridge != null) return
    val now = SystemClock.elapsedRealtime()
    if (now - lastBindAttemptAtMs < BIND_RETRY_COOLDOWN_MS) return
    lastBindAttemptAtMs = now
    binding = true
    runCatching {
      val intent = Intent(ModuleHookBridgeContract.BRIDGE_SERVICE_ACTION).apply {
        setPackage(ModuleHookBridgeContract.MODULE_PACKAGE)
        component = ComponentName(
          ModuleHookBridgeContract.MODULE_PACKAGE,
          "${ModuleHookBridgeContract.MODULE_PACKAGE}.service.ModuleGestureBridgeService",
        )
      }
      val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
      if (!bound) {
        binding = false
        log("module gesture bridge bindService returned false")
      }
    }.onFailure {
      binding = false
      log("module gesture bridge bind failed: ${it.message}")
    }
  }

  fun sendTouch(
    sessionId: Long,
    sideId: Int,
    action: Int,
    x: Float,
    y: Float,
    eventTime: Long,
    downTime: Long,
    metaState: Int,
  ): Boolean {
    val target = bridge ?: return false
    return runCatching {
      target.onTouchEvent(sessionId, sideId, action, x, y, eventTime, downTime, metaState)
      true
    }.getOrElse {
      log("module gesture bridge send failed: ${it.message}")
      false
    }
  }

  /** 同步确认 app 能否处理该边；桥未连接时返回 false（模块据此放行）。 */
  fun canAcceptTouch(sideId: Int): Boolean {
    val target = bridge ?: return false
    return runCatching { target.canAcceptTouch(sideId) }.getOrElse {
      log("module gesture bridge canAcceptTouch failed: ${it.message}")
      false
    }
  }

  /**
   * 同步确认 app 能否处理该扩展目标（角轮盘/悬浮球线条）的这一点。
   *
   * 不做缓存：命中区随位置设置与键盘状态变化，按 DOWN 现场询问最稳妥。
   */
  fun canAcceptTouchAt(target: Int, x: Float, y: Float): Boolean {
    val bridgeTarget = bridge ?: return false
    return runCatching { bridgeTarget.canAcceptTouchAt(target, x, y) }.getOrElse {
      log("module gesture bridge canAcceptTouchAt failed: ${it.message}")
      false
    }
  }

  fun endSession(sessionId: Long, reason: Int) {
    val target = bridge ?: return
    runCatching { target.onSessionEnd(sessionId, reason) }
  }

  fun dispose() {
    bridge = null
    binding = false
    runCatching { context.unbindService(connection) }
  }

  private companion object {
    const val BIND_RETRY_COOLDOWN_MS = 2_500L
  }
}
