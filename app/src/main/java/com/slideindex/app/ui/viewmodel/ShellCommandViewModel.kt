package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellOutputHistoryRepository
import com.slideindex.app.shell.ShellTemplateContextFactory
import com.slideindex.app.util.ShellCommandExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ShellCommandViewModel @Inject constructor(
    val historyRepository: ShellOutputHistoryRepository,
) : ViewModel() {
    val history = historyRepository.entries

    var pendingResult: ShellCommandResultState? = null
        private set

    fun setPendingResult(state: ShellCommandResultState?) {
        pendingResult = state
    }

    fun clearPendingResult() {
        pendingResult = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clear()
        }
    }

    suspend fun execute(command: ShellCommand): ShellCommandExecutorResult {
        val templateContext = ShellTemplateContextFactory.current()
        val expanded = command.copy(
            command = com.slideindex.app.shell.ShellCommandTemplate.expand(command.command, templateContext),
        )
        val result = withContext(Dispatchers.IO) {
            ShellCommandExecutor.execute(expanded)
        }
        historyRepository.append(
            label = command.label,
            command = expanded.command,
            exitCode = result.exitCode,
            output = result.output,
        )
        return ShellCommandExecutorResult(
            exitCode = result.exitCode,
            output = result.output,
            expandedCommand = expanded.command,
        )
    }
}

data class ShellCommandExecutorResult(
    val exitCode: Int,
    val output: String,
    val expandedCommand: String,
)

data class ShellCommandResultState(
    val label: String,
    val command: String,
    val exitCode: Int,
    val output: String,
)
