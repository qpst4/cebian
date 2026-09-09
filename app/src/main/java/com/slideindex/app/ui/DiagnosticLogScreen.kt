@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.viewmodel.DiagnosticLogViewModel
import com.slideindex.app.util.DiagnosticReportExporter
import com.slideindex.app.util.LocalCrashHandler
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticLogScreen(
    viewModel: DiagnosticLogViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val crashReports by viewModel.crashReports.collectAsStateWithLifecycle()
    var selectedCrashFile by remember { mutableStateOf<String?>(null) }
    var showClearCrashesConfirm by remember { mutableStateOf(false) }

    selectedCrashFile?.let { fileName ->
        val content = remember(fileName) { viewModel.readCrashReport(fileName).orEmpty() }
        val timeLabel = remember(fileName, crashReports) {
            crashReports.find { it.fileName == fileName }?.timestampMs?.let { ts ->
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))
            }
        }
        SettingsScreenScaffold(
            title = fileName,
            subtitle = timeLabel,
            onBack = { selectedCrashFile = null },
            scrollContent = false,
            actions = {
                IconButton(onClick = { copyDiagnosticText(context, content) }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.diagnostic_log_copy)
                    )
                }
            }
        ) {
            item(key = "crash-detail") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = content.ifBlank { stringResource(R.string.diagnostic_log_crash_empty) },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        return
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.diagnostic_log_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { DiagnosticReportExporter.shareOrCopy(context) }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.diagnostic_log_share)
                )
            }
            if (crashReports.isNotEmpty()) {
                IconButton(onClick = { showClearCrashesConfirm = true }) {
                    Text(
                        text = stringResource(R.string.diagnostic_log_clear),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    ) {
        if (crashReports.isEmpty()) {
            item(key = "crashes-empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.diagnostic_log_crash_empty_list),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item(key = "crash-reports-top-spacer") {
                Spacer(Modifier.padding(top = 8.dp))
            }
            groupedCardItems(
                keyPrefix = "crash-reports",
                items = crashReports.map { entry ->
                    settingsCardScopeItem(entry.fileName) {
                        CrashReportRow(
                            entry = entry,
                            onClick = { selectedCrashFile = entry.fileName }
                        )
                    }
                }
            )
        }

        item(key = "bottom-spacer") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    MiuixConfirmDialog(
        show = showClearCrashesConfirm,
        onDismissRequest = { showClearCrashesConfirm = false },
        title = stringResource(R.string.diagnostic_log_clear_crash_title),
        message = stringResource(R.string.diagnostic_log_clear_crash_message),
        confirmText = stringResource(R.string.diagnostic_log_clear),
        onConfirm = {
            viewModel.clearCrashReports()
            showClearCrashesConfirm = false
        }
    )
}

@Composable
private fun SettingsCardScope.CrashReportRow(
    entry: LocalCrashHandler.CrashReportEntry,
    onClick: () -> Unit
) {
    val timeLabel = remember(entry.timestampMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.timestampMs))
    }
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Outlined.BugReport, contentDescription = label) },
        title = entry.fileName,
        subtitle = buildString {
            append(timeLabel)
            if (entry.previewLine.isNotBlank()) {
                append("\n")
                append(entry.previewLine)
            }
        },
        onClick = onClick
    )
}

private fun copyDiagnosticText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Cebian Diagnostic", text))
}
