package com.slideindex.app.imageeditor.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.slideindex.app.imageeditor.model.EditAction
import kotlin.math.max
import kotlin.math.roundToInt

object EditorExportEngine {
    fun exportBitmap(sourceBitmap: Bitmap, actions: List<EditAction>, scaleFactor: Float): Bitmap {
        val scale = max(scaleFactor, 0.1f)
        val output = Bitmap.createBitmap(
            max(1, (sourceBitmap.width * scale).roundToInt()),
            max(1, (sourceBitmap.height * scale).roundToInt()),
            Bitmap.Config.ARGB_8888,
        )
        val mosaicBitmap = EditorRenderUtils.generateMosaicBitmap(sourceBitmap)
        val canvas = Canvas(output)
        canvas.scale(scale, scale)
        canvas.drawBitmap(sourceBitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        val layer = canvas.saveLayer(0f, 0f, sourceBitmap.width.toFloat(), sourceBitmap.height.toFloat(), null)
        actions.forEach { action ->
            EditorRenderUtils.drawAction(canvas, action, mosaicBitmap)
        }
        canvas.restoreToCount(layer)
        if (!mosaicBitmap.isRecycled) mosaicBitmap.recycle()
        return output
    }
}
