package com.slideindex.app.stash

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.ScreenPinManager
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object StashCoordinator {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun addText(text: String, onDone: (Boolean) -> Unit = {}) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        scope.launch {
            onDone(repo.addText(text) != null)
        }
    }

    fun addImage(
        bitmap: Bitmap,
        pinDisplayWidthPx: Int? = null,
        pinDisplayHeightPx: Int? = null,
        onDone: (Boolean) -> Unit = {},
    ) {
        val repo = StashAccess.repository
        if (repo == null) {
            onDone(false)
            return
        }
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        if (copy == null) {
            onDone(false)
            return
        }
        scope.launch {
            onDone(
                repo.addImage(
                    bitmap = copy,
                    pinDisplayWidthPx = pinDisplayWidthPx,
                    pinDisplayHeightPx = pinDisplayHeightPx,
                ) != null,
            )
        }
    }

    fun pinImageFromStash(context: Context, entry: StashEntry, bitmap: Bitmap) {
        ScreenPinManager.pinFromStashImage(
            context = context,
            bitmap = bitmap,
            displayWidthPx = entry.pinDisplayWidthPx,
            displayHeightPx = entry.pinDisplayHeightPx,
        )
    }

    fun openStashPanel(context: Context) {
        FloatBallStashPanel.show(context)
    }

    fun pinTextToScreen(context: Context, text: String) {
        ScreenPinManager.pinText(context, text)
    }

    fun pinImageToScreen(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
    ) {
        ScreenPinManager.pinImage(context, bitmap, screenRect, layoutMeta)
    }
}
