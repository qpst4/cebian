package com.slideindex.app.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import com.slideindex.app.util.finishWithoutTransition

import android.view.WindowManager

/** 壁纸模糊所需：跳转所有文件访问权限的瞬发跳板（仅供非 Activity 场景兼容调用）。 */
class WallpaperPermissionTrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        SystemWallpaperBlurHelper.requestWallpaperPermission(this)
        finishWithoutTransition()
    }

    companion object {
        fun launch(context: Context) {
            SystemWallpaperBlurHelper.requestWallpaperPermission(context)
        }

        fun ensurePermission(context: Context, onResult: ((Boolean) -> Unit)? = null) {
            if (SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)) {
                onResult?.invoke(true)
                return
            }
            launch(context)
        }
    }
}
