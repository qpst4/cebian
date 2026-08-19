package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.HistoryFloatLifecycle
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardFloatEntryClickAction
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
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardBackgroundMonitoring = enabled)) },
    ) {
        settingsRepository.setClipboardBackgroundMonitoring(enabled).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardBackgroundMonitoringMode(mode: ClipboardMonitoringMode) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardBackgroundMonitoringMode = mode)) },
    ) {
        settingsRepository.setClipboardBackgroundMonitoringMode(mode).also { result ->
            if (result.isSuccess) {
                restartMonitoring()
            }
        }
    }

    fun setClipboardScreenshotMonitoring(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardScreenshotMonitoring = enabled)) },
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
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardHistoryFloatEnabled = enabled)) },
    ) {
        settingsRepository.setClipboardHistoryFloatEnabled(enabled).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun setClipboardHistoryFloatLockPosition(lock: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardHistoryFloatLockPosition = lock)) },
    ) {
        settingsRepository.setClipboardHistoryFloatLockPosition(lock).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.applyRuntimeConfig(
                    context = appContext,
                    handleWidthDp = settingsRepository.readSnapshot().clipboardHistoryFloatHandleWidthDp,
                    lockPosition = lock,
                    landscapeEnabled = settingsRepository.readSnapshot().clipboardHistoryFloatEnabledLandscape,
                )
            }
        }
    }

    fun setClipboardHistoryFloatHandleWidthDp(widthDp: Int) = launchOptimisticSettingsWrite(
        optimisticUpdate = {
            it.copy(
                clipboard = it.clipboard.copy(
                    clipboardHistoryFloatHandleWidthDp = HistoryFloatHandleWidth.coerce(widthDp),
                ),
            )
        },
    ) {
        settingsRepository.setClipboardHistoryFloatHandleWidthDp(widthDp).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.applyRuntimeConfig(
                    context = appContext,
                    handleWidthDp = widthDp,
                    lockPosition = settingsRepository.readSnapshot().clipboardHistoryFloatLockPosition,
                    landscapeEnabled = settingsRepository.readSnapshot().clipboardHistoryFloatEnabledLandscape,
                )
            }
        }
    }

    fun setClipboardHistoryFloatEnabledLandscape(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardHistoryFloatEnabledLandscape = enabled)) },
    ) {
        settingsRepository.setClipboardHistoryFloatEnabledLandscape(enabled).also { result ->
            if (result.isSuccess) {
                HistoryFloatLifecycle.applyRuntimeConfig(
                    context = appContext,
                    handleWidthDp = settingsRepository.readSnapshot().clipboardHistoryFloatHandleWidthDp,
                    lockPosition = settingsRepository.readSnapshot().clipboardHistoryFloatLockPosition,
                    landscapeEnabled = enabled,
                )
            }
        }
    }

    fun setStashPanelBackgroundBlurEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(stashPanelBackgroundBlurEnabled = enabled)) },
    ) {
        settingsRepository.setStashPanelBackgroundBlurEnabled(enabled)
    }

    fun setStashPanelBackgroundBlurRadiusDp(value: Int) = launchOptimisticSettingsWrite(
        optimisticUpdate = {
            it.copy(
                clipboard = it.clipboard.copy(
                    stashPanelBackgroundBlurRadiusDp = value.coerceIn(
                        AppSettings.STASH_PANEL_BLUR_RADIUS_MIN_DP,
                        AppSettings.STASH_PANEL_BLUR_RADIUS_MAX_DP,
                    ),
                ),
            )
        },
    ) {
        settingsRepository.setStashPanelBackgroundBlurRadiusDp(value)
    }

    fun syncHistoryFloatFromSettings() {
        viewModelScope.launch {
            HistoryFloatLifecycle.syncFromSettings(appContext, settingsRepository)
        }
    }

    fun setClipboardFloatEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatEnabled = enabled)) },
    ) {
        settingsRepository.setClipboardFloatEnabled(enabled).also { result ->
            if (result.isSuccess) {
                ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun setClipboardFloatShowChip(showChip: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatShowChip = showChip)) },
    ) {
        settingsRepository.setClipboardFloatShowChip(showChip).also { result ->
            if (result.isSuccess) {
                ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun setClipboardFloatPinPosition(pin: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatPanelPinPosition = pin)) },
    ) {
        settingsRepository.setClipboardFloatPinPosition(pin)
    }

    fun setClipboardFloatEntryClickAction(action: ClipboardFloatEntryClickAction) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatEntryClickAction = action)) },
    ) {
        settingsRepository.setClipboardFloatEntryClickAction(action)
    }

    fun setClipboardFloatListStyle(style: com.slideindex.app.settings.ClipboardFloatListStyle) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatListStyleId = style.id)) },
    ) {
        settingsRepository.setClipboardFloatListStyle(style)
    }

    fun setClipboardFloatPasteHapticEnabled(enabled: Boolean) = launchOptimisticSettingsWrite(
        optimisticUpdate = { it.copy(clipboard = it.clipboard.copy(clipboardFloatPasteHapticEnabled = enabled)) },
    ) {
        settingsRepository.setClipboardFloatPasteHapticEnabled(enabled)
    }

    fun addClipboardFloatBlockedPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.addClipboardFloatBlockedPackage(packageName).also { result ->
            if (result.isSuccess) {
                ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun removeClipboardFloatBlockedPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.removeClipboardFloatBlockedPackage(packageName).also { result ->
            if (result.isSuccess) {
                ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
            }
        }
    }

    fun resetClipboardFloatLayout() = launchSettingsWrite {
        settingsRepository.resetClipboardFloatGeometry()
    }

    fun syncClipboardFloatFromSettings() {
        viewModelScope.launch {
            ClipboardFloatLifecycle.syncFromSettings(appContext, settingsRepository)
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
