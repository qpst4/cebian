package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.service.HistoryFloatLifecycle
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.HistoryFloatHandleWidth
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StashClipboardSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    val clipboardHistoryRepository: ClipboardHistoryRepository,
    val stashRepository: StashRepository,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setClipboardBackgroundMonitoring(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboardBackgroundMonitoring = enabled) },
    ) {
        settingsRepository.setClipboardBackgroundMonitoring(enabled).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardBackgroundMonitoringMode(mode: ClipboardMonitoringMode) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboardBackgroundMonitoringMode = mode) },
    ) {
        settingsRepository.setClipboardBackgroundMonitoringMode(mode).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardScreenshotMonitoring(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboardScreenshotMonitoring = enabled) },
    ) {
        settingsRepository.setClipboardScreenshotMonitoring(enabled).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardHistoryMaxEntries(maxEntries: Int) = launchSettingsWrite {
        settingsRepository.setClipboardHistoryMaxEntries(maxEntries).also { result ->
            if (result.isSuccess) {
                clipboardHistoryRepository.trimToConfiguredMax()
            }
        }
    }

    fun setClipboardHistoryFloatEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboardHistoryFloatEnabled = enabled) },
    ) {
        settingsRepository.setClipboardHistoryFloatEnabled(enabled).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun setClipboardHistoryFloatLockPosition(lock: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboardHistoryFloatLockPosition = lock) },
    ) {
        settingsRepository.setClipboardHistoryFloatLockPosition(lock).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.applyRuntimeConfig(
                    context = appContext,
                    handleWidthDp = settingsRepository.readSnapshot().clipboardHistoryFloatHandleWidthDp,
                    lockPosition = lock,
                )
            }
        }
    }

    fun setClipboardHistoryFloatHandleWidthDp(widthDp: Int) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboardHistoryFloatHandleWidthDp = HistoryFloatHandleWidth.coerce(widthDp)) },
    ) {
        settingsRepository.setClipboardHistoryFloatHandleWidthDp(widthDp).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.applyRuntimeConfig(
                    context = appContext,
                    handleWidthDp = widthDp,
                    lockPosition = settingsRepository.readSnapshot().clipboardHistoryFloatLockPosition,
                )
            }
        }
    }

    fun syncHistoryFloatFromSettings() {
        viewModelScope.launch {
            HistoryFloatLifecycle.syncFromSettings(appContext, settingsRepository)
        }
    }

    fun clearClipboardHistory() = launchRepositoryWrite {
        runCatching { clipboardHistoryRepository.clearAll() }
    }

    fun clearStash() = launchRepositoryWrite {
        runCatching { stashRepository.clearAll() }
    }

    private fun restartMonitoring() {
        clipboardHistoryRepository.restartClipboardMonitoringFromSettings()
        SlideIndexAccessibilityService.accessibilityInstance()?.syncScreenshotMonitoring()
    }
}
