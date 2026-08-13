package com.slideindex.app.overlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.util.finishWithoutTransition

/** 壁纸模糊所需：Android 13+ 申请 [READ_MEDIA_IMAGES]；更早版本走存储/所有文件访问。 */
class WallpaperPermissionTrampolineActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        deliverResult(SystemWallpaperBlurHelper.hasWallpaperAccessPermission(this))
    }

    private val manageAllFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        deliverResult(SystemWallpaperBlurHelper.hasWallpaperAccessPermission(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SystemWallpaperBlurHelper.hasWallpaperAccessPermission(this)) {
            deliverResult(true)
            return
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                requestPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
            else -> {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:$packageName".toUri(),
                )
                runCatching {
                    manageAllFilesLauncher.launch(intent)
                }.onFailure {
                    manageAllFilesLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    )
                }
            }
        }
    }

    private fun deliverResult(granted: Boolean) {
        onPermissionResult?.invoke(granted)
        onPermissionResult = null
        finishWithoutTransition()
    }

    companion object {
        var onPermissionResult: ((Boolean) -> Unit)? = null

        fun launch(context: Context, onResult: ((Boolean) -> Unit)? = null) {
            onPermissionResult = onResult
            val intent = Intent(context, WallpaperPermissionTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun ensurePermission(context: Context, onResult: ((Boolean) -> Unit)? = null) {
            if (SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)) {
                onResult?.invoke(true)
                return
            }
            launch(context, onResult)
        }
    }
}
