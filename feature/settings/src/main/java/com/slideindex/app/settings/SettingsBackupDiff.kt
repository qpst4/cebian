package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences

data class SettingsBackupImportDiff(
    val overwrittenKeys: List<String>,
    val newKeys: List<String>,
) {
    val overwrittenDomainCounts: Map<SettingsDomain, Int> =
        overwrittenKeys.groupingBy { mapPreferenceKeyToDomain(it) }.eachCount()

    val newDomainCounts: Map<SettingsDomain, Int> =
        newKeys.groupingBy { mapPreferenceKeyToDomain(it) }.eachCount()

    val hasChanges: Boolean get() = overwrittenKeys.isNotEmpty() || newKeys.isNotEmpty()
}

fun computeSettingsBackupImportDiff(
    currentPrefs: Preferences,
    backupDocument: SettingsBackupDocument,
): SettingsBackupImportDiff {
    val onboardingKey = SettingsPreferenceKeys.ONBOARDING_COMPLETED.name
    val currentKeys = currentPrefs.asMap().keys
        .map { it.name }
        .filter { it != onboardingKey }
        .toSet()
    val backupKeys = backupDocument.preferences
        .map { it.key }
        .filter { it != onboardingKey }
        .toSet()
    return SettingsBackupImportDiff(
        overwrittenKeys = backupKeys.intersect(currentKeys).sorted(),
        newKeys = (backupKeys - currentKeys).sorted(),
    )
}
