package com.slideindex.app.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * 后台检查应用更新。由 WorkManager 周期调度，不依赖手势服务。
 * 发现新版本且应用不在前台时发送通知。
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            UpdateWorkerEntryPoint::class.java,
        )
        val preferencesStore = entryPoint.updatePreferencesStore()
        val updateRepository = entryPoint.updateRepository()

        val prefs = preferencesStore.read()
        if (!prefs.autoCheckUpdate || !updateRepository.shouldCheck()) {
            return Result.success()
        }

        return when (val result = updateRepository.checkAndCache(force = false)) {
            is UpdateRepository.CheckResult.NewVersion -> {
                val ignored = preferencesStore.read().ignoredUpdateVersion
                val version = result.state.latestVersion
                if (
                    version.isNotBlank() &&
                    version != ignored &&
                    !UpdateAppForeground.isInForeground
                ) {
                    UpdateNotifications.showNewVersion(applicationContext, version)
                }
                Result.success()
            }
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "update_check"
    }
}
