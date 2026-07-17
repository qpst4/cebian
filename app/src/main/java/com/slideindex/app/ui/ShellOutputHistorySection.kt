package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.shell.ShellOutputHistoryEntry
import com.slideindex.app.shell.ShellOutputHistoryRepository
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import java.text.DateFormat
import java.util.Date

@Composable
fun ShellOutputHistoryEntryRow(
    historyCount: Int,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.History, contentDescription = label) },
        title = stringResource(R.string.shell_panel_history_entry_title),
        subtitle = if (historyCount > 0) {
            pluralStringResource(
                R.plurals.shell_panel_history_entry_desc_count,
                historyCount,
                historyCount,
            )
        } else {
            stringResource(R.string.shell_panel_history_entry_desc_empty)
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellOutputHistoryScreen(
    repository: ShellOutputHistoryRepository,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val entries by repository.entries.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<ShellOutputHistoryEntry?>(null) }
    val filtered = remember(entries, query) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            entries
        } else {
            entries.filter {
                it.label.contains(trimmed, ignoreCase = true) ||
                    it.command.contains(trimmed, ignoreCase = true) ||
                    it.output.contains(trimmed, ignoreCase = true)
            }
        }
    }

    if (selectedEntry != null) {
        val entry = selectedEntry!!
        ShellResultScreen(
            label = entry.label,
            command = entry.command,
            exitCode = entry.exitCode,
            output = entry.output,
            onBack = { selectedEntry = null },
            onCopy = { copyShellOutput(context, entry.output) },
        )
        return
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.shell_panel_history_title),
        subtitle = stringResource(R.string.shell_panel_history_desc),
        onBack = onBack,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.shell_panel_history_search_hint)) },
        )
        if (entries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.shell_panel_history_clear))
                }
            }
        }
        if (filtered.isEmpty()) {
            SettingsHintText(stringResource(R.string.shell_panel_history_empty))
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                filtered.forEach { entry ->
                    ShellHistoryEntryCard(
                        entry = entry,
                        onClick = { selectedEntry = entry },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShellHistoryEntryCard(
    entry: ShellOutputHistoryEntry,
    onClick: () -> Unit,
) {
    val timeLabel = remember(entry.executedAtEpochMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(entry.executedAtEpochMs))
    }
    val outputPreview = remember(entry.output) {
        entry.output.trim().ifBlank { "—" }
    }
    val exitSucceeded = entry.exitCode == 0

    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (exitSucceeded) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Text(
                        text = stringResource(R.string.shell_panel_history_exit_code, entry.exitCode),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (exitSucceeded) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
            }
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.command,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = outputPreview,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun copyShellOutput(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", text))
}
