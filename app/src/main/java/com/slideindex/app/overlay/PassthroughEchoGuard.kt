package com.slideindex.app.overlay

import android.os.SystemClock
import kotlin.math.hypot

/**
 * 「点击穿透」放行的注入回声抑制。
 *
 * [OverlayPassthrough] 的流程是「隐藏触发器 → 注入一次点击 → 恢复触发器」。如果触发器比这次注入
 * 更早恢复可用（主线程卡顿、窗口重排都会造成），注入的这一下会被自己的触发器接住，于是又触发
 * 同一个放行动作：隐藏/恢复反复进行，表现就是触发钮不停抽搐，日志里同一坐标反复出现
 * `dispatchTap start`。
 *
 * 这里按「注入坐标 + 时间窗」挡掉这种回声：注入前 [arm]，随后落在同一坐标附近的放行请求直接丢弃。
 * 用户真正的新触摸一般不在同一个点上，不受影响。
 *
 * 与 [PointerTapEchoGuard] 的分工：那个守卫吞咽的是指针注入事件本身，这个守卫拦的是「放行动作
 * 被回声再次触发」。
 */
internal class PassthroughEchoGuard {
    private var armed = false
    private var injectX = 0f
    private var injectY = 0f
    private var echoSlopPx = DEFAULT_ECHO_SLOP_PX
    private var absorbUntilMs = 0L

    /** 记住这一次注入的位置，并打开回声时间窗。 */
    fun arm(
        rawX: Float,
        rawY: Float,
        nowMs: Long = SystemClock.elapsedRealtime(),
        durationMs: Long = ABSORB_MS,
        echoSlopPx: Float = DEFAULT_ECHO_SLOP_PX
    ) {
        armed = true
        injectX = rawX
        injectY = rawY
        this.echoSlopPx = echoSlopPx.coerceAtLeast(MIN_ECHO_SLOP_PX)
        absorbUntilMs = nowMs + durationMs.coerceAtLeast(0L)
    }

    fun reset() {
        armed = false
        absorbUntilMs = 0L
    }

    fun disarmIfExpired(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (armed && nowMs >= absorbUntilMs) {
            armed = false
        }
    }

    /**
     * @return true 表示这次放行请求是上一次注入的回声，必须丢弃。
     */
    fun isEcho(
        rawX: Float,
        rawY: Float,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        disarmIfExpired(nowMs)
        if (!armed) return false
        val distance = hypot((rawX - injectX).toDouble(), (rawY - injectY).toDouble()).toFloat()
        return distance <= echoSlopPx
    }

    companion object {
        /**
         * 覆盖「注入 → 触发器恢复 → 回声重新派发」的整段窗口。
         *
         * 实测回声在注入后约 220ms 到达；取 400ms 既够挡住回声，又不会明显影响同点连点。
         */
        const val ABSORB_MS = 400L
        const val DEFAULT_ECHO_SLOP_PX = 32f
        private const val MIN_ECHO_SLOP_PX = 8f
    }
}
