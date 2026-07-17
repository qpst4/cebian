package com.slideindex.app.ui



import android.content.ClipData

import android.content.ClipboardManager

import android.content.Context

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.grid.items

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Add

import androidx.compose.material.icons.filled.Code

import androidx.compose.material.icons.filled.History

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.FloatingActionButton

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.MediumFlexibleTopAppBar

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

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

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.slideindex.app.R

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.shell.ShellCommand

import com.slideindex.app.shell.ShellTemplateContextFactory

import com.slideindex.app.shizuku.ShizukuUserServiceHost

import com.slideindex.app.ui.settings.components.PermissionGatedFeature

import com.slideindex.app.ui.settings.components.SettingsHintText

import com.slideindex.app.ui.settings.components.SettingsSectionTitle

import com.slideindex.app.ui.viewmodel.ShellCommandViewModel

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

    shellViewModel: ShellCommandViewModel,

) {

    val context = LocalContext.current

    val shellTimeoutMessage = stringResource(R.string.shell_panel_execute_timeout)

    val restartSuccessTemplate = stringResource(R.string.shell_panel_restart_success, "__API__")
    val restartOutdatedTemplate = stringResource(R.string.shell_panel_restart_outdated, "__API__")
    val restartFailedMessage = stringResource(R.string.shell_panel_restart_failed)

    val scope = rememberCoroutineScope()

    var commands by remember(settings.shellCommands) { mutableStateOf(settings.shellCommands) }

    var editorTarget by remember { mutableStateOf<ShellCommand?>(null) }

    var showEditor by remember { mutableStateOf(false) }

    var runningCommandId by remember { mutableStateOf<String?>(null) }

    var resultDialog by remember { mutableStateOf<ShellResultDialogState?>(null) }

    var showHistory by remember { mutableStateOf(false) }

    val historyEntries by shellViewModel.historyRepository.entries.collectAsStateWithLifecycle()



    fun runEditorTest(command: ShellCommand, callback: (Int, String) -> Unit) {

        scope.launch {

            val result = withContext(Dispatchers.IO) {

                ShellCommandExecutor.execute(command, ShellTemplateContextFactory.current())

            }

            callback(result.exitCode, result.output)

        }

    }



    fun runCommand(item: ShellCommand) {

        if (!shizukuGranted) {

            onRequestShizuku()

            return

        }

        scope.launch {

            runningCommandId = item.id

            try {

                val result = withTimeout(SHELL_UI_TIMEOUT_MS) {

                    shellViewModel.execute(item)

                }

                resultDialog = ShellResultDialogState(

                    label = item.label,

                    command = result.expandedCommand,

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

    }



    BackHandler(enabled = showEditor) {

        showEditor = false

        editorTarget = null

    }

    BackHandler(enabled = resultDialog != null) {

        resultDialog = null

    }

    BackHandler(enabled = showHistory) {

        showHistory = false

    }

    BackHandler(onBack = onBack)



    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(

            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),

            topBar = {

                MediumFlexibleTopAppBar(

                    title = { SettingsAppBarTitle(stringResource(R.string.shell_panel_title)) },

                    navigationIcon = {

                        IconButton(onClick = onBack) {

                            Icon(

                                Icons.AutoMirrored.Filled.ArrowBack,

                                contentDescription = stringResource(R.string.cd_navigate_back),

                            )

                        }

                    },

                    actions = {

                        IconButton(onClick = { showHistory = true }) {

                            Icon(

                                Icons.Default.History,

                                contentDescription = stringResource(R.string.shell_panel_history_action),

                            )

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

                    .padding(horizontal = 20.dp, vertical = 12.dp),

                verticalArrangement = Arrangement.spacedBy(16.dp),

            ) {

                var restartingService by remember { mutableStateOf(false) }

                ShellShizukuStatusCard(

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

                                val message = when {

                                    api >= ShizukuUserServiceHost.SERVICE_BUILD -> {

                                        restartSuccessTemplate.replace("__API__", api.toString())

                                    }

                                    api > 0 -> {

                                        restartOutdatedTemplate.replace("__API__", api.toString())

                                    }

                                    else -> {

                                        restartFailedMessage

                                    }

                                }

                                android.widget.Toast.makeText(

                                    context,

                                    message,

                                    android.widget.Toast.LENGTH_SHORT,

                                ).show()

                            }

                        }

                    },

                )



                PermissionGatedFeature(

                    granted = shizukuGranted,

                    permissionTitle = stringResource(R.string.permission_shizuku_title),

                    permissionDescription = stringResource(R.string.permission_shizuku_desc),

                    onGrant = onRequestShizuku,

                    grantLabel = stringResource(R.string.permission_shizuku_grant),

                ) {

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        SettingsSectionTitle(

                            if (commands.isEmpty()) {

                                stringResource(R.string.shell_panel_commands_section)

                            } else {

                                stringResource(R.string.shell_panel_commands_section_count, commands.size)

                            },

                        )

                        SettingsHintText(stringResource(R.string.shell_panel_template_hint))

                        if (commands.isEmpty()) {

                        SettingsCard {

                            Column(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .padding(vertical = 24.dp),

                                horizontalAlignment = Alignment.CenterHorizontally,

                                verticalArrangement = Arrangement.spacedBy(8.dp),

                            ) {

                                Icon(

                                    imageVector = Icons.Default.Code,

                                    contentDescription = null,

                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,

                                )

                                Text(

                                    text = stringResource(R.string.shell_panel_empty_hint),

                                    style = MaterialTheme.typography.bodyMedium,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                                    textAlign = TextAlign.Center,

                                    modifier = Modifier.padding(horizontal = 16.dp),

                                )

                            }

                        }

                    } else {

                        LazyVerticalGrid(

                            columns = GridCells.Fixed(2),

                            modifier = Modifier

                                .fillMaxWidth()

                                .weight(1f),

                            contentPadding = PaddingValues(bottom = 4.dp),

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

                                    onRun = { runCommand(item) },

                                )

                            }

                        }

                    }

                    }

                }



                if (historyEntries.isNotEmpty()) {

                    SettingsHintText(

                        pluralStringResource(
                            R.plurals.shell_panel_history_entry_desc_count,
                            historyEntries.size,
                            historyEntries.size,
                        ),

                    )

                }



                Spacer(modifier = Modifier.height(88.dp))

            }

        }



        AnimatedFullScreenOverlay(visible = showHistory) {

            ShellOutputHistoryScreen(

                repository = shellViewModel.historyRepository,

                onBack = { showHistory = false },

                onClear = shellViewModel::clearHistory,

            )

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

        icon = { label -> Icon(Icons.Default.Code, contentDescription = label) },

        title = stringResource(R.string.shell_panel_entry_title),

        subtitle = if (commandCount > 0) {

            stringResource(R.string.shell_panel_entry_desc_count, commandCount)

        } else {

            stringResource(R.string.shell_panel_entry_desc)

        },

        onClick = onClick,

    )

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


