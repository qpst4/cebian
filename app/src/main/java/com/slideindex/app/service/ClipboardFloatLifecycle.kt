package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import com.slideindex.app.clipboardfloat.ClipboardFloatImeCoordinator
import com.slideindex.app.settings.SettingsRepository
import kotlinx.coroutines.flow.first

object ClipboardFloatLifecycle {
    suspend fun syncFromSettings(context: Context, settingsRepository: SettingsRepository) {
        ClipboardFloatImeCoordinator.syncFromSettings(context.applicationContext, settingsRepository)
    }

    fun showForIme(context: Context, imeTop: Int, showChip: Boolean) {
        val appContext = context.applicationContext
        appContext.startService(
            Intent(appContext, ClipboardFloatService::class.java).apply {
                action = ClipboardFloatService.ACTION_SHOW_IME
                putExtra(ClipboardFloatService.EXTRA_IME_TOP, imeTop)
                putExtra(ClipboardFloatService.EXTRA_SHOW_CHIP, showChip)
            },
        )
    }

    fun showExpanded(context: Context) {
        val appContext = context.applicationContext
        appContext.startService(
            Intent(appContext, ClipboardFloatService::class.java).apply {
                action = ClipboardFloatService.ACTION_SHOW_EXPANDED
            },
        )
    }

    fun hide(context: Context) {
        val appContext = context.applicationContext
        appContext.startService(
            Intent(appContext, ClipboardFloatService::class.java).apply {
                action = ClipboardFloatService.ACTION_HIDE
            },
        )
    }
}
