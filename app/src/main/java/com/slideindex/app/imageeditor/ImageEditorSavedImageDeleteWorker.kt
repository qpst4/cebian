package com.slideindex.app.imageeditor

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ImageEditorSavedImageDeleteWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_IMAGE_URI) ?: return Result.failure()
        val uri = Uri.parse(uriString)
        val deleted = runCatching {
            applicationContext.contentResolver.delete(uri, null, null)
        }.getOrDefault(0)
        return if (deleted > 0) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val WORK_TAG = "image_editor_saved_delete"
    }
}
