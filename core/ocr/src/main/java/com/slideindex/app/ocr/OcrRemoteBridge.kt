package com.slideindex.app.ocr

import android.graphics.Bitmap

/**
 * OCR 推理的跨进程传输挂点。
 *
 * 目标：真正的推理（onnxruntime / opencv / tesseract 常驻）跑在 `:engine` 进程，
 * 调用方（取词、搜图、屏幕搜索）留在各自进程。
 *
 * core:ocr 不能反向依赖 app 模块，所以由 app 在启动时注入实现；未注入或调用失败时
 * [OcrInferenceService] 自动回退到本地推理，保证功能不受影响。
 */
interface OcrRemoteTransport {
    /** 返回 null 表示"没走通"，调用方回退本地推理。 */
    suspend fun recognizeBitmap(modelId: String, bitmap: Bitmap): OcrRecognizeResult?

    /**
     * `Result.success(lines)`：已由引擎进程处理（lines 可为 null = 非 PP-OCR/无结果）。
     * `Result.failure`：没走通，调用方回退本地推理。
     */
    suspend fun recognizePpOcrLines(
        modelId: String,
        bitmap: Bitmap,
    ): Result<List<OcrRecognizedLine>?>
}

object OcrRemoteBridge {
    @Volatile
    var transport: OcrRemoteTransport? = null
}
