package com.slideindex.app.settings

import kotlinx.serialization.Serializable

@Serializable
data class SettingsBackupDocument(
    val formatVersion: Int,
    val exportedAtEpochMs: Long,
    val appVersionName: String,
    val preferences: List<SettingsPreferenceEntry>,
    val otpRecordsJson: String? = null,
    val notificationHistoryJson: String? = null,
    val notificationFilterRulesJson: String? = null,
    val notificationFilterPreferencesJson: String? = null,
    val otpAutoFillStatsJson: String? = null,
    val shellOutputHistoryJson: String? = null,
    val searchPanelHistoryJson: String? = null,
)

@Serializable
data class SensitiveBackupSections(
    val otpRecordsJson: String? = null,
    val notificationHistoryJson: String? = null,
    val notificationFilterRulesJson: String? = null,
    val notificationFilterPreferencesJson: String? = null,
    val otpAutoFillStatsJson: String? = null,
    val shellOutputHistoryJson: String? = null,
    val searchPanelHistoryJson: String? = null,
    val includeDirectories: Boolean = false,
) {
    val hasAny: Boolean
        get() = includeDirectories ||
            listOf(
                otpRecordsJson,
                notificationHistoryJson,
                notificationFilterRulesJson,
                notificationFilterPreferencesJson,
                otpAutoFillStatsJson,
                shellOutputHistoryJson,
                searchPanelHistoryJson,
            ).any { !it.isNullOrBlank() }
}

data class SettingsBackupImportResult(
    val preferencesImported: Int,
    val sensitive: SensitiveBackupSections,
    val importedClipboardDirectory: Boolean = false,
    val importedShareImageOcrHistoryDirectory: Boolean = false,
)

@Serializable
data class SettingsPreferenceEntry(
    val key: String,
    val type: String,
    val value: String,
    val values: List<String> = emptyList(),
)
