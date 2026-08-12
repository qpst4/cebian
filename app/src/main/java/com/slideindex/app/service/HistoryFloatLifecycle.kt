package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.settings.HistoryFloatHandleWidth
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.flow.first

/** Starts or stops [HistoryFloatService] based on clipboard history float settings. */
object HistoryFloatLifecycle {
    suspend fun syncFromSettings(context: Context, settingsRepository: SettingsRepository) {
        val appContext = context.applicationContext
        val settings = settingsRepository.settings.first()
        if (settings.clipboardHistoryFloatEnabled && PermissionHelper.canDrawOverlays(appContext)) {
            start(appContext, settings.clipboardHistoryFloatHandleWidthDp, settings.clipboardHistoryFloatLockPosition)
        } else {
            stop(appContext)
        }
    }

    fun start(
        context: Context,
        handleWidthDp: Int = HistoryFloatHandleWidth.DEFAULT_DP,
        lockPosition: Boolean = true,
    ) {
        if (!PermissionHelper.canDrawOverlays(context)) return
        val appContext = context.applicationContext
        appContext.startService(
            Intent(appContext, HistoryFloatService::class.java).apply {
                putExtra(HistoryFloatService.EXTRA_HANDLE_WIDTH_DP, HistoryFloatHandleWidth.coerce(handleWidthDp))
                putExtra(HistoryFloatService.EXTRA_LOCK_POSITION, lockPosition)
            },
        )
    }

    fun stop(context: Context) {
        context.applicationContext.stopService(Intent(context.applicationContext, HistoryFloatService::class.java))
    }

    fun applyRuntimeConfig(
        context: Context,
        handleWidthDp: Int,
        lockPosition: Boolean,
    ) {
        val appContext = context.applicationContext
        appContext.startService(
            Intent(appContext, HistoryFloatService::class.java).apply {
                action = HistoryFloatService.ACTION_SET_HANDLE_WIDTH
                putExtra(HistoryFloatService.EXTRA_HANDLE_WIDTH_DP, HistoryFloatHandleWidth.coerce(handleWidthDp))
            },
        )
        appContext.startService(
            Intent(appContext, HistoryFloatService::class.java).apply {
                action = HistoryFloatService.ACTION_LOCK_POSITION
                putExtra(HistoryFloatService.EXTRA_LOCK_POSITION, lockPosition)
            },
        )
    }
}
