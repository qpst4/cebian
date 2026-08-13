package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

open class ClipboardListenerService : IClipboardListenerService.Stub() {
    private val tag = "ClipboardListenerService"
    private var process: Process? = null
    private var stopped = false
    private var isRootMode = false
    private var useHiddenApi = false

    private fun buildCommandsWithSpace(commands: Array<String>): String {
        return commands.joinToString(" ") { command ->
            if (command.contains(' ')) "'$command'" else command
        }
    }

    override fun startListening(
        callback: IOnClipboardChanged,
        useRoot: Boolean,
        filePath: String,
        useHiddenApi: Boolean,
    ) {
        isRootMode = useRoot
        this.useHiddenApi = useHiddenApi
        val success = if (useHiddenApi) {
            tryRunProcess(callback, useRoot, filePath)
        } else {
            tryReadLogs(callback, useRoot)
        }
        if (!success) {
            Log.d(tag, "listener error, useHiddenApi=$useHiddenApi")
        }
    }

    private fun tryRunProcess(
        callback: IOnClipboardChanged,
        useRoot: Boolean,
        filePath: String,
    ): Boolean {
        val dirPath = File(filePath).parent
        val hostPid = android.os.Process.myPid()
        val commands = arrayOf(
            "app_process",
            "-Djava.class.path=$filePath",
            dirPath,
            LISTENER_MAIN_CLASS,
            hostPid.toString(),
        )
        Log.d(tag, "try run process: ${buildCommandsWithSpace(commands)}")
        return tryRunAndWait(callback, useRoot, commands, useLog = false)
    }

    private fun tryReadLogs(callback: IOnClipboardChanged, useRoot: Boolean): Boolean {
        val fmt = "yyyy-MM-dd HH:mm:ss.SSS"
        val timeStamp = SimpleDateFormat(fmt, Locale.US).format(Date())
        val commands = arrayOf("logcat", "-T", timeStamp, "ClipboardService:E", "*:S")
        Log.d(tag, "try read logs: ${buildCommandsWithSpace(commands)}")
        return tryRunAndWait(callback, useRoot, commands, useLog = true)
    }

    private fun tryRunAndWait(
        callback: IOnClipboardChanged,
        useRoot: Boolean,
        commands: Array<String>,
        useLog: Boolean,
    ): Boolean {
        if (stopped) return true
        var reader: BufferedReader? = null
        var error = false
        try {
            process = if (useRoot) {
                val processBuilder = ProcessBuilder(listOf("su"))
                processBuilder.redirectErrorStream(true)
                val proc = processBuilder.start()
                DataOutputStream(proc.outputStream).use { os ->
                    os.writeBytes("${buildCommandsWithSpace(commands)}\n")
                    os.flush()
                }
                proc
            } else {
                ProcessBuilder(commands.toList()).redirectErrorStream(true).start()
            }
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val current = line ?: continue
                Log.d(tag, current)
                if (useLog) {
                    callback.onChanged(current)
                } else {
                    when {
                        current.startsWith("onChanged:") -> callback.onChanged(null)
                        current.startsWith("fatal:") -> {
                            error = true
                            break
                        }
                        current.startsWith("eof:") -> {
                            error = false
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "read error in loop: ${e.message}", e)
            error = true
        } finally {
            reader?.close()
        }
        return error
    }

    override fun stopListening() {
        Log.d(tag, "stopListening")
        stopped = true
        stopProcess()
    }

    private fun stopProcess() {
        try {
            if (useHiddenApi) {
                process?.outputStream?.let { stream ->
                    DataOutputStream(stream).use { os ->
                        os.writeBytes("exit\n")
                        os.flush()
                    }
                }
            }
            val processTemp = process
            process = null
            processTemp?.outputStream?.close()
            processTemp?.inputStream?.close()
            processTemp?.destroyForcibly()
        } catch (e: Exception) {
            Log.w(tag, "stopProcess failed", e)
        }
    }

    override fun destroy() {
        Log.i(tag, "destroy")
        stopProcess()
        if (!isRootMode) {
            exitProcess(0)
        }
    }

    override fun exit() {
        destroy()
    }
}
