package com.slideindex.app.imageeditor

import android.graphics.Bitmap
import android.graphics.Rect
import com.slideindex.app.overlay.ScreenshotLayoutMeta

data class ImageEditorLaunchPayload(
    val bitmap: Bitmap,
    val screenRect: Rect?,
    val layoutMeta: ScreenshotLayoutMeta?,
    val pickReturnContext: ImageEditorPickReturnContext? = null,
)

object ImageEditorLaunchCache {
    @Volatile
    private var pending: ImageEditorLaunchPayload? = null

    fun put(
        context: android.content.Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
        pickReturnContext: ImageEditorPickReturnContext? = null,
    ): String? {
        pending?.bitmap?.takeIf { !it.isRecycled && it !== bitmap }?.recycle()
        pending?.pickReturnContext?.recycleImageCopies()
        pending = ImageEditorLaunchPayload(
            bitmap = bitmap,
            screenRect = screenRect?.let { Rect(it) },
            layoutMeta = layoutMeta?.copy(),
            pickReturnContext = pickReturnContext,
        )
        // 同时落盘并把路径交出去：编辑器跑在主进程，读不到本进程的静态缓存
        // （真机现象：悬浮球区域截图 → 点图片 → 编辑器报"图片加载失败"，而图片其实已经在 cache 里）。
        return runCatching {
            val dir = java.io.File(context.applicationContext.cacheDir, "image_editor_inbox")
                .apply { mkdirs() }
            val now = System.currentTimeMillis()
            dir.listFiles()?.filter { now - it.lastModified() > 10 * 60 * 1000L }?.forEach { it.delete() }
            val file = java.io.File(dir, "editor_$now.png")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            file.absolutePath
        }.onFailure { android.util.Log.w("ImageEditorLaunch", "write launch image failed", it) }.getOrNull()
    }

    fun take(): ImageEditorLaunchPayload? {
        val payload = pending
        pending = null
        return payload
    }

    fun clear() {
        pending?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        pending?.pickReturnContext?.recycleImageCopies()
        pending = null
    }
}
