package com.slideindex.app.service

/**
 * 无障碍重绑抖动的节流器。
 *
 * 看门狗每 25s 轮询一次；「移除条目再写回」这种抖动如果每次都做，会不停打断系统还没完成的
 * 重绑。这里保证同一进程内两次抖动至少间隔 [minIntervalMs]。
 */
internal class NudgeThrottle(private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS) {

    @Volatile
    private var lastAtMs: Long = NEVER

    /** @return true 表示这次允许抖动，并已记录时间。 */
    fun beginIfDue(nowMs: Long): Boolean {
        val last = lastAtMs
        if (last != NEVER && nowMs - last < minIntervalMs) return false
        lastAtMs = nowMs
        return true
    }

    fun reset() {
        lastAtMs = NEVER
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 60_000L
        private const val NEVER = Long.MIN_VALUE
    }
}
