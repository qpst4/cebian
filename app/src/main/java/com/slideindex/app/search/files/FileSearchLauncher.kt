package com.slideindex.app.search.files

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.service.LaunchTrampolineActivity

object FileSearchLauncher {
    fun open(context: Context, entry: DeviceFileEntry): Boolean {
        val mime = when {
            entry.isDirectory -> "vnd.android.document/directory"
            !entry.mimeType.isNullOrBlank() -> entry.mimeType
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(entry.uri, mime)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.search_panel_file_open_failed),
                Toast.LENGTH_SHORT,
            ).show()
            false
        } catch (_: SecurityException) {
            Toast.makeText(
                context,
                context.getString(R.string.search_panel_file_open_failed),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }

    fun share(context: Context, entry: DeviceFileEntry): Boolean {
        val mime = when {
            entry.isDirectory -> "vnd.android.document/directory"
            !entry.mimeType.isNullOrBlank() -> entry.mimeType
            else -> "*/*"
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, entry.uri)
            clipData = ClipData.newUri(context.contentResolver, entry.displayName, entry.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            sendIntent,
            context.getString(R.string.search_panel_file_preview_share),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(LaunchTrampolineActivity.createIntent(context, chooser))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.search_panel_file_open_failed),
                Toast.LENGTH_SHORT,
            ).show()
            false
        } catch (_: SecurityException) {
            Toast.makeText(
                context,
                context.getString(R.string.search_panel_file_open_failed),
                Toast.LENGTH_SHORT,
            ).show()
            false
        }
    }
}
