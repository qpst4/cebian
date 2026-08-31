package com.slideindex.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 100% 纯本地离线异常捕获与诊断报告生成器。
 * 绝不使用任何第三方遥测 SDK，保护用户隐私。
 */
object LocalCrashHandler {
    private const val TAG = "LocalCrashHandler"
    private const val CRASH_FILE_NAME = "crash_log.txt"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        val appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashReport(appContext, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()

            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val info = buildString {
                appendLine("================ CRASH REPORT ================")
                appendLine("Time: $time")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                @Suppress("DEPRECATION")
                val threadId = thread.id
                appendLine("Thread: ${thread.name} (id: $threadId)")
                appendLine("Exception: ${throwable::class.java.name}: ${throwable.message}")
                appendLine("----------------- STACK TRACE -----------------")
                appendLine(stackTrace)
                appendLine("==============================================")
            }

            val file = File(context.filesDir, CRASH_FILE_NAME)
            file.writeText(info)
        }.onFailure { Log.e(TAG, "Failed to save crash report", it) }
    }

    fun readLastCrashReport(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        return if (file.exists() && file.isFile) {
            runCatching { file.readText() }.getOrNull()
        } else {
            null
        }
    }

    fun clearCrashReport(context: Context) {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * 生成完整的系统状态与诊断文本（用于一键复制排错）。
     */
    fun generateDiagnosticReport(context: Context): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()

        val versionName = packageInfo?.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: -1L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: -1L
        }

        val overlayGranted = Settings.canDrawOverlays(context)
        val accessibilityServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: ""
        val accessibilityGranted = accessibilityServices.contains(context.packageName)

        val notificationListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        val notificationListenerGranted = notificationListeners.contains(context.packageName)

        val lastCrash = readLastCrashReport(context)

        return buildString {
            appendLine("### Cebian 系统诊断与排错报告")
            appendLine("- **生成时间**: $time")
            appendLine("- **应用版本**: $versionName ($versionCode)")
            appendLine("- **设备型号**: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("- **系统版本**: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / Build ${Build.DISPLAY}")
            appendLine()
            appendLine("#### 权限与服务状态")
            appendLine("- 悬浮窗权限 (Overlay): ${if (overlayGranted) "✅ 已开启" else "❌ 未开启"}")
            appendLine("- 无障碍服务 (Accessibility): ${if (accessibilityGranted) "✅ 已开启" else "❌ 未开启"}")
            appendLine("- 通知监听服务 (Notification Listener): ${if (notificationListenerGranted) "✅ 已开启" else "❌ 未开启"}")
            appendLine()
            if (!lastCrash.isNullOrBlank()) {
                appendLine("#### 最近一次异常崩溃日志")
                appendLine("```text")
                appendLine(lastCrash.trim())
                appendLine("```")
            } else {
                appendLine("#### 崩溃日志记录")
                appendLine("✅ 暂无未捕获异常崩溃记录（运行稳定）")
            }
        }
    }
}
