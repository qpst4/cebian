@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixScrollableConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.viewmodel.SettingsBackupPreviewState
import com.slideindex.app.settings.SettingsDomain
import com.slideindex.app.settings.SettingsBackupImportDiff
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(
    onBack: () -> Unit,
    onExport: (includeSensitiveData: Boolean, uri: android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    importPreviewState: SettingsBackupPreviewState?,
    onDismissPreview: () -> Unit,
    onConfirmImport: (android.net.Uri) -> Unit,
    missingPermissionCount: Int,
    onOpenMissingPermissions: () -> Unit,
) {
    val resources = LocalResources.current
    var includeSensitiveData by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onExport(includeSensitiveData, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImport(uri)
        }
    }

    val actionsSectionTitle = stringResource(R.string.settings_backup_section_actions)
    val backupHint = stringResource(R.string.settings_backup_hint)
    val permissionsSectionTitle = stringResource(R.string.settings_backup_section_permissions)

    SettingsScreenScaffold(
        title = stringResource(R.string.settings_backup_title),
        subtitle = stringResource(R.string.settings_backup_subtitle),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "backup_actions_section", title = actionsSectionTitle)
        settingsLazyHint(key = "backup_hint", text = backupHint)
        groupedCardItems(
            keyPrefix = "backup_sensitive",
            items = buildList {
                add(
                    settingsCardScopeItem("include-sensitive") {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_backup_include_sensitive),
                            subtitle = stringResource(R.string.settings_backup_sensitive_hint),
                            checked = includeSensitiveData,
                            enabled = true,
                            onCheckedChange = { includeSensitiveData = it },
                        )
                    },
                )
            },
        )

        LazySettingsItem(key = "backup-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val defaultName = resources.getString(
                            R.string.settings_backup_default_filename,
                            System.currentTimeMillis(),
                        )
                        exportLauncher.launch(defaultName)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.cd_export_settings))
                    Text(
                        text = stringResource(R.string.settings_backup_export),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "multipart/x-zip"),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = stringResource(R.string.cd_import_settings))
                    Text(
                        text = stringResource(R.string.settings_backup_import),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        if (missingPermissionCount > 0) {
            settingsLazySmallTitle(
                key = "backup_permissions_section",
                title = permissionsSectionTitle,
                sectionTop = true,
            )
            groupedCardItems(
                keyPrefix = "backup_permissions",
                items = buildList {
                    add(
                        settingsCardScopeItem("missing-permissions") {
                            MissingPermissionsEntryCard(
                                missingCount = missingPermissionCount,
                                onClick = onOpenMissingPermissions,
                            )
                        },
                    )
                },
            )
        }
    }

    if (importPreviewState != null) {
        val preview = importPreviewState.preview
        val context = LocalContext.current
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
        val dateString = dateFormat.format(Date(preview.exportedAtEpochMs))

        MiuixScrollableConfirmDialog(
            show = true,
            onDismissRequest = onDismissPreview,
            title = stringResource(R.string.settings_backup_preview_title),
            confirmText = stringResource(R.string.settings_backup_preview_confirm),
            onConfirm = { onConfirmImport(importPreviewState.uri) },
            dismissText = stringResource(android.R.string.cancel),
        ) {
            Text(stringResource(R.string.settings_backup_preview_info, dateString, preview.appVersionName))
            Text(
                pluralStringResource(
                    R.plurals.settings_backup_preview_count,
                    preview.totalPreferencesCount,
                    preview.totalPreferencesCount,
                ),
            )

            if (preview.domains.isNotEmpty()) {
                val domainNames = preview.domains.joinToString("、") {
                    settingsDomainLabel(context, it)
                }
                Text(stringResource(R.string.settings_backup_preview_domains, domainNames))
            }

            if (preview.importDiff.hasChanges) {
                SettingsBackupDiffSection(context, preview.importDiff)
            }

            if (preview.hasOtpRecords || preview.hasNotificationHistory ||
                preview.hasNotificationFilterRules || preview.hasNotificationFilterPreferences ||
                preview.hasOtpAutoFillStats || preview.hasShellOutputHistory ||
                preview.hasClipboardDirectory || preview.hasShareImageOcrHistoryDirectory
            ) {
                val sensitiveItems = listOfNotNull(
                    if (preview.hasOtpRecords) stringResource(R.string.settings_domain_sensitive_otp) else null,
                    if (preview.hasNotificationHistory) stringResource(R.string.settings_domain_sensitive_notification) else null,
                    if (preview.hasNotificationFilterRules) {
                        stringResource(R.string.settings_domain_sensitive_notification_filter_rules)
                    } else {
                        null
                    },
                    if (preview.hasNotificationFilterPreferences) {
                        stringResource(R.string.settings_domain_sensitive_notification_filter_prefs)
                    } else {
                        null
                    },
                    if (preview.hasOtpAutoFillStats) stringResource(R.string.settings_domain_sensitive_otp_stats) else null,
                    if (preview.hasShellOutputHistory) stringResource(R.string.settings_domain_sensitive_shell_history) else null,
                    if (preview.hasClipboardDirectory) stringResource(R.string.settings_domain_sensitive_clipboard) else null,
                    if (preview.hasShareImageOcrHistoryDirectory) {
                        stringResource(R.string.settings_domain_sensitive_share_image_ocr)
                    } else {
                        null
                    },
                ).joinToString("、")
                Text(
                    text = stringResource(R.string.settings_backup_preview_sensitive, sensitiveItems),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = stringResource(R.string.settings_backup_preview_warning),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsBackupDiffSection(
    context: android.content.Context,
    diff: SettingsBackupImportDiff,
) {
    if (diff.overwrittenDomainCounts.isNotEmpty()) {
        Text(
            text = stringResource(
                R.string.settings_backup_diff_overwritten,
                formatDomainDiffSummary(context, diff.overwrittenDomainCounts),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (diff.newDomainCounts.isNotEmpty()) {
        Text(
            text = stringResource(
                R.string.settings_backup_diff_new,
                formatDomainDiffSummary(context, diff.newDomainCounts),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatDomainDiffSummary(
    context: android.content.Context,
    counts: Map<SettingsDomain, Int>,
): String = counts.entries.joinToString("、") { (domain, count) ->
    "${settingsDomainLabel(context, domain)}（$count）"
}

fun settingsDomainLabel(context: android.content.Context, domain: SettingsDomain): String = when (domain) {
    SettingsDomain.EDGE_GESTURES -> context.getString(R.string.settings_domain_edge)
    SettingsDomain.SHAKE_GESTURES -> context.getString(R.string.settings_domain_shake)
    SettingsDomain.MESSAGE_DANMAKU -> context.getString(R.string.settings_domain_message)
    SettingsDomain.OTP_AUTO_INPUT -> context.getString(R.string.settings_domain_otp)
    SettingsDomain.FLOATING_POINTER -> context.getString(R.string.settings_domain_floating_pointer)
    SettingsDomain.WIDGET_PANEL -> context.getString(R.string.settings_domain_widget_panel)
    SettingsDomain.QUICK_LAUNCHER -> context.getString(R.string.settings_domain_quick_launcher)
    SettingsDomain.FREE_WINDOW -> context.getString(R.string.settings_domain_free_window)
    SettingsDomain.GENERAL -> context.getString(R.string.settings_domain_general)
}
