package com.slideindex.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.settings.SensitiveBackupSections
import com.slideindex.app.settings.SettingsRepository
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
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun exportSettings(
        includeSensitiveData: Boolean,
        uri: Uri,
    ) {
        viewModelScope.launch {
            val sensitive = if (includeSensitiveData) {
                SensitiveBackupSections(
                    otpRecordsJson = otpRecordsRepository.exportRawJson(),
                    notificationHistoryJson = notificationHistoryRepository.exportRawJson(),
                ).takeIf { it.hasAny }
            } else {
                null
            }
            
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { output ->
                    settingsRepository.exportSettings(BuildConfig.VERSION_NAME, sensitive, output).getOrThrow()
                } ?: error("Unable to open output stream")
            }.onFailure {
                userMessageBus.showError(
                    appContext.getString(R.string.settings_backup_export_failed),
                )
            }
        }
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
                    result.sensitive.otpRecordsJson?.let { otpJson ->
                        otpRecordsRepository.importRawJson(otpJson)
                    }
                    result.sensitive.notificationHistoryJson?.let { historyJson ->
                        notificationHistoryRepository.importRawJson(historyJson)
                    }
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
}

data class SettingsBackupPreviewState(
    val uri: Uri,
    val preview: SettingsBackupPreview,
)
