package com.slideindex.app.clipboardoverlay

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import com.slideindex.app.R
import com.slideindex.app.imageeditor.ImageEditorLaunchCache
import com.slideindex.app.imageeditor.SlideIndexImageEditorActivity
import com.slideindex.app.util.resolveActivityCompat

internal object ClipboardOverlayIntents {
    private const val REMOTE_COPY_ACTION = "android.intent.action.REMOTE_COPY"
    private const val GMS_PACKAGE = "com.google.android.gms"
    private const val LEGACY_REMOTE_COPY_COMPONENT =
        "com.google.android.gms/.nearby.sharing.ShareSheetActivity"
    private const val EXTRA_EDIT_SOURCE = "edit_source"
    private const val EDIT_SOURCE_CLIPBOARD = "clipboard"

    fun getTextEditorIntent(context: Context, text: CharSequence?): Intent {
        return Intent(context, ClipboardOverlayEditTextActivity::class.java).apply {
            putExtra(ClipboardOverlayEditTextActivity.EXTRA_TEXT, text?.toString().orEmpty())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    fun getShareIntent(clipData: ClipData, context: Context): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND)
        val uri = clipData.getItemAt(0).uri
        if (uri != null) {
            val mime = clipData.description.getMimeType(0) ?: "image/*"
            shareIntent.type = mime
            shareIntent.clipData = ClipData(
                "content",
                arrayOf(mime),
                ClipData.Item(uri),
            )
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            shareIntent.putExtra(Intent.EXTRA_TEXT, clipData.getItemAt(0).coerceToText(context).toString())
            shareIntent.type = "text/plain"
        }
        return Intent.createChooser(shareIntent, null)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun getImageEditIntent(uri: Uri, context: Context): Intent {
        val editorPackage = context.getString(R.string.clipboard_overlay_screenshot_editor)
        val editIntent = Intent(Intent.ACTION_EDIT)
        if (!TextUtils.isEmpty(editorPackage)) {
            editIntent.component = ComponentName.unflattenFromString(editorPackage)
        }
        editIntent.setDataAndType(uri, "image/*")
        editIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        editIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        editIntent.putExtra(EXTRA_EDIT_SOURCE, EDIT_SOURCE_CLIPBOARD)
        return editIntent
    }

    fun launchBuiltinImageEditor(context: Context, bitmap: android.graphics.Bitmap) {
        val editorPath = ImageEditorLaunchCache.put(context, bitmap)
        SlideIndexImageEditorActivity.launch(context, editorPath)
    }

    fun resolveRemoteCopyIntent(clipData: ClipData, context: Context): Intent? {
        val pm = context.packageManager
        val preferred = context.getString(R.string.clipboard_overlay_remote_copy_package)
        val candidates = buildList {
            add(remoteCopyIntent(clipData, packageName = GMS_PACKAGE))
            for (flat in listOf(preferred, LEGACY_REMOTE_COPY_COMPONENT).distinct()) {
                if (flat.isEmpty()) continue
                val component = ComponentName.unflattenFromString(flat) ?: continue
                add(remoteCopyIntent(clipData, component = component))
            }
        }
        return candidates.firstOrNull { pm.resolveActivityCompat(it) != null }
    }

    private fun remoteCopyIntent(
        clipData: ClipData,
        packageName: String? = null,
        component: ComponentName? = null,
    ): Intent {
        return Intent(REMOTE_COPY_ACTION).apply {
            this.clipData = clipData
            if (component != null) {
                this.component = component
            } else if (!packageName.isNullOrEmpty()) {
                setPackage(packageName)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }
}
