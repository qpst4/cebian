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
    override fun canAcceptTouch(sideId: Int): Boolean =
      SlideIndexAccessibilityService.canHandleForwardedSide(sideId)

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
      SlideIndexAccessibilityService.handleModuleGestureSessionEnd(sessionId, reason)
    }
  }

  override fun onBind(intent: Intent?): IBinder? {
    val callingUid = Binder.getCallingUid()
    if (!isTrustedCaller(callingUid)) {
      Log.w(TAG, "Rejected module bridge bind from uid=$callingUid")
      return null
    }
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