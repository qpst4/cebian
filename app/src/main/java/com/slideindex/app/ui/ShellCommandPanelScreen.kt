package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import com.slideindex.app.util.ShellCommandExecutor
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellCommandPanelScreen(
    settings: AppSettings,
    shizukuGranted: Boolean,
    onBack: () -> Unit,
    onSaveCommands: (List<ShellCommand>) -> Unit,
    onRequestShizuku: () -> Unit,
) {
    val context = LocalContext.current
    val shellTimeoutMessage = stringResource(R.string.shell_panel_execute_timeout)
    val scope = rememberCoroutineScope()
    var commands by remember(settings.shellCommands) { mutableStateOf(settings.shellCommands) }
    var editorTarget by remember { mutableStateOf<ShellCommand?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var runningCommandId by remember { mutableStateOf<String?>(null) }
    var resultDialog by remember { mutableStateOf<ShellResultDialogState?>(null) }

    fun runEditorTest(command: ShellCommand, callback: (Int, String) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShellCommandExecutor.execute(command)
            }
            callback(result.exitCode, result.output)
        }
    }

    BackHandler(enabled = showEditor) {
        showEditor = false
        editorTarget = null
    }
    BackHandler(enabled = resultDialog != null) {
        resultDialog = null
    }
    BackHandler(onBack = onBack)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { SettingsAppBarTitle(stringResource(R.string.shell_panel_title)) },
                subtitle = { Text(stringResource(R.string.shell_panel_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editorTarget = null
                    showEditor = true
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.shell_panel_add))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            var restartingService by remember { mutableStateOf(false) }
            ShellStatusHeader(
                shizukuGranted = shizukuGranted,
                restartingService = restartingService,
                onRequestShizuku = onRequestShizuku,
                onRestartService = {
                    if (!restartingService) {
                        restartingService = true
                        scope.launch {
                            val api = withContext(Dispatchers.IO) {
                                runCatching { TaskManagerUtil.restartShellService() }.getOrDefault(-1)
                            }
                            restartingService = false
                            android.widget.Toast.makeText(
                                context,
                                if (api >= ShizukuUserServiceHost.SERVICE_BUILD) {
                                    "Shell 服务已重启（build=$api）"
                                } else if (api > 0) {
                                    "Shell 服务已连接（api=$api），若命令仍失败请重启 Shizuku"
                                } else {
                                    "Shell 服务重启失败，请完全关闭 Shizuku 后重试"
                                },
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (commands.isEmpty()) {
                Text(
                    text = stringResource(R.string.shell_panel_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            if (!shizukuGranted) {
                                onRequestShizuku()
                                return@ShellCommandCard
                            }
                            scope.launch {
                                runningCommandId = item.id
                                try {
                                    val result = withTimeout(SHELL_UI_TIMEOUT_MS) {
                                        withContext(Dispatchers.IO) {
                                            ShellCommandExecutor.execute(item)
                                        }
                                    }
                                    resultDialog = ShellResultDialogState(
                                        label = item.label,
                                        command = item.command,
                                        exitCode = result.exitCode,
                                        output = result.output,
                                    )
                                } catch (_: Exception) {
                                    resultDialog = ShellResultDialogState(
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
    }

    AnimatedFullScreenOverlay(visible = showEditor) {
        ShellCommandEditorScreen(
            initial = editorTarget,
            shizukuGranted = shizukuGranted,
            onBack = {
                showEditor = false
                editorTarget = null
            },
            onSave = { saved ->
                commands = if (editorTarget == null) {
                    commands + saved
                } else {
                    commands.map { if (it.id == saved.id) saved else it }
                }
                onSaveCommands(commands)
                showEditor = false
                editorTarget = null
            },
            onDelete = editorTarget?.let { target ->
                {
                    commands = commands.filterNot { it.id == target.id }
                    onSaveCommands(commands)
                    showEditor = false
                    editorTarget = null
                }
            },
            onTest = if (shizukuGranted) {
                { command, callback -> runEditorTest(command, callback) }
            } else {
                null
            },
        )
    }

    AnimatedFullScreenOverlay(visible = resultDialog != null) {
        resultDialog?.let { state ->
            ShellResultScreen(
                label = state.label,
                command = state.command,
                exitCode = state.exitCode,
                output = state.output,
                onBack = { resultDialog = null },
                onCopy = { copyToClipboard(context, state.output) },
            )
        }
    }
    }
}

@Composable
fun ShellCommandEntryCard(
    commandCount: Int,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { Icon(Icons.Default.Code, contentDescription = null) },
        title = stringResource(R.string.shell_panel_entry_title),
        subtitle = if (commandCount > 0) {
            stringResource(R.string.shell_panel_entry_desc_count, commandCount)
        } else {
            stringResource(R.string.shell_panel_entry_desc)
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShellStatusHeader(
    shizukuGranted: Boolean,
    restartingService: Boolean = false,
    onRequestShizuku: () -> Unit,
    onRestartService: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shell_panel_shizuku_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (shizukuGranted) {
                        stringResource(R.string.shell_panel_shizuku_active)
                    } else {
                        stringResource(R.string.shell_panel_shizuku_inactive)
                    },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            if (!shizukuGranted) {
                TextButton(onClick = onRequestShizuku) {
                    Text(stringResource(R.string.permission_shizuku_grant))
                }
            } else {
                if (restartingService) {
                    LoadingIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                    )
                } else {
                    IconButton(onClick = onRestartService) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重启 Shell 服务",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShellCommandCard(
    item: ShellCommand,
    running: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onRun: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled || running, onClick = onEdit),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.command,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (running) {
                LoadingIndicator(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp),
                )
            } else {
                IconButton(onClick = onRun, enabled = enabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.shell_panel_run),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private const val SHELL_UI_TIMEOUT_MS = 40_000L

private data class ShellResultDialogState(
    val label: String,
    val command: String,
    val exitCode: Int,
    val output: String,
)

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", text))
}
