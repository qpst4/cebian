package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureExecuteShellCommandScreen(
    initialCommand: String,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var command by remember(initialCommand) { mutableStateOf(initialCommand) }
    val canSave = command.isNotBlank()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.gesture_shell_command_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onConfirm(command.trim()) },
                        enabled = canSave,
                    ) {
                        Text(stringResource(R.string.shell_panel_save))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        GestureExecuteShellCommandConfigSection(
            command = command,
            onCommandChange = { command = it },
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
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
