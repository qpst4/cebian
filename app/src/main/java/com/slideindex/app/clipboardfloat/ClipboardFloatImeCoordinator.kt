package com.slideindex.app.clipboardfloat

import android.content.Context
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.ClipboardFloatService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import kotlinx.coroutines.flow.first

object ClipboardFloatImeCoordinator {
    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var showChip: Boolean = true

    @Volatile
    private var lastImeVisible: Boolean = false

    fun applySettings(settings: AppSettings) {
        enabled = settings.clipboardFloatEnabled
        showChip = settings.clipboardFloatShowChip
    }

    fun onWindowsChanged(serviceContext: Context) {
        if (!enabled) {
            if (lastImeVisible) {
                lastImeVisible = false
                ClipboardFloatLifecycle.hide(serviceContext)
            }
            return
        }
        val service = com.slideindex.app.service.SlideIndexAccessibilityService.accessibilityInstance()
            ?: return
        val imeBounds = ClipboardFloatImeDetector.detectImeBounds(service)
        if (imeBounds != null) {
            if (!lastImeVisible) {
                ClipboardFloatLifecycle.showForIme(
                    context = serviceContext,
                    imeTop = imeBounds.top,
                    showChip = showChip,
                )
            } else {
                ClipboardFloatService.updateImeTop(serviceContext, imeBounds.top)
            }
        } else if (lastImeVisible) {
            ClipboardFloatLifecycle.hide(serviceContext)
        }
        lastImeVisible = imeBounds != null
    }

    suspend fun syncFromSettings(context: Context, settingsRepository: SettingsRepository) {
        applySettings(settingsRepository.settings.first())
        if (!enabled) {
            ClipboardFloatLifecycle.hide(context)
            lastImeVisible = false
        }
    }
}
