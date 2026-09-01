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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.privilege.PrivilegeUiStrings
import androidx.compose.foundation.layout.width
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsCardSegmentContent
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsGroupedRowBackground
import top.yukonga.miuix.kmp.basic.BasicComponent

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShellShizukuStatusCard(
    shizukuGranted: Boolean,
    restartingService: Boolean,
    onRequestShizuku: () -> Unit,
    onRestartService: () -> Unit,
    embeddedInOverlay: Boolean = false,
) {
    val card = settingsCardItems(shizukuGranted, restartingService) {
        SettingsCardRow(key = "shell_shizuku_status") { position ->
            BasicComponent(
                modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
                title = stringResource(PrivilegeUiStrings.shellPanelLabelRes()),
                summary = if (shizukuGranted) {
                    stringResource(PrivilegeUiStrings.shellPanelActiveDescRes())
                } else {
                    stringResource(PrivilegeUiStrings.shellPanelInactiveDescRes())
                },
                startAction = {
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
                endActions = {
                    if (shizukuGranted && PrivilegeGateway.isShizukuMode()) {
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
            )
        }
        if (!shizukuGranted && PrivilegeGateway.isShizukuMode()) {
            SettingLinkRow(
                title = stringResource(PrivilegeUiStrings.shellPanelGrantTitleRes()),
                subtitle = stringResource(PrivilegeUiStrings.shellPanelGrantDescRes()),
                onClick = onRequestShizuku,
            )
        }
    }
    if (embeddedInOverlay) {
        SettingsCardSegmentContent { card.RenderRows() }
    } else {
        card.RenderRows()
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
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (item.hasCustomIcon()) {
                ShellCommandIcon(
                    command = item,
                    modifier = Modifier
                        .padding(top = 2.dp, end = 8.dp)
                        .size(32.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (running) {
                LoadingIndicator(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(32.dp),
                )
            } else {
                IconButton(
                    onClick = onRun,
                    enabled = enabled,
                    modifier = Modifier.size(36.dp),
                ) {
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
