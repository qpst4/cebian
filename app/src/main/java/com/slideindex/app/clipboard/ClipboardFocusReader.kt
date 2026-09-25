package com.slideindex.app.clipboard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.provider.Settings
import com.slideindex.app.util.PermissionHelper

/**
 * Adapted from [ClipShare](https://github.com/aa2013/ClipShare) (GPL-3.0).
 *
 * ClipShare-style clipboard read: briefly add a 16×16 **focusable** overlay so
 * [ClipboardManager] is readable on Android 10+.
 */
object ClipboardFocusReader {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var inFlight = false
    private val pendingCallbacks = ArrayDeque<(ClipboardPayload?) -> Unit>()

    /** 一次读取最多尝试几次（首次 + 2 次重试）。 */
    private const val MAX_ATTEMPTS = 3

    /** 单次尝试的窗口：焦点回调通常几毫秒内到，50ms 足够覆盖 post/超时兜底。 */
    private const val ATTEMPT_WINDOW_MS = 50L

    /** 失败后换新探针重试的间隔。 */
    private const val RETRY_DELAY_MS = 60L

    /** 探针窗口硬上限，防止读取卡住时窗口留在屏幕上。 */
    private const val PROBE_LIFETIME_MS = 200L

    fun read(context: Context, onResult: (ClipboardPayload?) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            readOnMain(context.applicationContext, onResult)
        } else {
            mainHandler.post { readOnMain(context.applicationContext, onResult) }
        }
    }

    private fun readOnMain(appContext: Context, onResult: (ClipboardPayload?) -> Unit) {
        if (inFlight) {
            pendingCallbacks.addLast(onResult)
            return
        }
        inFlight = true
        ClipboardReadTelemetry.onReadRequested()
        startAttempt(appContext, onResult, attempt = 0)
    }

    /**
     * 单次尝试：加 16×16 可聚焦探针，在「拿到窗口焦点 / 下一帧 / 尝试窗口到期」三个时点各读一次，
     * 只要读到内容就结束；一直读到 null 就换一个新探针重试。
     *
     * 重试的原因：同一次复制如果有另一个剪贴板应用（实测 ClipShare）也在抢焦点，
     * 我们的探针可能只持有几毫秒焦点，读取时已失焦，`getPrimaryClip()` 返回 null。
     */
    private fun startAttempt(
        appContext: Context,
        onResult: (ClipboardPayload?) -> Unit,
        attempt: Int
    ) {
        val windowManager = appContext.getSystemService(WindowManager::class.java)
        if (windowManager == null || !PermissionHelper.canDrawOverlays(appContext)) {
            val payload = ClipboardReader.read(appContext)
            ClipboardReadTelemetry.onProbeUnavailable(payload != null)
            finishRead(appContext, onResult, payload)
            return
        }
        val probe = ProbeView(appContext)
        val overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            16,
            16,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.RGBA_8888
        ).apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }
        val added = runCatching { windowManager.addView(probe, params) }.isSuccess
        if (!added) {
            val payload = ClipboardReader.read(appContext)
            ClipboardReadTelemetry.onProbeUnavailable(payload != null)
            finishRead(appContext, onResult, payload)
            return
        }

        val startedAtMs = SystemClock.uptimeMillis()
        var removed = false
        var attemptFinished = false
        var payload: ClipboardPayload? = null
        var readReason = "none"
        var hasFocus = false
        var focusGainedAtMs = -1L
        var focusLostAtMs = -1L
        var focusedAtRead = false

        fun safeRemove() {
            if (removed) return
            val ok = runCatching { windowManager.removeViewImmediate(probe) }.isSuccess ||
                runCatching { windowManager.removeView(probe) }.isSuccess
            if (ok) removed = true
        }

        fun finishAttempt() {
            if (attemptFinished) return
            attemptFinished = true
            safeRemove()
            val now = SystemClock.uptimeMillis()
            val focusMs = if (focusGainedAtMs < 0) {
                -1L
            } else {
                (if (focusLostAtMs >= 0) focusLostAtMs else now) - focusGainedAtMs
            }
            ClipboardReadTelemetry.onAttempt(
                context = appContext,
                attempt = attempt + 1,
                maxAttempts = MAX_ATTEMPTS,
                reason = readReason,
                focusGained = focusGainedAtMs >= 0,
                focusMs = focusMs,
                focusedAtRead = focusedAtRead,
                success = payload != null,
                elapsedMs = now - startedAtMs,
            )
            if (payload != null || attempt + 1 >= MAX_ATTEMPTS) {
                finishRead(appContext, onResult, payload)
            } else {
                mainHandler.postDelayed(
                    { startAttempt(appContext, onResult, attempt + 1) },
                    RETRY_DELAY_MS,
                )
            }
        }

        fun performRead(reason: String) {
            if (attemptFinished) return
            if (readReason == "none") readReason = reason
            focusedAtRead = hasFocus
            val value = ClipboardReader.read(appContext)
            if (value != null) {
                payload = value
                finishAttempt()
            }
        }

        // 1) 窗口一拿到焦点立刻读：这是权限判定上最早的合法时机。
        probe.onWindowFocus = { focused ->
            if (focused) {
                hasFocus = true
                if (focusGainedAtMs < 0) focusGainedAtMs = SystemClock.uptimeMillis()
                performRead("focus")
            } else {
                hasFocus = false
                if (focusGainedAtMs >= 0 && focusLostAtMs < 0) {
                    focusLostAtMs = SystemClock.uptimeMillis()
                }
            }
        }

        // 2) 兜底：保留原本「下一帧读一次」的路径，ROM 不派发焦点回调时仍然可用。
        probe.post {
            runCatching { probe.requestFocus() }
            performRead("post")
        }

        // 3) 尝试窗口到期：最后再读一次，然后结束本次尝试（读不到就重试）。
        mainHandler.postDelayed({
            performRead("timeout")
            finishAttempt()
        }, ATTEMPT_WINDOW_MS)

        // 探针硬上限：读取异常卡住时也不能让窗口一直留在屏幕上。
        mainHandler.postDelayed({ safeRemove() }, PROBE_LIFETIME_MS)
    }

    private fun finishRead(
        appContext: Context,
        onResult: (ClipboardPayload?) -> Unit,
        payload: ClipboardPayload?
    ) {
        inFlight = false
        onResult(payload)
        val nextCallback = pendingCallbacks.removeFirstOrNull()
        if (nextCallback != null) {
            mainHandler.post { readOnMain(appContext, nextCallback) }
        }
    }

    /**
     * 只为了拿窗口焦点：View 加到 WindowManager 之后，系统把窗口设为焦点窗口时会回调
     * [onWindowFocusChanged]——这是"窗口焦点已经生效"的最早通知，比等下一帧更早。
     */
    private class ProbeView(context: Context) : View(context) {
        var onWindowFocus: ((Boolean) -> Unit)? = null

        init {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            onWindowFocus?.invoke(hasFocus)
        }
    }
}
