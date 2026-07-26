package com.slideindex.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterRepository
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.otp.OtpAutoFillStatsRepository
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.service.ShareImageOcrHistoryRepository
import com.slideindex.app.settings.SensitiveBackupSections
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shell.ShellOutputHistoryRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.slideindex.app.settings.SettingsBackupPreview
import com.slideindex.app.gesture.GestureActionPermissionAuditor

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    private val otpRecordsRepository: OtpRecordsRepository,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationFilterRepository: NotificationFilterRepository,
    private val notificationFilterPreferences: NotificationFilterPreferences,
    private val clipboardHistoryRepository: ClipboardHistoryRepository,
    private val shareImageOcrHistoryRepository: ShareImageOcrHistoryRepository,
    private val shellOutputHistoryRepository: ShellOutputHistoryRepository,
    private val otpAutoFillStatsRepository: OtpAutoFillStatsRepository,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun exportSettings(
        includeSensitiveData: Boolean,
        uri: Uri,
    ) {
        viewModelScope.launch {
            runCatching {
                val sensitive = buildBackupSections(includeSensitiveData)
                appContext.contentResolver.openOutputStream(uri)?.use { output ->
                    settingsRepository.exportSettings(BuildConfig.VERSION_NAME, sensitive, output).getOrThrow()
                } ?: error("Unable to open output stream")
            }.fold(
                onSuccess = {
                    userMessageBus.showSuccess(
                        appContext.getString(R.string.settings_backup_export_success),
                    )
                },
                onFailure = {
                    userMessageBus.showError(
                        appContext.getString(R.string.settings_backup_export_failed),
                    )
                },
            )
        }
    }

    private suspend fun buildBackupSections(includeSensitiveData: Boolean): SensitiveBackupSections {
        val notificationFilterRulesJson = notificationFilterRepository.exportRawJson()
        val notificationFilterPreferencesJson = notificationFilterPreferences.exportRawJson()
        val otpAutoFillStatsJson = otpAutoFillStatsRepository.exportRawJson()

        if (!includeSensitiveData) {
            return SensitiveBackupSections(
                notificationFilterRulesJson = notificationFilterRulesJson,
                notificationFilterPreferencesJson = notificationFilterPreferencesJson,
                otpAutoFillStatsJson = otpAutoFillStatsJson,
            )
        }

        return SensitiveBackupSections(
            otpRecordsJson = otpRecordsRepository.exportRawJson(),
            notificationHistoryJson = notificationHistoryRepository.exportRawJson(),
            notificationFilterRulesJson = notificationFilterRulesJson,
            notificationFilterPreferencesJson = notificationFilterPreferencesJson,
            otpAutoFillStatsJson = otpAutoFillStatsJson,
            shellOutputHistoryJson = shellOutputHistoryRepository.exportRawJson(),
            includeDirectories = true,
        )
    }

    private val _importPreviewState = MutableStateFlow<SettingsBackupPreviewState?>(null)
    val importPreviewState: StateFlow<SettingsBackupPreviewState?> = _importPreviewState.asStateFlow()

    private val _navigateToMissingPermissions = MutableStateFlow(false)
    val navigateToMissingPermissions: StateFlow<Boolean> = _navigateToMissingPermissions.asStateFlow()

    fun consumeNavigateToMissingPermissions() {
        _navigateToMissingPermissions.value = false
    }

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    settingsRepository.previewImport(input).getOrThrow()
                } ?: error("Unable to read backup file")
            }.fold(
                onSuccess = { preview ->
                    _importPreviewState.value = SettingsBackupPreviewState(
                        uri = uri,
                        preview = preview
                    )
                },
                onFailure = {
                    userMessageBus.showError(
                        appContext.getString(R.string.settings_backup_import_failed)
                    )
                }
            )
        }
    }

    fun dismissPreview() {
        _importPreviewState.value = null
    }

    fun confirmImport(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    settingsRepository.importSettings(input).getOrThrow()
                } ?: error("Unable to open input stream")
            }.fold(
                onSuccess = { result ->
                    applyImportedSections(result)
                    userMessageBus.showSuccess(
                        appContext.resources.getQuantityString(
                            R.plurals.settings_backup_import_success,
                            result.preferencesImported,
                            result.preferencesImported,
                        ),
                    )
                    dismissPreview()
                    if (GestureActionPermissionAuditor.auditMissingPermissions(
                            appContext,
                            settingsRepository.readSnapshot(),
                        ).isNotEmpty()
                    ) {
                        _navigateToMissingPermissions.value = true
                    }
                },
                onFailure = {
                    userMessageBus.showError(
                        appContext.getString(R.string.settings_backup_import_failed),
                    )
                    dismissPreview()
                }
            )
        }
    }

    private suspend fun applyImportedSections(result: com.slideindex.app.settings.SettingsBackupImportResult) {
        val sections = result.sensitive
        sections.otpRecordsJson?.let { otpJson ->
            otpRecordsRepository.importRawJson(otpJson)
        }
        sections.notificationHistoryJson?.let { historyJson ->
            notificationHistoryRepository.importRawJson(historyJson)
        }
        sections.notificationFilterRulesJson?.let { rulesJson ->
            notificationFilterRepository.importRawJson(rulesJson, replace = true)
        }
        sections.notificationFilterPreferencesJson?.let { prefsJson ->
            notificationFilterPreferences.importRawJson(prefsJson)
        }
        sections.otpAutoFillStatsJson?.let { statsJson ->
            otpAutoFillStatsRepository.importRawJson(statsJson)
        }
        sections.shellOutputHistoryJson?.let { shellJson ->
            shellOutputHistoryRepository.importRawJson(shellJson)
        }
        if (result.importedClipboardDirectory) {
            clipboardHistoryRepository.reloadFromDisk()
        }
        if (result.importedShareImageOcrHistoryDirectory) {
            shareImageOcrHistoryRepository.reloadFromDisk()
        }
    }
}

data class SettingsBackupPreviewState(
    val uri: Uri,
    val preview: SettingsBackupPreview,
)
