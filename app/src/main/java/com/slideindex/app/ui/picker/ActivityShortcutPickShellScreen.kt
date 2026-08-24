package com.slideindex.app.ui.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.ShellCommandIcon
import com.slideindex.app.ui.ThinActionIcons
import com.slideindex.app.ui.gestureExecuteShellCommandPreview
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutPickShellScreen(
    shellCommands: List<ShellCommand>,
    onBack: () -> Unit,
    onPick: (ShellCommand) -> Unit,
) {
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.activity_shortcut_add_from_shell),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (shellCommands.isEmpty()) {
            item(key = "shell-empty") {
                Text(
                    text = stringResource(R.string.quick_launcher_shell_shortcuts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            return@SettingsLazyScreenScaffold
        }
        item(key = "shell-section") {
            Md3PickerSectionHeader(stringResource(R.string.quick_launcher_shell_shortcuts_section))
        }
        items(
            count = shellCommands.size,
            key = { shellCommands[it].id },
        ) { index ->
            val cmd = shellCommands[index]
            Md3PickerListRow(
                segmentIndex = index,
                segmentCount = shellCommands.size,
                title = cmd.label.ifBlank { gestureExecuteShellCommandPreview(cmd.command) },
                subtitle = gestureExecuteShellCommandPreview(cmd.command, maxLength = 64),
                selected = false,
                onClick = { onPick(cmd) },
                leadingContent = { ShellCommandPickLeading(cmd) },
                trailingMode = PickerTrailingMode.None,
            )
        }
    }
}

@Composable
private fun ShellCommandPickLeading(command: ShellCommand) {
    if (command.hasCustomIcon()) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ShellCommandIcon(
                    command = command,
                    modifier = Modifier.size(28.dp),
                    showDefaultCodeIcon = true,
                )
            }
        }
    } else {
        Md3PickerIconLeading(
            icon = ThinActionIcons.Code,
            selected = false,
        )
    }
}
