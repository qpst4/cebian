package com.slideindex.app.overlay.searchpanel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import java.io.File

internal object SearchPanelAppShare {
    fun shareApk(context: Context, packageName: String) {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val apkFile = File(appInfo.sourceDir)
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            intent,
            context.getString(R.string.search_panel_app_quick_action_share),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
