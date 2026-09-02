package com.slideindex.app.diagnostic

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

open class DiagnosticLogListenerService : IDiagnosticLogService.Stub() {

    private val tag = "DiagnosticLogListener"
    private var process: Process? = null
    private var stopped = false

    override fun startListening(callback: IOnDiagnosticLogLine?, uid: Int) {
        if (callback == null) return
        stopped = false
        val fmt = "yyyy-MM-dd HH:mm:ss.SSS"
        val timeStamp = SimpleDateFormat(fmt, Locale.US).format(Date())
        val commands = arrayOf(
            "logcat",
            "-T",
            timeStamp,
            "-v",
            "threadtime",
            "--uid=$uid",
        )
        Log.d(tag, "startListening uid=$uid cmd=${commands.joinToString(" ")}")
        var reader: BufferedReader? = null
        try {
            process = ProcessBuilder(commands.toList())
                .redirectErrorStream(true)
                .start()
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            while (!stopped) {
                val current = reader.readLine() ?: break
                runCatching { callback.onLine(current) }
                    .onFailure { Log.w(tag, "callback failed: ${it.message}") }
            }
        } catch (error: Exception) {
            Log.w(tag, "logcat read failed: ${error.message}", error)
        } finally {
            reader?.close()
            stopProcess()
        }
    }

    override fun stopListening() {
        Log.d(tag, "stopListening")
        stopped = true
        stopProcess()
    }

    private fun stopProcess() {
        try {
            val processTemp = process
            process = null
            processTemp?.inputStream?.close()
            processTemp?.destroyForcibly()
        } catch (error: Exception) {
            Log.w(tag, "stopProcess failed", error)
        }
    }

    override fun destroy() {
        Log.i(tag, "destroy")
        stopProcess()
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }
}
