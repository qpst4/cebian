package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.ShellCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val SHELL_PANEL_OVERLAY_TIMEOUT_MS = 40_000L

private class ContinuousDismissGate {
    var editorOpen: Boolean = false
    var resultOpen: Boolean = false
    fun canDismiss(): Boolean = !editorOpen && !resultOpen
}

private data class ShellPanelResultState(
    val label: String,
    val command: String,
    val exitCode: Int,
    val output: String,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandPanelOverlaySheet(
    initialCommands: List<ShellCommand>,
    shizukuGranted: Boolean,
    onDismissComplete: () -> Unit,
    onPersistCommands: (List<ShellCommand>) -> Unit,
    onWindowReady: (() -> Unit)? = null,
    registerBackHandler: ((() -> Unit) -> Unit)? = null,
    registerContinuousDismissHandler: ((() -> Unit) -> Unit)? = null,
    onCopyOutput: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val shellTimeoutMessage = stringResource(R.string.shell_panel_execute_timeout)
    val scope = rememberCoroutineScope()
    var commands by remember(initialCommands) { mutableStateOf(initialCommands) }
    var editorTarget by remember { mutableStateOf<ShellCommand?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var runningCommandId by remember { mutableStateOf<String?>(null) }
    var resultDialog by remember { mutableStateOf<ShellPanelResultState?>(null) }
    val dismissGate = remember { ContinuousDismissGate() }
    dismissGate.editorOpen = showEditor
    dismissGate.resultOpen = resultDialog != null

    SideEffect {
        registerContinuousDismissHandler?.invoke {
            if (dismissGate.canDismiss()) {
                onDismissComplete()
            }
        }
    }

    if (showEditor) {
        ShellCommandEditorOverlaySheet(
            onDismissComplete = {
                showEditor = false
                editorTarget = null
            },
            initial = editorTarget,
            shizukuGranted = shizukuGranted,
            onSave = { saved ->
                commands = if (editorTarget == null) {
                    commands + saved
                } else {
                    commands.map { if (it.id == saved.id) saved else it }
                }
                onPersistCommands(commands)
                showEditor = false
                editorTarget = null
            },
            onDelete = editorTarget?.let { target ->
                {
                    commands = commands.filterNot { it.id == target.id }
                    onPersistCommands(commands)
                    showEditor = false
                    editorTarget = null
                }
            },
            onTest = if (shizukuGranted) {
                { command, callback ->
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ShellCommandExecutor.execute(command)
                        }
                        callback(result.exitCode, result.output)
                    }
                }
            } else {
                null
            },
        )
        return
    }

    if (resultDialog != null) {
        val state = resultDialog!!
        ShellCommandResultOverlaySheet(
            label = state.label,
            command = state.command,
            exitCode = state.exitCode,
            output = state.output,
            onDismissComplete = { resultDialog = null },
            onCopy = { onCopyOutput(state.output) },
        )
        return
    }

    OverlayAnimatedDialogContent(
        onDismissComplete = onDismissComplete,
        onWindowReady = onWindowReady,
        registerBackHandler = registerBackHandler,
    ) { requestDismiss ->
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                ShellStatusHeader(
                    shizukuGranted = shizukuGranted,
                    restartingService = false,
                    onRequestShizuku = {},
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (commands.isEmpty()) {
                    Text(
                        text = stringResource(R.string.shell_panel_overlay_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(commands, key = { it.id }) { item ->
                            ShellCommandCard(
                                item = item,
                                running = runningCommandId == item.id,
                                enabled = shizukuGranted && runningCommandId == null,
                                onEdit = {
                                    editorTarget = item
                                    showEditor = true
                                },
                                onRun = {
                                    if (!shizukuGranted) return@ShellCommandCard
                                    scope.launch {
                                        runningCommandId = item.id
                                        try {
                                            val result = withTimeout(SHELL_PANEL_OVERLAY_TIMEOUT_MS) {
                                                withContext(Dispatchers.IO) {
                                                    ShellCommandExecutor.execute(item)
                                                }
                                            }
                                            resultDialog = ShellPanelResultState(
                                                label = item.label,
                                                command = item.command,
                                                exitCode = result.exitCode,
                                                output = result.output,
                                            )
                                        } catch (_: Exception) {
                                            resultDialog = ShellPanelResultState(
                                                label = item.label,
                                                command = item.command,
                                                exitCode = -1,
                                                output = shellTimeoutMessage,
                                            )
                                        } finally {
                                            runningCommandId = null
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        editorTarget = null
                        showEditor = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.shell_panel_add))
                }
            }
        }
    }
}
