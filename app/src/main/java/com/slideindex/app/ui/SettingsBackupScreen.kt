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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSectionTitle
import com.slideindex.app.ui.viewmodel.SettingsBackupPreviewState
import com.slideindex.app.settings.SettingsDomain
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(
    onBack: () -> Unit,
    onExport: (includeSensitiveData: Boolean, onJsonReady: (String) -> Unit) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    importPreviewState: SettingsBackupPreviewState?,
    onDismissPreview: () -> Unit,
    onConfirmImport: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var includeSensitiveData by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onExport(includeSensitiveData) { json ->
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImport(uri)
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.settings_backup_title),
        subtitle = stringResource(R.string.settings_backup_subtitle),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.settings_backup_section_actions))
        SettingsHintText(stringResource(R.string.settings_backup_hint))
        SettingsCard {
            SettingSwitchRow(
                title = stringResource(R.string.settings_backup_include_sensitive),
                subtitle = stringResource(R.string.settings_backup_sensitive_hint),
                checked = includeSensitiveData,
                enabled = true,
                onCheckedChange = { includeSensitiveData = it },
            )
        }
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
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
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

    if (importPreviewState != null) {
        val preview = importPreviewState.preview
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
        val dateString = dateFormat.format(Date(preview.exportedAtEpochMs))
        
        AlertDialog(
            onDismissRequest = onDismissPreview,
            title = { Text(stringResource(R.string.settings_backup_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_backup_preview_info, dateString, preview.appVersionName))
                    Text(
                        pluralStringResource(
                            R.plurals.settings_backup_preview_count,
                            preview.totalPreferencesCount,
                            preview.totalPreferencesCount,
                        ),
                    )
                    
                    if (preview.domains.isNotEmpty()) {
                        val domainNames = preview.domains.map { domain ->
                            when (domain) {
                                SettingsDomain.EDGE_GESTURES -> stringResource(R.string.settings_domain_edge)
                                SettingsDomain.SHAKE_GESTURES -> stringResource(R.string.settings_domain_shake)
                                SettingsDomain.MESSAGE_DANMAKU -> stringResource(R.string.settings_domain_message)
                                SettingsDomain.OTP_AUTO_INPUT -> stringResource(R.string.settings_domain_otp)
                                SettingsDomain.FLOATING_POINTER -> stringResource(R.string.settings_domain_floating_pointer)
                                SettingsDomain.WIDGET_PANEL -> stringResource(R.string.settings_domain_widget_panel)
                                SettingsDomain.QUICK_LAUNCHER -> stringResource(R.string.settings_domain_quick_launcher)
                                SettingsDomain.FREE_WINDOW -> stringResource(R.string.settings_domain_free_window)
                                SettingsDomain.GENERAL -> stringResource(R.string.settings_domain_general)
                            }
                        }.joinToString("、")
                        Text(stringResource(R.string.settings_backup_preview_domains, domainNames))
                    }

                    if (preview.hasOtpRecords || preview.hasNotificationHistory) {
                        val sensitiveItems = listOfNotNull(
                            if (preview.hasOtpRecords) stringResource(R.string.settings_domain_sensitive_otp) else null,
                            if (preview.hasNotificationHistory) stringResource(R.string.settings_domain_sensitive_notification) else null
                        ).joinToString("、")
                        Text(
                            text = stringResource(R.string.settings_backup_preview_sensitive, sensitiveItems),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_backup_preview_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirmImport(importPreviewState.rawJson) }) {
                    Text(stringResource(R.string.settings_backup_preview_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPreview) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
