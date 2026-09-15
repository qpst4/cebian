package com.slideindex.app.imageeditor

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ImageEditorSavedImageDeleteScheduler {
    private const val PREFS_NAME = "slide_index_image_editor"
    private const val KEY_PENDING_DELETE_URI = "pending_auto_delete_image_uri"

    fun scheduleDeleteAfterMinutes(context: Context, uri: Uri, minutes: Long) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_DELETE_URI, uri.toString())
            .apply()
        val request = OneTimeWorkRequestBuilder<ImageEditorSavedImageDeleteWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setInputData(
                Data.Builder()
                    .putString(ImageEditorSavedImageDeleteWorker.KEY_IMAGE_URI, uri.toString())
                    .build(),
            )
            .addTag(ImageEditorSavedImageDeleteWorker.WORK_TAG)
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
    }
}
