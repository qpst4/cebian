package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.diagnostic.DiagnosticLogConnectionState
import com.slideindex.app.diagnostic.DiagnosticLogController
import com.slideindex.app.util.LocalCrashHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class IndexedLogLine(
    val id: Long,
    val text: String,
)

@HiltViewModel
class DiagnosticLogViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val logController: DiagnosticLogController,
) : ViewModel() {

    private val _connectionState = MutableStateFlow(DiagnosticLogConnectionState.Idle)
    val connectionState: StateFlow<DiagnosticLogConnectionState> = _connectionState.asStateFlow()

    private var nextLogId = 0L
    private val buffer = ArrayDeque<IndexedLogLine>(MAX_LOGS)
    private var logsDirty = false

    private val _logs = MutableStateFlow<ImmutableList<IndexedLogLine>>(persistentListOf())
    val logs: StateFlow<ImmutableList<IndexedLogLine>> = _logs.asStateFlow()

    private val _crashReports = MutableStateFlow(LocalCrashHandler.listCrashReports(appContext))
    val crashReports: StateFlow<List<LocalCrashHandler.CrashReportEntry>> = _crashReports.asStateFlow()

    private var collectJob: Job? = null
    private var flushJob: Job? = null
    private var connectionJob: Job? = null

    init {
        connectionJob = viewModelScope.launch {
            logController.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
    }

    fun connect() {
        if (collectJob?.isActive == true) return
        logController.connect()
        collectJob = viewModelScope.launch {
            logController.lines.collect { line ->
                appendLog(line)
            }
        }
        flushJob = viewModelScope.launch {
            while (isActive) {
                if (logsDirty) {
                    logsDirty = false
                    _logs.value = buffer.toPersistentList()
                }
                delay(FLUSH_INTERVAL_MS)
            }
        }
    }

    fun disconnect() {
        collectJob?.cancel()
        collectJob = null
        flushJob?.cancel()
        flushJob = null
        logController.disconnect()
    }

    fun refreshPermissionState() {
        logController.refreshPermissionState()
    }

    fun reloadCrashReports() {
        _crashReports.value = LocalCrashHandler.listCrashReports(appContext)
    }

    fun clearLogs() {
        buffer.clear()
        nextLogId = 0L
        logsDirty = false
        _logs.value = persistentListOf()
    }

    fun clearCrashReports() {
        LocalCrashHandler.clearCrashReports(appContext)
        reloadCrashReports()
    }

    fun readCrashReport(fileName: String): String? =
        LocalCrashHandler.readCrashReport(appContext, fileName)

    private fun appendLog(line: String) {
        buffer.addLast(IndexedLogLine(nextLogId++, line))
        while (buffer.size > MAX_LOGS) {
            buffer.removeFirst()
        }
        logsDirty = true
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    companion object {
        private const val MAX_LOGS = 800
        private const val FLUSH_INTERVAL_MS = 120L
    }
}
