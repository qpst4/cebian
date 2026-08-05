package com.slideindex.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class ShellTestResultState(
    val exitCode: Int,
    val output: String,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellCommandEditorActionsRow(
    canSave: Boolean,
    canTest: Boolean,
    testing: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onTest: (() -> Unit)? = null,
    saveAsConfirm: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.shell_panel_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (onTest != null) {
                if (testing) {
                    LoadingIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(20.dp),
                    )
                } else {
                    TextButton(
                        enabled = canTest,
                        onClick = onTest,
                    ) {
                        Text(stringResource(R.string.shell_panel_test))
                    }
                }
            }
        }
        TextButton(onClick = onCancel) {
            Text(
                if (saveAsConfirm) {
                    stringResource(R.string.cancel)
                } else {
                    stringResource(R.string.shell_panel_cancel)
                },
            )
        }
        if (saveAsConfirm) {
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.confirm))
            }
        } else {
            TextButton(
                enabled = canSave,
                onClick = onSave,
            ) {
                Text(stringResource(R.string.shell_panel_save))
            }
        }
    }
}

@Composable
private fun ShellCommandEditorFields(
    label: String,
    onLabelChange: (String) -> Unit,
    command: String,
    onCommandChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = label,
        onValueChange = onLabelChange,
        label = { Text(stringResource(R.string.shell_panel_label_field)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
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
        text = stringResource(R.string.shell_panel_edit_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandEditorScreen(
    initial: ShellCommand?,
    shizukuGranted: Boolean,
    onBack: () -> Unit,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)? = null,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)? = null,
) {
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var command by remember(initial) { mutableStateOf(initial?.command.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ShellTestResultState?>(null) }
    val canSave = label.isNotBlank() && command.isNotBlank()
    val canTest = canSave && shizukuGranted && !testing

    fun buildDraft(): ShellCommand =
        ShellCommand(
            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
            label = label.trim(),
            command = command.trim(),
        )

    val title = if (initial == null) {
        stringResource(R.string.shell_panel_add)
    } else {
        stringResource(R.string.shell_panel_edit)
    }

    SettingsScreenScaffold(
        title = title,
        onBack = onBack,
    ) {
        LazySettingsItem(key = "shell-editor-body") {
            ShellCommandEditorFields(
            label = label,
            onLabelChange = {
                label = it
                testResult = null
            },
            command = command,
            onCommandChange = {
                command = it
                testResult = null
            },
        )
        testResult?.let { result ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    if (result.exitCode == 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.shell_panel_exit_code, result.exitCode),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (result.exitCode == 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = result.output.ifBlank { stringResource(R.string.shell_panel_no_output) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        ShellCommandEditorActionsRow(
            canSave = canSave,
            canTest = canTest,
            testing = testing,
            onCancel = onBack,
            onSave = { onSave(buildDraft()) },
            onDelete = onDelete,
            onTest = onTest?.let { test ->
                {
                    testing = true
                    testResult = null
                    test.invoke(buildDraft()) { exitCode, output ->
                        testing = false
                        testResult = ShellTestResultState(exitCode, output)
                    }
                }
            },
            saveAsConfirm = true,
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellResultScreen(
    label: String,
    command: String,
    exitCode: Int,
    output: String,
    onBack: () -> Unit,
    onCopy: () -> Unit,
) {
    SettingsScreenScaffold(
        title = label,
        onBack = onBack,
    ) {
        LazySettingsItem(key = "shell-result-body") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.shell_panel_exit_code, exitCode),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (exitCode == 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = output.ifBlank { stringResource(R.string.shell_panel_no_output) },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.shell_panel_result_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.shell_panel_close))
                    }
                    Button(
                        onClick = onCopy,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.shell_panel_copy))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandResultOverlaySheet(
    label: String,
    command: String,
    exitCode: Int,
    output: String,
    onDismissComplete: () -> Unit,
    onCopy: () -> Unit,
    onWindowReady: (() -> Unit)? = null,
    registerBackHandler: ((() -> Unit) -> Unit)? = null,
) {
    OverlayAnimatedDialogContent(
        onDismissComplete = onDismissComplete,
        onWindowReady = onWindowReady,
        registerBackHandler = registerBackHandler,
    ) { requestDismiss ->
        Card(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiuixText(
                    text = label,
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                MiuixText(
                    text = command,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                MiuixText(
                    text = stringResource(R.string.shell_panel_exit_code, exitCode),
                    style = MiuixTheme.textStyles.subtitle,
                    color = if (exitCode == 0) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.error
                    },
                )
                val scrollState = rememberScrollState()
                MiuixText(
                    text = output.ifBlank { stringResource(R.string.shell_panel_no_output) },
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(scrollState),
                )
                MiuixText(
                    text = stringResource(R.string.shell_panel_result_hint),
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.shell_panel_copy),
                        onClick = onCopy,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.shell_panel_close),
                        onClick = requestDismiss,
                    )
                }
            }
        }
    }
}

@Composable
fun ShellCommandEditorDialog(
    initial: ShellCommand?,
    onDismiss: () -> Unit,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)? = null,
    shizukuGranted: Boolean = true,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)? = null,
) {
    ShellCommandEditorScreen(
        initial = initial,
        shizukuGranted = shizukuGranted,
        onBack = onDismiss,
        onSave = onSave,
        onDelete = onDelete,
        onTest = onTest,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandEditorOverlaySheet(
    onDismissComplete: () -> Unit,
    initial: ShellCommand?,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)? = null,
    shizukuGranted: Boolean = true,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)? = null,
    onWindowReady: (() -> Unit)? = null,
    registerBackHandler: ((() -> Unit) -> Unit)? = null,
) {
    OverlayAnimatedDialogContent(
        onDismissComplete = onDismissComplete,
        onWindowReady = onWindowReady,
        registerBackHandler = registerBackHandler,
    ) { requestDismiss ->
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            ShellCommandEditorOverlayBody(
                initial = initial,
                shizukuGranted = shizukuGranted,
                onDismiss = requestDismiss,
                onSave = {
                    onSave(it)
                    requestDismiss()
                },
                onDelete = onDelete?.let { delete ->
                    {
                        delete()
                        requestDismiss()
                    }
                },
                onTest = onTest,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellCommandEditorOverlayBody(
    initial: ShellCommand?,
    shizukuGranted: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)?,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)?,
) {
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var command by remember(initial) { mutableStateOf(initial?.command.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ShellTestResultState?>(null) }
    val canSave = label.isNotBlank() && command.isNotBlank()
    val canTest = canSave && shizukuGranted && !testing

    fun buildDraft(): ShellCommand =
        ShellCommand(
            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
            label = label.trim(),
            command = command.trim(),
        )

    val title = if (initial == null) {
        stringResource(R.string.shell_panel_add)
    } else {
        stringResource(R.string.shell_panel_edit)
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLargeEmphasized)
        ShellCommandEditorFields(
            label = label,
            onLabelChange = {
                label = it
                testResult = null
            },
            command = command,
            onCommandChange = {
                command = it
                testResult = null
            },
        )
        testResult?.let { result ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    if (result.exitCode == 0) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.shell_panel_exit_code, result.exitCode),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (result.exitCode == 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = result.output.ifBlank { stringResource(R.string.shell_panel_no_output) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        ShellCommandEditorActionsRow(
            canSave = canSave,
            canTest = canTest,
            testing = testing,
            onCancel = onDismiss,
            onSave = { onSave(buildDraft()) },
            onDelete = onDelete,
            onTest = onTest?.let { test ->
                {
                    testing = true
                    testResult = null
                    test.invoke(buildDraft()) { exitCode, output ->
                        testing = false
                        testResult = ShellTestResultState(exitCode, output)
                    }
                }
            },
        )
    }
}
