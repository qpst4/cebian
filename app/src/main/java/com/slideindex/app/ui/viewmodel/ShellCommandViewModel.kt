package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellOutputHistoryRepository
import com.slideindex.app.util.ShellCommandRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ShellCommandViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
        val outcome = withContext(Dispatchers.IO) {
            ShellCommandRunner.execute(appContext, command)
        }
        return ShellCommandExecutorResult(
            exitCode = outcome.exitCode,
            output = outcome.output,
            expandedCommand = outcome.expandedCommand,
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
