package com.slideindex.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.graphics.Bitmap
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import com.slideindex.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FloatBallTextPick {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun deliverResult(context: Context?, text: String?, showEmptyToast: Boolean = true) {
        val appContext = context?.applicationContext ?: return
        mainHandler.post {
            if (text.isNullOrBlank()) {
                if (showEmptyToast) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.float_ball_text_not_found),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@post
            }
            copyText(appContext, text)
            Toast.makeText(
                appContext,
                appContext.getString(R.string.float_ball_text_copied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun copyText(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("float_ball_text", text))
    }

    fun readClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    fun translateText(context: Context, text: String) {
        val encoded = Uri.encode(text)
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://translate.google.com/?sl=auto&tl=zh-CN&text=$encoded".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                searchText(context, "translate $text")
            }
    }

    fun searchText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            }
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.float_ball_action_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onFailure {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            }
    }

    fun saveScreenshot(context: Context, bitmap: Bitmap): Boolean {
        val fileName = "float_ball_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SlideIndex")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.onFailure {
            resolver.delete(uri, null, null)
        }.getOrDefault(false)
    }

    fun shareScreenshot(context: Context, bitmap: Bitmap) {
        val fileName = "float_ball_share_${System.currentTimeMillis()}.png"
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SlideIndex/Share")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return
            }
        runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, context.getString(R.string.float_ball_action_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.onFailure {
            resolver.delete(uri, null, null)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
