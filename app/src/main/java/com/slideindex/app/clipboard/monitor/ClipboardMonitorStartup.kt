package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Gates clipboard FGS startup until the main process Application has finished onCreate. */
internal object ClipboardMonitorStartup {
    @Volatile
    var applicationReady: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    fun runOnMainWhenIdle(block: () -> Unit) {
        runOnMainWhenReady {
            postWhenIdle(block)
        }
    }

    /**
     * 主线程「空闲且不忙」时才启动前台服务。
     *
     * `startForegroundService()` 之后，系统要求前台服务在 5~10 秒内 `startForeground()`；
     * 这条 service 创建消息排在主线程队列里，如果此刻队列里压着启动期重活（进程冷启动、
     * 无障碍事件批量回调、设置读写等），窗口就会被吃掉 →
     * `ForegroundServiceDidNotStartInTimeException` → **整个 `:overlay` 进程被系统干掉**
     * （用户看到悬浮球消失、手势全失效，要重新打开 App 才恢复）。
     *
     * 这里先等空闲，再探一次队列：投递的探测任务如果等待超过 [CALM_LATENCY_MS] 就说明主线程还忙着，
     * 稍后重试，直到真的闲下来再发起启动。
     */
    fun runOnMainWhenCalm(block: () -> Unit) {
        runOnMainWhenReady {
            postWhenIdle {
                measureCalm(block, attemptsLeft = CALM_ATTEMPTS)
            }
        }
    }

    private fun measureCalm(block: () -> Unit, attemptsLeft: Int) {
        val postedAt = SystemClock.uptimeMillis()
        mainHandler.post {
            val latencyMs = SystemClock.uptimeMillis() - postedAt
            if (latencyMs <= CALM_LATENCY_MS) {
                block()
            } else if (attemptsLeft <= 1) {
                // 重试用尽：之前这里是**静默放弃**（不启动、不报错），切通道时主线程正忙就会把重启丢掉，
                // 表现成"点了没反应、要再点一次"。现在先留下证据，再退化成慢速重试而不是丢掉。
                Log.w(TAG, "main thread busy for ${latencyMs}ms, giving up calm wait and starting anyway")
                block()
            } else {
                mainHandler.postDelayed({ measureCalm(block, attemptsLeft - 1) }, CALM_RETRY_MS)
            }
        }
    }

    private const val TAG = "ClipboardMonitorStartup"

    private fun postWhenIdle(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Looper.myQueue().addIdleHandler {
                block()
                false
            }
        } else {
            mainHandler.post { postWhenIdle(block) }
        }
    }

    fun runOnMainWhenReady(block: () -> Unit) {
        if (applicationReady) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                mainHandler.post(block)
            }
            return
        }
        mainHandler.post {
            if (applicationReady) {
                block()
            } else {
                mainHandler.postDelayed({ runOnMainWhenReady(block) }, RETRY_MS)
            }
        }
    }

    private const val RETRY_MS = 200L
    private const val CALM_LATENCY_MS = 250L
    private const val CALM_RETRY_MS = 400L
    private const val CALM_ATTEMPTS = 8
}
