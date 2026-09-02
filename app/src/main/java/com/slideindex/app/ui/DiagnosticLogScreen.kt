@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.diagnostic.DiagnosticLogConnectionState
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.viewmodel.DiagnosticLogViewModel
import com.slideindex.app.ui.viewmodel.IndexedLogLine
import com.slideindex.app.util.DiagnosticReportExporter
import com.slideindex.app.util.LocalCrashHandler
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class DiagnosticLogTab {
    Crashes,
    Runtime,
}

@Composable
fun DiagnosticLogScreen(
    viewModel: DiagnosticLogViewModel,
    shizukuGranted: Boolean,
    readLogsGranted: Boolean,
    onBack: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestReadLogs: () -> Unit,
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val crashReports by viewModel.crashReports.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(DiagnosticLogTab.Crashes.ordinal) }
    var selectedCrashFile by remember { mutableStateOf<String?>(null) }
    var showClearLogsConfirm by remember { mutableStateOf(false) }
    var showClearCrashesConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.connect()
        onDispose { viewModel.disconnect() }
    }

    LaunchedEffect(shizukuGranted, readLogsGranted) {
        viewModel.refreshPermissionState()
        if (shizukuGranted && readLogsGranted) {
            viewModel.connect()
        }
    }

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
                        contentDescription = stringResource(R.string.diagnostic_log_copy),
                    )
                }
            },
        ) {
            item(key = "crash-detail") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = content.ifBlank { stringResource(R.string.diagnostic_log_crash_empty) },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        return
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.diagnostic_log_title),
        subtitle = stringResource(R.string.diagnostic_log_subtitle),
        onBack = onBack,
        actions = {
            IconButton(onClick = { DiagnosticReportExporter.shareOrCopy(context) }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.diagnostic_log_share),
                )
            }
            if (selectedTab == DiagnosticLogTab.Runtime.ordinal && logs.isNotEmpty()) {
                IconButton(onClick = { showClearLogsConfirm = true }) {
                    MiuixText(
                        text = stringResource(R.string.diagnostic_log_clear),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
            if (selectedTab == DiagnosticLogTab.Crashes.ordinal && crashReports.isNotEmpty()) {
                IconButton(onClick = { showClearCrashesConfirm = true }) {
                    MiuixText(
                        text = stringResource(R.string.diagnostic_log_clear),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        },
    ) {
        item(key = "tabs") {
            MiuixTabRowWithContour(
                tabs = listOf(
                    stringResource(R.string.diagnostic_log_tab_crashes),
                    stringResource(R.string.diagnostic_log_tab_runtime),
                ),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                contentHorizontalPadding = 12.dp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        when (DiagnosticLogTab.entries[selectedTab]) {
            DiagnosticLogTab.Crashes -> {
                if (crashReports.isEmpty()) {
                    item(key = "crashes-empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.diagnostic_log_crash_empty_list),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    groupedCardItems(
                        keyPrefix = "crash-reports",
                        items = crashReports.map { entry ->
                            settingsCardScopeItem(entry.fileName) {
                                CrashReportRow(
                                    entry = entry,
                                    onClick = { selectedCrashFile = entry.fileName },
                                )
                            }
                        },
                    )
                }
            }
            DiagnosticLogTab.Runtime -> {
                if (!shizukuGranted || !readLogsGranted) {
                    item(key = "permission-card") {
                        DiagnosticLogPermissionCard(
                            shizukuGranted = shizukuGranted,
                            readLogsGranted = readLogsGranted,
                            onRequestShizuku = onRequestShizuku,
                            onRequestReadLogs = onRequestReadLogs,
                        )
                    }
                }
                if (logs.isEmpty()) {
                    item(key = "runtime-empty") {
                        RuntimeLogEmptyState(
                            permissionReady = shizukuGranted && readLogsGranted,
                            connectionState = connectionState,
                        )
                    }
                } else {
                    logs.forEach { indexedLog ->
                        item(key = "log-${indexedLog.id}", contentType = "log") {
                            DiagnosticLogLineCard(indexedLog.text)
                        }
                    }
                }
            }
        }

        item(key = "bottom-spacer") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    MiuixConfirmDialog(
        show = showClearLogsConfirm,
        onDismissRequest = { showClearLogsConfirm = false },
        title = stringResource(R.string.diagnostic_log_clear_runtime_title),
        message = stringResource(R.string.diagnostic_log_clear_runtime_message),
        confirmText = stringResource(R.string.diagnostic_log_clear),
        onConfirm = {
            viewModel.clearLogs()
            showClearLogsConfirm = false
        },
    )

    MiuixConfirmDialog(
        show = showClearCrashesConfirm,
        onDismissRequest = { showClearCrashesConfirm = false },
        title = stringResource(R.string.diagnostic_log_clear_crash_title),
        message = stringResource(R.string.diagnostic_log_clear_crash_message),
        confirmText = stringResource(R.string.diagnostic_log_clear),
        onConfirm = {
            viewModel.clearCrashReports()
            showClearCrashesConfirm = false
        },
    )
}

@Composable
private fun RuntimeLogEmptyState(
    permissionReady: Boolean,
    connectionState: DiagnosticLogConnectionState,
) {
    val message = when {
        !permissionReady -> stringResource(R.string.diagnostic_log_permission_required)
        connectionState == DiagnosticLogConnectionState.Connecting ->
            stringResource(R.string.diagnostic_log_connecting)
        connectionState == DiagnosticLogConnectionState.Error ->
            stringResource(R.string.diagnostic_log_stream_error)
        else -> stringResource(R.string.diagnostic_log_waiting)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticLogLineCard(line: String) {
    val parsed = remember(line) { parseLogcatLine(line) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        insideMargin = PaddingValues(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogLevelBadge(parsed.level)
                if (parsed.tag.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = parsed.tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = parsed.message.ifBlank { line },
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LogLevelBadge(level: LogLevel) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = level.color,
    ) {
        Text(
            text = level.label,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
        )
    }
}

@Composable
private fun SettingsCardScope.CrashReportRow(
    entry: LocalCrashHandler.CrashReportEntry,
    onClick: () -> Unit,
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
        onClick = onClick,
    )
}

@Composable
private fun DiagnosticLogPermissionCard(
    shizukuGranted: Boolean,
    readLogsGranted: Boolean,
    onRequestShizuku: () -> Unit,
    onRequestReadLogs: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = stringResource(R.string.diagnostic_log_permission_title),
                style = MiuixTheme.textStyles.title4,
            )
            MiuixText(
                text = stringResource(R.string.diagnostic_log_permission_desc),
                style = MiuixTheme.textStyles.body1,
            )
            if (!shizukuGranted) {
                Button(
                    onClick = onRequestShizuku,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    MiuixText(stringResource(R.string.diagnostic_log_grant_shizuku))
                }
            }
            if (shizukuGranted && !readLogsGranted) {
                Button(
                    onClick = onRequestReadLogs,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    MiuixText(stringResource(R.string.diagnostic_log_grant_read_logs))
                }
            }
        }
    }
}

private enum class LogLevel(val label: String, val color: Color) {
    Verbose("V", Color(0xFFBDBDBD)),
    Debug("D", Color(0xFF81C784)),
    Info("I", Color(0xFF69C0FF)),
    Warning("W", Color(0xFFFFB74D)),
    Error("E", Color(0xFFFF5252)),
    Fatal("F", Color(0xFFD32F2F)),
    Unknown("?", Color(0xFF9E9E9E)),
}

private data class ParsedLogLine(
    val level: LogLevel,
    val tag: String,
    val message: String,
)

private fun parseLogcatLine(line: String): ParsedLogLine {
    val trimmed = line.trim()
    val levelChar = Regex("\\s([VDIWEF])/").find(trimmed)?.groupValues?.getOrNull(1)
    val level = when (levelChar) {
        "V" -> LogLevel.Verbose
        "D" -> LogLevel.Debug
        "I" -> LogLevel.Info
        "W" -> LogLevel.Warning
        "E" -> LogLevel.Error
        "F" -> LogLevel.Fatal
        else -> LogLevel.Unknown
    }
    val tagMatch = Regex("\\s([VDIWEF])\\s(\\S+)\\s*:").find(trimmed)
    val tag = tagMatch?.groupValues?.getOrNull(2).orEmpty()
    val message = if (tag.isNotBlank()) {
        trimmed.substringAfter("$tag:", trimmed).trim()
    } else {
        trimmed
    }
    return ParsedLogLine(level = level, tag = tag, message = message)
}

private fun copyDiagnosticText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Cebian Diagnostic", text))
}
