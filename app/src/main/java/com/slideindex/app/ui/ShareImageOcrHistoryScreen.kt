package com.slideindex.app.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.service.ShareImageOcrCoordinator
import com.slideindex.app.service.ShareImageOcrHistoryEntry
import com.slideindex.app.service.ShareImageOcrHistoryRepository
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import java.text.DateFormat
import java.util.Date

@Composable
fun ShareImageOcrHistoryEntryRow(
    historyCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(Icons.Default.History, contentDescription = label) },
        title = stringResource(R.string.share_image_ocr_history_title),
        subtitle = if (historyCount > 0) {
            pluralStringResource(R.plurals.share_image_ocr_history_entry_desc_count, historyCount, historyCount)
        } else {
            stringResource(R.string.share_image_ocr_history_entry_desc_empty)
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShareImageOcrHistoryScreen(
    repository: ShareImageOcrHistoryRepository,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val entries by repository.entries.collectAsStateWithLifecycle()
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    SettingsScreenScaffold(
        title = stringResource(R.string.share_image_ocr_history_title),
        onBack = onBack,
    ) {
        if (entries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.share_image_ocr_history_clear))
                }
            }
        }
        if (entries.isEmpty()) {
            SettingsHintText(stringResource(R.string.share_image_ocr_history_empty))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { entry ->
                    ShareImageOcrHistoryListItem(
                        entry = entry,
                        dateFormat = dateFormat,
                        onOpen = { openHistoryEntry(context, entry) },
                    )
                }
            }
        }
        SettingsHintText(stringResource(R.string.share_image_ocr_history_hint))
    }
}

@Composable
private fun ShareImageOcrHistoryListItem(
    entry: ShareImageOcrHistoryEntry,
    dateFormat: DateFormat,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = dateFormat.format(Date(entry.createdAtEpochMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.ocrText.lineSequence().firstOrNull().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun openHistoryEntry(context: Context, entry: ShareImageOcrHistoryEntry) {
    ShareImageOcrCoordinator.showHistoryEntry(context, entry)
}
