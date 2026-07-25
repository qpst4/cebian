package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.clipboard.ClipboardWhitelistBridge
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.ClipboardMonitoringPath
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
                syncClipboardWhitelist()
                restartMonitoring()
            }
        }
    }

    fun setClipboardBackgroundMonitoringPath(path: ClipboardMonitoringPath) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboardBackgroundMonitoringPath = path) },
    ) {
        settingsRepository.setClipboardBackgroundMonitoringPath(path).also { result ->
            if (result.isSuccess) {
                syncClipboardWhitelist()
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

    fun setClipboardLsposedExtraWhitelist(packages: Set<String>) = launchSettingsWrite {
        settingsRepository.setClipboardLsposedExtraWhitelist(packages).also { result ->
            if (result.isSuccess) {
                syncClipboardWhitelist()
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

    fun clearClipboardHistory() = launchRepositoryWrite {
        runCatching { clipboardHistoryRepository.clearAll() }
    }

    fun clearStash() = launchRepositoryWrite {
        runCatching { stashRepository.clearAll() }
    }

    fun syncClipboardWhitelist() {
        ClipboardWhitelistBridge.sync(settingsRepository.readSnapshot())
    }

    private fun restartMonitoring() {
        SlideIndexAccessibilityService.accessibilityInstance()?.syncMonitoring()
    }
}
