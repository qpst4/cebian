package com.slideindex.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardPayload
import com.slideindex.app.clipboard.ClipboardReader
import com.slideindex.app.R
import com.slideindex.app.imageeditor.ImageEditorPickReturnContext
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FloatBallTextPick {
    private const val SHARE_CACHE_DIR = "float_ball_share"

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun shareCacheDir(context: Context): File =
        File(context.cacheDir, SHARE_CACHE_DIR).apply { mkdirs() }

    private fun deleteShareImageUri(context: Context, uri: Uri) {
        val name = uri.lastPathSegment ?: return
        File(shareCacheDir(context), name).delete()
    }

    fun deliverResult(context: Context?, text: String?, showEmptyToast: Boolean = true) {
        val appContext = context?.applicationContext ?: return
        mainHandler.post {
            if (text.isNullOrBlank()) {
                if (showEmptyToast) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.float_ball_text_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@post
            }
            copyText(appContext, text)
            Toast.makeText(
                appContext,
                appContext.getString(R.string.float_ball_text_copied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun copyText(context: Context, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("float_ball_text", text))
    }

    fun copyImage(context: Context, bitmap: Bitmap) {
        val uri = createShareImageUri(context, bitmap) ?: run {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "image", uri))
        Toast.makeText(context, R.string.float_ball_image_copied, Toast.LENGTH_SHORT).show()
    }

    fun readClipboardText(context: Context): String? = ClipboardReader.read(context)?.text

    fun readClipboardPayload(context: Context): ClipboardPayload? = ClipboardReader.read(context)

    fun translateText(context: Context, text: String) {
        val encoded = Uri.encode(text)
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://translate.google.com/?sl=auto&tl=zh-CN&text=$encoded".toUri()
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

    fun openUrl(
        context: Context,
        url: String,
        settings: AppSettings,
        longPressTriggered: Boolean = false
    ) {
        SearchEngineLauncher.launchOpenableUri(context, url, settings, longPressTriggered)
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

    fun shareTextTo(context: Context, text: String, target: ComponentName): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = target
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun saveScreenshot(context: Context, bitmap: Bitmap): Boolean =
        saveScreenshotReturningUri(context, bitmap) != null

    fun saveScreenshotReturningUri(
        context: Context,
        bitmap: Bitmap,
        ingestToHistory: Boolean = true,
    ): Uri? {
        val fileName = screenshotFileName()
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            if (ingestToHistory) {
                ClipboardAccess.repository?.ingestScreenshot(uri, fileName)
            }
            uri
        }.onFailure {
            resolver.delete(uri, null, null)
        }.getOrNull()
    }

    private fun screenshotFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "Screenshot_$timestamp.png"
    }

    fun createShareImageUri(context: Context, bitmap: Bitmap): Uri? {
        val fileName = "float_ball_share_${System.currentTimeMillis()}.png"
        val file = File(shareCacheDir(context), fileName)
        return runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.onFailure {
            file.delete()
        }.getOrNull()
    }

    fun shareScreenshot(context: Context, bitmap: Bitmap) {
        val uri = createShareImageUri(context, bitmap)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return
            }
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "image", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, context.getString(R.string.float_ball_action_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.onFailure {
            deleteShareImageUri(context, uri)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun viewScreenshot(
        context: Context,
        bitmap: Bitmap,
        targetPackage: String? = null,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
        pickReturnContext: ImageEditorPickReturnContext? = null,
    ): Boolean {
        if (targetPackage == com.slideindex.app.imageeditor.ImageEditorOpenTargets.BUILTIN_PACKAGE) {
            return openBuiltinImageEditor(context, bitmap, screenRect, layoutMeta, pickReturnContext)
        }
        if (targetPackage.isNullOrBlank()) {
            // "每次都询问" = 交给系统弹"打开方式"，不再自己画列表。
            return openSystemImageChooser(context, bitmap, screenRect, layoutMeta, pickReturnContext)
        }
        return viewScreenshotWithPackage(context, bitmap, targetPackage)
    }

    /**
     * "每次都询问"：用系统 chooser（ACTION_VIEW + createChooser）来问。
     *
     * 以前这里是自己画一个列表对话框，用的是 AppCompat 的 AlertDialog 配浮层那套主题
     * （`android:Theme.Material.Light.NoActionBar`）。那套主题里没有 AppCompat 的
     * `alertDialogTheme/listLayout`，列表布局解析成资源 id 0 → `inflate(0)` 抛
     * `Resources$NotFoundException`；而浮层和无障碍服务同在 `:overlay` 进程，
     * 等于点一下图片就把无障碍服务一起带走（真机 crash_20260927_003149 已确认）。
     *
     * 系统 chooser 由系统绘制，不受我们的主题影响，而且自带"仅此一次 / 始终"——
     * 这才是"每次都询问"本来的语义。
     */
    private fun openSystemImageChooser(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
        pickReturnContext: ImageEditorPickReturnContext? = null,
    ): Boolean {
        val uri = createShareImageUri(context, bitmap)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return false
            }
        val target = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            clipData = ClipData.newUri(context.contentResolver, "image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // 系统里一个能看图的 App 都没有时，chooser 会是空的：回退到内置编辑器，别让用户点了没反应。
        val hasExternalViewer = runCatching {
            context.packageManager.queryIntentActivities(target, 0).isNotEmpty()
        }.getOrDefault(true)
        if (!hasExternalViewer) {
            deleteShareImageUri(context, uri)
            return openBuiltinImageEditor(context, bitmap, screenRect, layoutMeta, pickReturnContext)
        }
        return runCatching {
            val chooser = Intent.createChooser(
                target,
                context.getString(R.string.float_ball_action_open_image),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        }.getOrElse {
            deleteShareImageUri(context, uri)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun viewScreenshotWithPackage(context: Context, bitmap: Bitmap, targetPackage: String): Boolean {
        val uri = createShareImageUri(context, bitmap)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return false
            }
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                clipData = ClipData.newUri(context.contentResolver, "image", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(targetPackage)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            deleteShareImageUri(context, uri)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openBuiltinImageEditor(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
        pickReturnContext: ImageEditorPickReturnContext? = null,
    ): Boolean {
        return runCatching {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
            val editorPath = com.slideindex.app.imageeditor.ImageEditorLaunchCache.put(
                context,
                copy,
                screenRect,
                layoutMeta,
                pickReturnContext,
            )
            com.slideindex.app.imageeditor.SlideIndexImageEditorActivity.launch(context, editorPath)
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareScreenshotTo(context: Context, bitmap: Bitmap, target: ComponentName): Boolean {
        val uri = createShareImageUri(context, bitmap)
            ?: run {
                Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                return false
            }
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "image", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                component = target
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            deleteShareImageUri(context, uri)
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
