package com.slideindex.app.clipboardfloat

import android.content.Context
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.ClipboardFloatService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import kotlinx.coroutines.flow.first

object ClipboardFloatImeCoordinator {
    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var showChip: Boolean = true

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var lastImeVisible: Boolean = false

    fun applySettings(settings: AppSettings) {
        enabled = settings.clipboardFloatEnabled
        showChip = settings.clipboardFloatShowChip
        blockedPackages = settings.clipboardFloatBlockedPackages
    }

    fun onWindowsChanged(serviceContext: Context) {
        if (!enabled) {
            if (lastImeVisible) {
                lastImeVisible = false
                ClipboardFloatLifecycle.hide(serviceContext)
            }
            return
        }
        val service = SlideIndexAccessibilityService.accessibilityInstance()
            ?: return
        if (isForegroundBlocked(service)) {
            if (lastImeVisible) {
                lastImeVisible = false
                ClipboardFloatLifecycle.hide(serviceContext)
            }
            return
        }
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
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (!enabled || (service != null && isForegroundBlocked(service))) {
            ClipboardFloatLifecycle.hide(context)
            lastImeVisible = false
        }
    }

    private fun isForegroundBlocked(service: SlideIndexAccessibilityService): Boolean {
        val foregroundPackage = ClipboardFloatForegroundResolver.resolveHostPackage(service)
            ?: return false
        return foregroundPackage in blockedPackages
    }
}
