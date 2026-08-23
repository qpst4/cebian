package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardItems

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureExecuteShellCommandScreen(
    initialCommand: String,
    shellCommands: List<ShellCommand> = emptyList(),
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
    enableBackHandler: Boolean = true,
) {
    var command by remember(initialCommand) { mutableStateOf(initialCommand) }
    val canSave = command.isNotBlank()
    val shortcutPickCard = settingsCardItems(shellCommands) {
        ShellCommandPanelShortcutPickSection(
            shellCommands = shellCommands,
            onPick = { picked -> command = picked.command.trim() },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_shell_command_config_title),
        onBack = onBack,
        enableBackHandler = enableBackHandler,
        actions = {
            MiuixTextButton(
                text = stringResource(R.string.shell_panel_save),
                onClick = { onConfirm(command.trim()) },
                enabled = canSave,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
    ) {
        if (shellCommands.isNotEmpty()) {
            LazySettingsItem(key = "shell-panel-shortcuts") {
                shortcutPickCard.RenderRows()
            }
        } else {
            LazySettingsItem(key = "shell-panel-shortcuts-empty") {
                Text(
                    text = stringResource(R.string.quick_launcher_shell_shortcuts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        LazySettingsItem(key = "gesture-shell-command-config") {
            GestureExecuteShellCommandConfigSection(
                command = command,
                onCommandChange = { command = it },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun GestureExecuteShellCommandConfigSection(
    command: String,
    onCommandChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MiuixLabeledTextField(
            value = command,
            onValueChange = onCommandChange,
            label = stringResource(R.string.shell_panel_command_field),
            singleLine = false,
            minLines = 3,
            maxLines = 6,
        )
        Text(
            text = stringResource(R.string.gesture_shell_command_config_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

fun gestureActionNeedsShellCommandConfig(action: GestureAction): Boolean =
    action is GestureAction.ExecuteShellCommand

fun gestureExecuteShellCommandPreview(command: String, maxLength: Int = 40): String =
    command.lineSequence().firstOrNull().orEmpty().trim().let { line ->
        if (line.length <= maxLength) line else line.take(maxLength) + "…"
    }
