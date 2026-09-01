package com.slideindex.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.slideindex.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReportExporter {

    private const val MAX_CLIPBOARD_BYTES = 500 * 1024

    fun copyOrShare(context: Context) {
        val appContext = context.applicationContext
        val fullReport = LocalCrashHandler.generateDiagnosticReport(appContext)
        val truncationSuffix = appContext.getString(R.string.diagnostic_report_truncation_suffix)
        val clipboardText = truncateUtf8(fullReport, MAX_CLIPBOARD_BYTES, truncationSuffix)
        val truncated = fullReport.toByteArray(Charsets.UTF_8).size > MAX_CLIPBOARD_BYTES

        if (copyToClipboard(appContext, clipboardText)) {
            val messageRes = if (truncated) {
                R.string.diagnostic_report_copied_truncated
            } else {
                R.string.diagnostic_report_copied
            }
            Toast.makeText(appContext, messageRes, Toast.LENGTH_SHORT).show()
            return
        }

        if (shareReport(context, fullReport)) {
            Toast.makeText(appContext, R.string.diagnostic_report_shared_fallback, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(appContext, R.string.diagnostic_report_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun copyToClipboard(context: Context, text: String): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("Cebian Diagnostic Report", text))
            true
        }.getOrDefault(false)
    }

    private fun shareReport(context: Context, report: String): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "cebian_diagnostic_$stamp.txt")
            file.writeText(report, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Cebian Diagnostic Report")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                context.getString(R.string.diagnostic_report_share_chooser_title),
            )
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private fun truncateUtf8(text: String, maxBytes: Int, suffix: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return text
        val suffixBytes = suffix.toByteArray(Charsets.UTF_8)
        val budget = (maxBytes - suffixBytes.size).coerceAtLeast(0)
        var end = text.length
        while (end > 0 && text.substring(0, end).toByteArray(Charsets.UTF_8).size > budget) {
            end--
        }
        return text.substring(0, end) + suffix
    }
}
