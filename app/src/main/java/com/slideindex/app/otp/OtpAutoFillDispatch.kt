package com.slideindex.app.otp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.overlay.OverlayStatePort
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper

/**
 * 把"抓到一个验证码，让浮层去填"这件事送出去。
 *
 * 为什么需要它：无障碍实例在 `:overlay`，而短信抓码在默认进程，所以这条请求**只能跨进程**。
 * 原来的实现是一句普通 `sendBroadcast`，而 `:overlay` 那边的接收者是**动态注册**的：
 * 进程没起来 / 刚重启时，这条广播发给空气 —— 没有接收者，广播也不会把进程拉起来，
 * 记录于是永远停在"填充中"（重构前是同进程直调，不存在这个口子）。
 *
 * 这里做两件事：
 * 1. 发之前看浮层在不在（无障碍连着就说明它在），不在就顺手把常驻服务拉一次并短重试；
 * 2. 每次发送都记一行日志（含记录 id），下次出问题能直接看出请求有没有送出去。
 *
 * 超时兜底不在这里：由 [OtpRecordsRepository] 读盘时把"过期还在填充中"的记录结掉，
 * 这样即使本进程被杀也不会留下一条永远转圈的记录。
 *
 * 重发是安全的：[OtpAutoInputOrchestrator] 对同一条验证码做去重，不会填两次。
 */
object OtpAutoFillDispatch {

    private const val TAG = "OtpAutoFillDispatch"

    /** 首发送 + 两次重试的等待时间（给浮层进程起来的窗口）。 */
    private val RETRY_DELAYS_MS = longArrayOf(0L, 1_000L, 3_000L)

    private val handler = Handler(Looper.getMainLooper())

    fun request(
        context: Context,
        code: String,
        recordId: String?,
        settings: AppSettings,
    ) {
        val appContext = context.applicationContext
        var attempt = 0
        fun sendAndMaybeRetry() {
            val overlayReachable =
                OverlayStatePort.hasServiceState() && OverlayStatePort.isServiceConnected()
            // 一律发一次：广播很便宜，而且"没收到过状态广播"只代表未知，不代表浮层真的不在。
            OverlayStatePort.sendOtpAutoFill(appContext, code, recordId)
            if (overlayReachable) {
                Log.i(TAG, "otp fill request sent (recordId=$recordId, attempt=$attempt)")
                return
            }
            attempt++
            if (attempt < RETRY_DELAYS_MS.size) {
                nudgeOverlay(appContext, settings)
                Log.i(
                    TAG,
                    "otp fill sent but overlay not reachable, retry #$attempt " +
                        "in ${RETRY_DELAYS_MS[attempt]}ms (recordId=$recordId)",
                )
                handler.postDelayed({ sendAndMaybeRetry() }, RETRY_DELAYS_MS[attempt])
                return
            }
            // 送到送不到都到此为止：记录若一直没结果，会由仓库的超时兜底落成失败。
            Log.w(TAG, "otp fill request undeliverable? overlay mirror offline (recordId=$recordId)")
        }
        sendAndMaybeRetry()
    }

    /**
     * 尽量把浮层进程拉起来：覆盖安装 / 被系统杀过之后，它可能还没起。
     * 这里只是"顺手拉一把"，失败无所谓（比如后台启动前台服务被系统拒了）。
     */
    private fun nudgeOverlay(context: Context, settings: AppSettings) {
        if (!settings.serviceEnabled) return
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return
        if (!PermissionHelper.hasNotificationPermission(context)) return
        runCatching {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }.onFailure {
            Log.w(TAG, "nudge overlay service failed: ${it.message}")
        }
    }
}
