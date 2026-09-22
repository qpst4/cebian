package com.slideindex.app.overlay

import android.os.SystemClock

/**
 * 标记"当前触摸来自 LSPosed 模块的输入层接管"。
 *
 * 模块装了 IInputFilter 之后，每条事件都会经由过滤器转发/回注；
 * 若此时 app 再走"注入点击放行"（OverlayPassthrough），注入事件会被模块重新接管，
 * 形成 app↔模块回环，把 system_server 打满。接管期间因此禁用点击注入放行。
 */
object ModuleForwardedTouchGate {
  private const val RECENT_WINDOW_MS = 1_500L

  @Volatile
  private var lastForwardedAtMs: Long = 0L

  fun markForwarded() {
    lastForwardedAtMs = SystemClock.elapsedRealtime()
  }

  fun isRecent(): Boolean =
    SystemClock.elapsedRealtime() - lastForwardedAtMs <= RECENT_WINDOW_MS
}