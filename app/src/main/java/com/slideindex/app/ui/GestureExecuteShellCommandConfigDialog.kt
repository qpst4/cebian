package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureExecuteShellCommandScreen(
    initialCommand: String,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var command by remember(initialCommand) { mutableStateOf(initialCommand) }
    val canSave = command.isNotBlank()

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_shell_command_config_title),
        onBack = onBack,
        actions = {
            TextButton(
                onClick = { onConfirm(command.trim()) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.shell_panel_save))
            }
        },
    ) {
        GestureExecuteShellCommandConfigSection(
            command = command,
            onCommandChange = { command = it },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
fun GestureExecuteShellCommandConfigSection(
    command: String,
    onCommandChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = command,
            onValueChange = onCommandChange,
            label = { Text(stringResource(R.string.shell_panel_command_field)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            shape = MaterialTheme.shapes.medium,
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
