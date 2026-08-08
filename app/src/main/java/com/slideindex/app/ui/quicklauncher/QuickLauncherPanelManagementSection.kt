package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.launcher.QuickLauncherPanelMutator
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.settings.components.SettingsCardScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLauncherPanelManagementSection(
    panels: List<QuickLauncherPanel>,
    selectedIndex: Int,
    defaultColumns: Int,
    defaultRows: Int,
    onPanelsChange: (List<QuickLauncherPanel>) -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestPanels by rememberUpdatedState(panels)
    val safeIndex = selectedIndex.coerceIn(0, (panels.size - 1).coerceAtLeast(0))
    val currentPanel = panels.getOrElse(safeIndex) { QuickLauncherPanelDefaults.defaultPanel() }
    var renameTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }

    fun updatePanel(index: Int, updated: QuickLauncherPanel) {
        val panelId = latestPanels.getOrNull(index)?.id ?: return
        onPanelsChange(QuickLauncherPanelMutator.replacePanel(latestPanels, panelId, updated))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.quick_launcher_panels_section),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(
                enabled = panels.size < QuickLauncherPanelDefaults.MAX_PANELS,
                onClick = {
                    val added = QuickLauncherPanelMutator.addPanel(
                        panels = latestPanels,
                        defaultColumns = defaultColumns,
                        defaultRows = defaultRows,
                    ) ?: return@IconButton
                    onPanelsChange(added)
                    onSelectedIndexChange(added.lastIndex)
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.quick_launcher_panel_add))
            }
        }

        if (panels.size > 1) {
            MiuixTabRowWithContour(
                tabs = panels.mapIndexed { index, panel ->
                    panel.name.ifBlank {
                        stringResource(R.string.quick_launcher_panel_default_name, index + 1)
                    }
                },
                selectedTabIndex = safeIndex,
                onTabSelected = onSelectedIndexChange,
            )
        }

        val displayName = currentPanel.name.ifBlank {
            stringResource(R.string.quick_launcher_panel_default_name, safeIndex + 1)
        }
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = {
                    renameTarget = currentPanel
                    renameText = currentPanel.name
                }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.quick_launcher_panel_rename))
                }
                IconButton(
                    enabled = panels.size < QuickLauncherPanelDefaults.MAX_PANELS,
                    onClick = {
                        QuickLauncherPanelMutator.duplicatePanel(latestPanels, currentPanel.id)?.let { duplicated ->
                            onPanelsChange(duplicated)
                            onSelectedIndexChange(duplicated.lastIndex)
                        }
                    },
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.quick_launcher_panel_duplicate))
                }
                IconButton(
                    enabled = panels.size > 1,
                    onClick = { deleteTarget = currentPanel },
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.quick_launcher_panel_delete))
                }
            }
            PanelLayoutSliders(
                panel = currentPanel,
                onPanelChange = { updatePanel(safeIndex, it) },
            )
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.quick_launcher_panel_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val index = latestPanels.indexOfFirst { it.id == target.id }
                        if (index >= 0) {
                            updatePanel(index, target.copy(name = renameText.trim()))
                        }
                        renameTarget = null
                    },
                ) { Text(stringResource(R.string.shell_panel_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.quick_launcher_panel_delete)) },
            text = { Text(stringResource(R.string.quick_launcher_panel_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val removed = QuickLauncherPanelMutator.removePanel(latestPanels, target.id)
                        if (removed != null) {
                            onPanelsChange(removed)
                            onSelectedIndexChange(
                                safeIndex.coerceIn(0, (removed.size - 1).coerceAtLeast(0)),
                            )
                        }
                        deleteTarget = null
                    },
                ) { Text(stringResource(R.string.shell_panel_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsCardScope.PanelLayoutSliders(
    panel: QuickLauncherPanel,
    onPanelChange: (QuickLauncherPanel) -> Unit,
) {
    SettingsSliderRow(
        title = stringResource(R.string.quick_launcher_grid_columns),
        value = panel.columnsPerPage.toFloat(),
        valueRange = 2f..5f,
        steps = 2,
        enabled = true,
        label = stringResource(R.string.quick_launcher_grid_columns_label, panel.columnsPerPage),
        onValueChange = { onPanelChange(panel.copy(columnsPerPage = it.toInt())) },
    )
    SettingsSliderRow(
        title = stringResource(R.string.quick_launcher_grid_rows),
        value = panel.rowsPerPage.toFloat(),
        valueRange = 2f..6f,
        steps = 3,
        enabled = true,
        label = stringResource(R.string.quick_launcher_grid_rows_label, panel.rowsPerPage),
        onValueChange = { onPanelChange(panel.copy(rowsPerPage = it.toInt())) },
    )
}
