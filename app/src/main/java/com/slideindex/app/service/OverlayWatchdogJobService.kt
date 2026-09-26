package com.slideindex.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.slideindex.app.di.AppGraphEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 主进程里的常驻巡检（每 15 分钟一次，持久化，开机自恢复）。
 *
 * 存在的意义只有一个：**让「用户不开 App」时边缘手势也能自己回来**。
 * 具体动作见 [OverlayGuard]。
 */
class OverlayWatchdogJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters?): Boolean {
        val appContext = applicationContext
        scope.launch {
            runCatching {
                val deps = EntryPointAccessors.fromApplication(
                    appContext,
                    AppGraphEntryPoint::class.java,
                ).dependencies()
                val result = OverlayGuard.run(appContext, deps.settingsRepository.readSnapshot())
                // 用 warn 级别打点：部分 ROM 会丢第三方应用的 INFO 日志，看门狗是最需要留痕的一环。
                Log.w(TAG, "watchdog result=$result")
            }.onFailure { Log.w(TAG, "watchdog failed", it) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // 被打断也没关系：下一次周期巡检会再来一遍。
        return false
    }

    companion object {
        private const val TAG = "OverlayWatchdog"
        private const val JOB_ID = 0x5101
        private const val PERIOD_MS = 15 * 60 * 1000L

        /** 幂等：已排过就跳过（开机、覆盖安装、App 启动都会调）。 */
        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: return
            val component = ComponentName(appContext, OverlayWatchdogJobService::class.java)
            runCatching {
                if (scheduler.getPendingJob(JOB_ID) != null) return
                val job = JobInfo.Builder(JOB_ID, component)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build()
                val result = scheduler.schedule(job)
                if (result != JobScheduler.RESULT_SUCCESS) {
                    Log.w(TAG, "schedule rejected: $result")
                }
            }.onFailure { Log.w(TAG, "schedule failed", it) }
        }
    }
}
