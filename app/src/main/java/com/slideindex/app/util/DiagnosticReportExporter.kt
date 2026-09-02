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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DiagnosticReportExporter {

    private const val MAX_CLIPBOARD_BYTES = 500 * 1024

    private val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun copyOrShare(context: Context) {
        shareOrCopy(context)
    }

    fun shareOrCopy(context: Context) {
        val appContext = context.applicationContext
        val uiContext = context
        exportScope.launch {
            val outcome = runCatching { buildExportOutcome(appContext) }
                .getOrElse { ExportOutcome.Failed }
            withContext(Dispatchers.Main.immediate) {
                presentOutcome(uiContext, appContext, outcome)
            }
        }
    }

    private fun buildExportOutcome(appContext: Context): ExportOutcome {
        val fullReport = LocalCrashHandler.generateDiagnosticReport(appContext)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "cebian_diagnostic_$stamp.log"
        return ExportOutcome.Ready(
            fullReport = fullReport,
            fileName = fileName,
        )
    }

    private fun presentOutcome(
        uiContext: Context,
        appContext: Context,
        outcome: ExportOutcome,
    ) {
        when (outcome) {
            ExportOutcome.Failed -> {
                Toast.makeText(appContext, R.string.diagnostic_report_export_failed, Toast.LENGTH_LONG).show()
            }
            is ExportOutcome.Ready -> {
                if (shareReport(uiContext, outcome.fullReport, outcome.fileName)) {
                    Toast.makeText(appContext, R.string.diagnostic_report_shared, Toast.LENGTH_SHORT).show()
                    return
                }
                val truncationSuffix = appContext.getString(R.string.diagnostic_report_truncation_suffix)
                val clipboardText = truncateUtf8(outcome.fullReport, MAX_CLIPBOARD_BYTES, truncationSuffix)
                val truncated = outcome.fullReport.toByteArray(Charsets.UTF_8).size > MAX_CLIPBOARD_BYTES
                if (copyToClipboard(appContext, clipboardText)) {
                    val messageRes = if (truncated) {
                        R.string.diagnostic_report_copied_truncated
                    } else {
                        R.string.diagnostic_report_copied
                    }
                    Toast.makeText(appContext, messageRes, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appContext, R.string.diagnostic_report_export_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private sealed class ExportOutcome {
        data class Ready(
            val fullReport: String,
            val fileName: String,
        ) : ExportOutcome()

        data object Failed : ExportOutcome()
    }

    private fun copyToClipboard(context: Context, text: String): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        return runCatching {
            clipboard.setPrimaryClip(ClipData.newPlainText("Cebian Diagnostic Report", text))
            true
        }.getOrDefault(false)
    }

    private fun shareReport(context: Context, report: String, fileName: String): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val file = File(dir, fileName)
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
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostic_report_share_subject))
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
        if (budget <= 0) return suffix

        var cut = budget
        while (cut > 0 && (bytes[cut - 1].toInt() and 0xC0) == 0x80) {
            cut--
        }
        return String(bytes, 0, cut, Charsets.UTF_8) + suffix
    }
}
