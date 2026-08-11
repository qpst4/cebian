package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.slideindex.app.ui.miuix.MiuixSmallTitle
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

    val renameLabel = stringResource(R.string.quick_launcher_panel_rename)
    LaunchedEffect(renameTarget?.id) {
        renameTarget?.let { renameText = it.name }
    }
    val duplicateLabel = stringResource(R.string.quick_launcher_panel_duplicate)
    val deleteLabel = stringResource(R.string.quick_launcher_panel_delete)
    val panelMenuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = renameLabel,
                onClick = {
                    renameTarget = currentPanel
                    renameText = currentPanel.name
                },
                icon = { modifier ->
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = modifier.size(20.dp),
                    )
                },
            ),
            DropdownItem(
                text = duplicateLabel,
                enabled = panels.size < QuickLauncherPanelDefaults.MAX_PANELS,
                onClick = {
                    QuickLauncherPanelMutator.duplicatePanel(latestPanels, currentPanel.id)
                        ?.let { duplicated ->
                            onPanelsChange(duplicated)
                            onSelectedIndexChange(duplicated.lastIndex)
                        }
                },
                icon = { modifier ->
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = modifier.size(20.dp),
                    )
                },
            ),
            DropdownItem(
                text = deleteLabel,
                enabled = panels.size > 1,
                onClick = { deleteTarget = currentPanel },
                icon = { modifier ->
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = modifier.size(20.dp),
                    )
                },
            ),
        ),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MiuixSmallTitle(stringResource(R.string.quick_launcher_panels_section))

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
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            renameTarget = currentPanel
                            renameText = currentPanel.name
                        }
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.quick_launcher_panel_add),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WindowIconDropdownMenu(entry = panelMenuEntry) {
                    MiuixIcon(
                        Icons.Default.MoreVert,
                        contentDescription = renameLabel,
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
            PanelLayoutSliders(
                panel = currentPanel,
                onPanelChange = { updatePanel(safeIndex, it) },
            )
        }
    }

    MiuixFormDialog(
        show = renameTarget != null,
        onDismissRequest = { renameTarget = null },
        title = stringResource(R.string.quick_launcher_panel_rename),
        confirmText = stringResource(R.string.shell_panel_save),
        confirmEnabled = renameText.isNotBlank(),
        onConfirm = {
            val target = renameTarget ?: return@MiuixFormDialog
            val index = latestPanels.indexOfFirst { it.id == target.id }
            if (index >= 0) {
                updatePanel(index, target.copy(name = renameText.trim()))
            }
            renameTarget = null
        },
    ) {
        MiuixLabeledTextField(
            value = renameText,
            onValueChange = { renameText = it },
            label = stringResource(R.string.quick_launcher_panel_rename),
        )
    }

    MiuixConfirmDialog(
        show = deleteTarget != null,
        onDismissRequest = { deleteTarget = null },
        title = stringResource(R.string.quick_launcher_panel_delete),
        message = stringResource(R.string.quick_launcher_panel_delete_confirm),
        confirmText = stringResource(R.string.shell_panel_delete),
        onConfirm = {
            val target = deleteTarget ?: return@MiuixConfirmDialog
            val removed = QuickLauncherPanelMutator.removePanel(latestPanels, target.id)
            if (removed != null) {
                onPanelsChange(removed)
                onSelectedIndexChange(
                    safeIndex.coerceIn(0, (removed.size - 1).coerceAtLeast(0)),
                )
            }
            deleteTarget = null
        },
    )
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
