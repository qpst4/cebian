package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureExecuteShellCommandScreen(
    initialCommand: String,
    shellCommands: List<ShellCommand> = emptyList(),
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
    embedInParentChrome: Boolean = false,
    overlayMode: Boolean = false,
    enableBackHandler: Boolean = true,
) {
    var command by remember(initialCommand) { mutableStateOf(initialCommand) }
    val canSave = command.isNotBlank()

    val handlePickPreset: (ShellCommand) -> Unit = { picked ->
        val chosen = picked.command.trim()
        if (chosen.isNotBlank()) {
            onConfirm(chosen)
        }
    }

    val shortcutPickCard = settingsCardItems(shellCommands) {
        ShellCommandPanelShortcutPickSection(
            shellCommands = shellCommands,
            onPick = handlePickPreset,
        )
    }

    val customCommandCard = settingsCardItems(command) {
        SettingsCardRow(key = "shell_command_input_row") {
            MiuixLabeledTextField(
                value = command,
                onValueChange = { command = it },
                label = stringResource(R.string.shell_panel_command_field),
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    if (embedInParentChrome) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shellCommands.isNotEmpty()) {
                MiuixSmallTitle(
                    text = stringResource(R.string.quick_launcher_shell_shortcuts_section),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                shortcutPickCard.RenderRows()
            }

            MiuixSmallTitle(
                text = stringResource(R.string.shell_panel_command_field),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (shellCommands.isNotEmpty()) MiuixSmallTitleSectionTop else 4.dp),
            )
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                MiuixLabeledTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = stringResource(R.string.shell_panel_command_field),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            MiuixHintText(
                text = stringResource(R.string.gesture_shell_command_config_hint),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )

            Button(
                onClick = { onConfirm(command.trim()) },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.shell_panel_save),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        return
    }

    val shortcutsSectionTitle = stringResource(R.string.quick_launcher_shell_shortcuts_section)
    val customSectionTitle = stringResource(R.string.shell_panel_command_field)

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_shell_command_config_title),
        onBack = onBack,
        enableBackHandler = enableBackHandler,
        overlayMode = overlayMode,
        actions = {
            top.yukonga.miuix.kmp.basic.IconButton(
                onClick = { onConfirm(command.trim()) },
                enabled = canSave,
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.shell_panel_save),
                )
            }
        },
    ) {
        if (shellCommands.isNotEmpty()) {
            settingsLazySmallTitle(
                key = "shell_shortcuts_title",
                title = shortcutsSectionTitle,
                sectionTop = false,
            )
            LazySettingsItem(key = "shell-panel-shortcuts") {
                shortcutPickCard.RenderRows()
            }
        }

        settingsLazySmallTitle(
            key = "custom_shell_command_title",
            title = customSectionTitle,
            sectionTop = shellCommands.isNotEmpty(),
        )
        LazySettingsItem(key = "gesture-shell-command-config") {
            customCommandCard.RenderRows()
        }
        LazySettingsItem(key = "gesture-shell-command-hint") {
            MiuixHintText(
                text = stringResource(R.string.gesture_shell_command_config_hint),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
