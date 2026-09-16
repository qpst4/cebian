package com.slideindex.app.timeddnd

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TempDndRestoreWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val success = TempDndManager.restoreDnd(applicationContext)
        return if (success) Result.success() else Result.failure()
    }
}
