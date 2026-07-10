package com.slideindex.app.otp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.slideindex.app.R

object OtpClipboardHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun copyCode(context: Context, code: String, showToast: Boolean = true) {
        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("otp", code))
        if (!showToast) return
        mainHandler.post {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.otp_copied_to_clipboard, code),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
