package com.slideindex.app.overlay.searchpanel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.search.files.FileSearchIndex
import com.slideindex.app.util.finishWithoutTransition

class FilePermissionTrampolineActivity : ComponentActivity() {

    private val requestLegacyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        deliverResult(isGranted)
    }

    private val manageAllFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        deliverResult(FileSearchIndex.hasPermission(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FileSearchIndex.hasPermission(this)) {
            deliverResult(true)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            runCatching {
                manageAllFilesLauncher.launch(intent)
            }.onFailure {
                manageAllFilesLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                )
            }
        } else {
            requestLegacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun deliverResult(granted: Boolean) {
        onPermissionResult?.invoke(granted)
        onPermissionResult = null
        finishWithoutTransition()
    }

    companion object {
        var onPermissionResult: ((Boolean) -> Unit)? = null

        fun launch(context: Context, onResult: (Boolean) -> Unit) {
            onPermissionResult = onResult
            val intent = Intent(context, FilePermissionTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
