package com.slideindex.app.clipboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * FV-style clipboard change detection via logcat (requires [Manifest.permission.READ_LOGS] from ADB).
 */
object ClipboardLogcatWatcher {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var running = false
    private var worker: Thread? = null

    fun hasReadLogsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    fun start(context: Context, onClipboardChanged: () -> Unit) {
        if (!hasReadLogsPermission(context) || running) return
        val appContext = context.applicationContext
        running = true
        worker = Thread({
            runCatching {
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "brief", "ClipboardService:E", "*:S"))
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break
                        if (line.contains("Denying", ignoreCase = true) &&
                            line.contains("clipboard", ignoreCase = true)
                        ) {
                            mainHandler.post(onClipboardChanged)
                        }
                    }
                }
            }
            running = false
        }, "ClipboardLogcatWatcher").also { it.start() }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }
}
