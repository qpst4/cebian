package com.slideindex.app.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.slideindex.app.xposed.bridge.IModuleGestureBridge

/**
 * 供 system_server 内的 LSPosed 模块绑定的事件桥服务。
 *
 * 只接受系统 UID（1000，模块注入在 system_server）与自身进程的调用，
 * 因此必须声明为 exported；实际数据只包含触摸坐标与时间戳。
 */
class ModuleGestureBridgeService : Service() {

  private val binder = object : IModuleGestureBridge.Stub() {
    override fun canAcceptTouch(sideId: Int): Boolean {
      if (!enforceTrustedCaller()) return false
      return SlideIndexAccessibilityService.canHandleForwardedSide(sideId)
    }

    override fun canAcceptTouchAt(target: Int, x: Float, y: Float): Boolean {
      if (!enforceTrustedCaller()) return false
      return SlideIndexAccessibilityService.canHandleForwardedTargetAt(target, x, y)
    }

    override fun onTouchEvent(
      sessionId: Long,
      sideId: Int,
      action: Int,
      x: Float,
      y: Float,
      eventTime: Long,
      downTime: Long,
      metaState: Int,
    ) {
      if (!enforceTrustedCaller()) return
      SlideIndexAccessibilityService.handleModuleGestureTouch(
        sessionId = sessionId,
        sideId = sideId,
        action = action,
        x = x,
        y = y,
        eventTime = eventTime,
        downTime = downTime,
        metaState = metaState,
      )
    }

    override fun onSessionEnd(sessionId: Long, reason: Int) {
      if (!enforceTrustedCaller()) return
      SlideIndexAccessibilityService.handleModuleGestureSessionEnd(sessionId, reason)
    }

    private fun enforceTrustedCaller(): Boolean {
      val callingUid = Binder.getCallingUid()
      if (!isTrustedCaller(callingUid)) {
        Log.w(TAG, "Rejected module bridge call from uid=$callingUid")
        return false
      }
      return true
    }
  }

  override fun onBind(intent: Intent?): IBinder? {
    if (!SlideIndexAccessibilityService.isOverlayReady()) {
      // 宿主未就绪时拒绝绑定，模块会退化为完全放行，避免吞掉事件却无人处理。
      Log.i(TAG, "Overlay host not ready, rejecting module bridge bind")
      return null
    }
    return binder
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

  private fun isTrustedCaller(uid: Int): Boolean =
    uid == Process.SYSTEM_UID || uid == applicationInfo.uid

  private companion object {
    const val TAG = "ModuleGestureBridge"
  }
}
