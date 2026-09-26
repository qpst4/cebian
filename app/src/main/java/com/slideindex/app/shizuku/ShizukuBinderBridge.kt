package com.slideindex.app.shizuku

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.util.AppProcess

/**
 * 非主进程获取 Shizuku binder 的唯一入口（惰性获取）。
 *
 * 背景（真机实测的根因，2026-09）：
 * Shizuku 的 binder 只在**声明了 ShizukuProvider 的进程（主进程）**里。其它进程要用
 * Shizuku，必须通过 provider 让主进程把 binder 递过来。问题在于"取 binder"这个动作会
 * **顺带把主进程拉起来**，并让当前进程成为"依赖主进程里的 provider"的进程：
 *
 * ```
 * 23:32:09.277 Start proc 24559:com.slideindex.app:overlay for service MediaNotificationListener
 * 23:32:09.481 Start proc 24589:com.slideindex.app for content provider rikka.shizuku.ShizukuProvider
 * 23:32:19.484 Process 24589:com.slideindex.app failed to attach      // 覆盖安装后主进程启动慢
 * 23:32:19.488 Killing 24559:com.slideindex.app:overlay (adj 0): depends on provider
 *               com.slideindex.app/rikka.shizuku.ShizukuProvider in dying proc
 * ```
 *
 * 即：覆盖安装后 :overlay 一起来就去取 Shizuku binder → 顺手把主进程拉起（安装刚结束时
 * 主进程启动最慢，实测 attach 超时 10s 被判死）→ :overlay 作为"依赖方"被系统**连带杀掉**
 * → 悬浮球消失、要等系统重绑无障碍才回来（用户反馈的"安装后要等近一分钟"）。
 *
 * 所以策略改成两层：
 * 1. 进程启动期**不主动取**（[scheduleDeferredAcquire]），且只有主进程**已经活着**时才补一次；
 * 2. 真正要用 Shizuku 的调用点再惰性取（[ensure]），保证功能不受影响。
 */
object ShizukuBinderBridge {

    private const val TAG = "ShizukuBinderBridge"

    /** 进程起来后隔多久做一次"非侵入式"的补取尝试。 */
    const val DEFERRED_ACQUIRE_DELAY_MS = 20_000L

    @Volatile
    private var acquired = false

    @Volatile
    private var deferredScheduled = false

    /**
     * 只有这两条链路真的用 Shizuku：
     * - `:overlay`：任务切换器 / shell 命令面板 / 冻结等；
     * - `:clipboard`：系统隐藏 API 剪贴板监听。
     * `:engine`（OCR）与 Shizuku 用户服务进程都不需要，避免无谓地把主进程拉起来。
     */
    private fun needsBinder(): Boolean =
        !AppProcess.isMain && (AppProcess.isOverlay || AppProcess.isClipboardMonitor)

    /** 主进程此刻是否活着（用于判断"去取 binder 会不会顺带拉起主进程"）。 */
    fun isMainProcessAlive(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return@runCatching false
        val selfPackage = context.packageName
        manager.runningAppProcesses?.any { it.processName == selfPackage } ?: false
    }.getOrDefault(false)

    /**
     * 立即取 binder（在真正要用 Shizuku 的调用点之前调）。幂等：取到过就不再调用 provider。
     * 主进程 / 不需要 Shizuku 的进程直接返回。
     */
    fun ensure(context: Context) {
        if (!needsBinder() || acquired) return
        synchronized(this) {
            if (acquired) return
            runCatching {
                rikka.shizuku.ShizukuProvider.enableMultiProcessSupport(false)
                rikka.shizuku.ShizukuProvider.requestBinderForNonProviderProcess(
                    context.applicationContext
                )
            }.onSuccess {
                acquired = true
            }.onFailure {
                Log.w(TAG, "request shizuku binder failed: ${it.message}")
            }
        }
    }

    /**
     * 进程启动后延后一次补取。**只有主进程已经活着才取**：
     * 主进程正在启动/被杀时去取，会让本进程被系统连带杀掉（见类注释），得不偿失。
     * 之后用户真正用到 Shizuku 时，[ensure] 会补上。
     */
    fun scheduleDeferredAcquire(context: Context) {
        if (!needsBinder() || deferredScheduled) return
        deferredScheduled = true
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isMainProcessAlive(appContext)) {
                Log.i(TAG, "deferred shizuku acquire skipped: main process not running")
                return@postDelayed
            }
            ensure(appContext)
        }, DEFERRED_ACQUIRE_DELAY_MS)
    }
}
