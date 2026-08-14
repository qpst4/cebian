package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon as Material3Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class ShellTestResultState(
    val exitCode: Int,
    val output: String,
)

private fun copyShellOutputWithToast(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", text))
    Toast.makeText(context, R.string.shell_panel_copied, Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShellInlineTestResultCard(
    exitCode: Int,
    output: String,
    onCopyOutput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (exitCode == 0) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onCopyOutput,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.shell_panel_exit_code, exitCode),
                style = MaterialTheme.typography.labelLarge,
                color = if (exitCode == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = output.ifBlank { stringResource(R.string.shell_panel_no_output) },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

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
    val cancelLabel = if (saveAsConfirm) {
        stringResource(R.string.cancel)
    } else {
        stringResource(R.string.shell_panel_cancel)
    }
    val saveLabel = if (saveAsConfirm) {
        stringResource(R.string.confirm)
    } else {
        stringResource(R.string.shell_panel_save)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.shell_panel_delete),
                        tint = MiuixTheme.colorScheme.error,
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
                    MiuixTextButton(
                        text = stringResource(R.string.shell_panel_test),
                        onClick = onTest,
                        enabled = canTest,
                    )
                }
            }
        }
        MiuixTextButton(
            text = cancelLabel,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        MiuixTextButton(
            text = saveLabel,
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun ShellCommandEditorFields(
    label: String,
    onLabelChange: (String) -> Unit,
    command: String,
    onCommandChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiuixLabeledTextField(
            value = label,
            onValueChange = onLabelChange,
            label = stringResource(R.string.shell_panel_label_field),
        )
        MiuixLabeledTextField(
            value = command,
            onValueChange = onCommandChange,
            label = stringResource(R.string.shell_panel_command_field),
            singleLine = false,
            minLines = 3,
            maxLines = 6,
        )
        Text(
            text = stringResource(R.string.shell_panel_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
    var iconDraft by remember(initial) { mutableStateOf(shellCommandIconDraftFrom(initial)) }
    var pickingAppIcon by remember(initial?.id) { mutableStateOf(false) }
    var isSavingAppIcon by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ShellTestResultState?>(null) }
    val canSave = label.isNotBlank() && command.isNotBlank()
    val canTest = canSave && shizukuGranted && !testing
    val context = LocalContext.current

    BackHandler(enabled = pickingAppIcon) { pickingAppIcon = false }

    if (pickingAppIcon) {
        ShellCommandAppIconPickerScreen(
            initial = initial,
            iconDraft = iconDraft,
            isSavingAppIcon = isSavingAppIcon,
            onBack = { pickingAppIcon = false },
            onIconDraftChange = { iconDraft = it },
            onSavingChange = { isSavingAppIcon = it },
        )
        return
    }

    fun buildDraft(): ShellCommand {
        val finalizedIcon = finalizeShellCommandIconDraft(context, initial, iconDraft)
        return applyIconDraft(
            ShellCommand(
                id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                label = label.trim(),
                command = command.trim(),
            ),
            finalizedIcon,
        )
    }

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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShellCommandEditorIconSection(
                    label = label,
                    initial = initial,
                    iconDraft = iconDraft,
                    onIconDraftChange = { iconDraft = it },
                    onPickAppIcon = { pickingAppIcon = true },
                    isSavingAppIcon = isSavingAppIcon,
                )
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
                    ShellInlineTestResultCard(
                        exitCode = result.exitCode,
                        output = result.output,
                        onCopyOutput = { copyShellOutputWithToast(context, result.output) },
                    )
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
    executedAtLabel: String? = null,
) {
    val outputText = output.ifBlank { stringResource(R.string.shell_panel_no_output) }
    val exitSucceeded = exitCode == 0
    val scrollState = rememberScrollState()

    SettingsScreenScaffold(
        title = label,
        subtitle = command,
        onBack = onBack,
        scrollContent = false,
        actions = {
            IconButton(onClick = onCopy) {
                Material3Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.shell_panel_copy),
                )
            }
        },
    ) {
        LazySettingsItem(key = "shell-result-card") {
        CardSegment(isFirst = true, isLast = true) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                    if (executedAtLabel != null) {
                        Text(
                            text = executedAtLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.shell_panel_result_section_output),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (exitSucceeded) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.shell_panel_history_exit_code, exitCode),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (exitSucceeded) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 480.dp)
                            .verticalScroll(scrollState),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = outputText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
        LazySettingsItem(key = "shell-result-actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixTextButton(
                    text = stringResource(R.string.shell_panel_close),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                MiuixTextButton(
                    text = stringResource(R.string.shell_panel_copy),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
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
    var label by remember(initial) { mutableStateOf(initial?.label.orEmpty()) }
    var command by remember(initial) { mutableStateOf(initial?.command.orEmpty()) }
    var iconDraft by remember(initial) { mutableStateOf(shellCommandIconDraftFrom(initial)) }
    var pickingAppIcon by remember(initial?.id) { mutableStateOf(false) }
    var isSavingAppIcon by remember { mutableStateOf(false) }

    OverlayAnimatedDialogContent(
        onDismissComplete = onDismissComplete,
        onWindowReady = onWindowReady,
        registerBackHandler = registerBackHandler,
    ) { requestDismiss ->
        if (pickingAppIcon) {
            Box(modifier = Modifier.fillMaxSize()) {
                ShellCommandAppIconPickerScreen(
                    initial = initial,
                    iconDraft = iconDraft,
                    isSavingAppIcon = isSavingAppIcon,
                    onBack = { pickingAppIcon = false },
                    onIconDraftChange = { iconDraft = it },
                    onSavingChange = { isSavingAppIcon = it },
                )
            }
            return@OverlayAnimatedDialogContent
        }
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
                label = label,
                onLabelChange = { label = it },
                command = command,
                onCommandChange = { command = it },
                iconDraft = iconDraft,
                onIconDraftChange = { iconDraft = it },
                onPickAppIcon = { pickingAppIcon = true },
                isSavingAppIcon = isSavingAppIcon,
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
    label: String,
    onLabelChange: (String) -> Unit,
    command: String,
    onCommandChange: (String) -> Unit,
    iconDraft: ShellCommandIconDraft,
    onIconDraftChange: (ShellCommandIconDraft) -> Unit,
    onPickAppIcon: () -> Unit,
    isSavingAppIcon: Boolean,
    onDismiss: () -> Unit,
    onSave: (ShellCommand) -> Unit,
    onDelete: (() -> Unit)?,
    onTest: ((ShellCommand, (Int, String) -> Unit) -> Unit)?,
) {
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ShellTestResultState?>(null) }
    val canSave = label.isNotBlank() && command.isNotBlank()
    val canTest = canSave && shizukuGranted && !testing
    val context = LocalContext.current

    fun buildDraft(): ShellCommand {
        val finalizedIcon = finalizeShellCommandIconDraft(context, initial, iconDraft)
        return applyIconDraft(
            ShellCommand(
                id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                label = label.trim(),
                command = command.trim(),
            ),
            finalizedIcon,
        )
    }

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
        ShellCommandEditorIconSection(
            label = label,
            initial = initial,
            iconDraft = iconDraft,
            onIconDraftChange = onIconDraftChange,
            onPickAppIcon = onPickAppIcon,
            isSavingAppIcon = isSavingAppIcon,
        )
        ShellCommandEditorFields(
            label = label,
            onLabelChange = {
                onLabelChange(it)
                testResult = null
            },
            command = command,
            onCommandChange = {
                onCommandChange(it)
                testResult = null
            },
        )
        testResult?.let { result ->
            ShellInlineTestResultCard(
                exitCode = result.exitCode,
                output = result.output,
                onCopyOutput = { copyShellOutputWithToast(context, result.output) },
            )
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
