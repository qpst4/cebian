package com.slideindex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingsCardScope

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShellShizukuStatusCard(
    shizukuGranted: Boolean,
    restartingService: Boolean,
    onRequestShizuku: () -> Unit,
    onRestartService: () -> Unit,
) {
    SettingsCard {
        segment(key = "shell_shizuku_status") { position ->
            SegmentedListItem(
                onClick = {},
                enabled = true,
                shapes = pickerSegmentedShapes(position.index, position.count),
                colors = settingsSegmentedColors(),
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (shizukuGranted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            ),
                    )
                },
                trailingContent = {
                    if (shizukuGranted) {
                        if (restartingService) {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            IconButton(onClick = onRestartService) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.shell_panel_restart_shizuku),
                                )
                            }
                        }
                    }
                },
                supportingContent = {
                    Text(
                        text = if (shizukuGranted) {
                            stringResource(R.string.shell_panel_shizuku_active_desc)
                        } else {
                            stringResource(R.string.shell_panel_shizuku_inactive_desc)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                content = {
                    Text(
                        text = stringResource(R.string.shell_panel_shizuku_label),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                },
            )
        }
        if (!shizukuGranted) {
            SettingLinkRow(
                title = stringResource(R.string.permission_shizuku_grant),
                subtitle = stringResource(R.string.permission_shizuku_desc),
                onClick = onRequestShizuku,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShellCommandCard(
    item: ShellCommand,
    running: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onRun: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled || running, onClick = onEdit),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.command,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (running) {
                LoadingIndicator(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp),
                )
            } else {
                IconButton(onClick = onRun, enabled = enabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.shell_panel_run),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
