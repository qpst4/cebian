package com.slideindex.app.imageeditor

import android.graphics.Bitmap
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.PickContentKind
import com.slideindex.app.overlay.PickResultContentOrigin
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.pickresult.PickResultTextMode

/**
 * 从取词面板进入编辑器时缓存的会话；关闭按钮长按回面板时用于还原文案/来源等，并替换 [editedImageIndex] 的位图。
 */
data class ImageEditorPickReturnContext(
    val a11yText: String?,
    val ocrText: String?,
    val activeSource: PickResultTextSource,
    val ocrAvailable: Boolean,
    val ocrPending: Boolean,
    val ocrPreferSwitchOnComplete: Boolean,
    val a11ySourceEnabled: Boolean,
    val isShareImageOcr: Boolean,
    val barcodeResults: List<BarcodeScanResult>,
    val contentOrigin: PickResultContentOrigin,
    val contentKind: PickContentKind?,
    val imageCopies: List<Bitmap>,
    val editedImageIndex: Int,
    val ocrTextsByImageIndex: Map<Int, String>,
    val textMode: PickResultTextMode?,
) {
    fun recycleImageCopies() {
        imageCopies.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}
