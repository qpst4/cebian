package com.slideindex.app.diagnostic

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.util.TaskManagerUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class DiagnosticLogConnectionState {
    Idle,
    Connecting,
    Streaming,
    PermissionRequired,
    ShizukuRequired,
    Error,
}

@Singleton
class DiagnosticLogController @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val tag = "DiagnosticLogController"
    private val mainHandler = Handler(Looper.getMainLooper())

    val serviceArgs: Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            appContext.packageName,
            DiagnosticLogUserService::class.java.name,
        ),
    ).daemon(false).processNameSuffix("diagnostic-log")

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    private val _connectionState = MutableStateFlow(DiagnosticLogConnectionState.Idle)
    val connectionState: StateFlow<DiagnosticLogConnectionState> = _connectionState.asStateFlow()

    private var listenerService: IDiagnosticLogService? = null
    private var listenerThread: Thread? = null
    private var serviceConnection: ServiceConnection? = null
    private var bindGeneration = 0

    private val logCallback = object : IOnDiagnosticLogLine.Stub() {
        override fun onLine(line: String?) {
            if (line.isNullOrBlank()) return
            _lines.tryEmit(line)
        }
    }

    fun connect() {
        if (_connectionState.value == DiagnosticLogConnectionState.Streaming ||
            _connectionState.value == DiagnosticLogConnectionState.Connecting
        ) {
            return
        }
        if (!TaskManagerUtil.hasShizukuPermission()) {
            _connectionState.value = DiagnosticLogConnectionState.ShizukuRequired
            return
        }
        if (!ClipboardPermissionHelper.hasReadLogsPermission(appContext)) {
            _connectionState.value = DiagnosticLogConnectionState.PermissionRequired
            return
        }
        bindGeneration++
        val generation = bindGeneration
        _connectionState.value = DiagnosticLogConnectionState.Connecting
        mainHandler.post {
            bindShizukuService(generation)
        }
    }

    fun disconnect() {
        bindGeneration++
        stopListeningLocally("disconnect")
        unbindService()
        _connectionState.value = DiagnosticLogConnectionState.Idle
    }

    fun refreshPermissionState() {
        when {
            !TaskManagerUtil.hasShizukuPermission() -> {
                disconnect()
                _connectionState.value = DiagnosticLogConnectionState.ShizukuRequired
            }
            !ClipboardPermissionHelper.hasReadLogsPermission(appContext) -> {
                disconnect()
                _connectionState.value = DiagnosticLogConnectionState.PermissionRequired
            }
            _connectionState.value == DiagnosticLogConnectionState.ShizukuRequired ||
                _connectionState.value == DiagnosticLogConnectionState.PermissionRequired -> {
                connect()
            }
        }
    }

    private fun bindShizukuService(generation: Int) {
        unbindService()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (generation != bindGeneration) {
                    runCatching { IDiagnosticLogService.Stub.asInterface(binder)?.stopListening() }
                    return
                }
                val service = IDiagnosticLogService.Stub.asInterface(binder)
                listenerService = service
                serviceConnection = this
                startPrivilegedListening(service, generation)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (generation != bindGeneration) return
                stopListeningLocally("service disconnected")
                _connectionState.value = DiagnosticLogConnectionState.Error
            }
        }
        serviceConnection = connection
        runCatching {
            if (!Shizuku.pingBinder()) {
                _connectionState.value = DiagnosticLogConnectionState.ShizukuRequired
                return
            }
            Shizuku.bindUserService(serviceArgs, connection)
        }.onFailure { error ->
            Log.w(tag, "bindUserService failed: ${error.message}", error)
            _connectionState.value = DiagnosticLogConnectionState.Error
        }
    }

    private fun startPrivilegedListening(service: IDiagnosticLogService, generation: Int) {
        listenerThread?.interrupt()
        listenerThread = Thread({
            try {
                if (generation != bindGeneration) return@Thread
                _connectionState.value = DiagnosticLogConnectionState.Streaming
                service.startListening(logCallback, appContext.applicationInfo.uid)
            } catch (error: Exception) {
                Log.w(tag, "privileged listening failed", error)
                if (generation == bindGeneration) {
                    _connectionState.value = DiagnosticLogConnectionState.Error
                }
            } finally {
                if (generation == bindGeneration) {
                    listenerService = null
                    if (_connectionState.value == DiagnosticLogConnectionState.Streaming) {
                        _connectionState.value = DiagnosticLogConnectionState.Idle
                    }
                }
            }
        }, "DiagnosticLogReader").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun stopListeningLocally(reason: String) {
        Log.d(tag, "stopListeningLocally: $reason")
        listenerThread?.interrupt()
        listenerThread = null
        runCatching { listenerService?.stopListening() }
        listenerService = null
    }

    private fun unbindService() {
        val connection = serviceConnection
        serviceConnection = null
        if (connection != null) {
            runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
        }
    }
}
