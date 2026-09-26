package com.slideindex.app.engine

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.IBinder
import android.util.Log
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrRecognizeResult
import com.slideindex.app.ocr.OcrRecognizedLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `:engine` 进程里的 OCR 推理サービス（binder）。
 *
 * 推理本身仍由 [com.slideindex.app.ocr.OcrInferenceService] 完成——它在这个进程里被判为
 * engine 进程，不会再走跨进程传输，因此不会递归。
 */
class EngineOcrService : Service() {

    @Serializable
    private data class OcrLineDto(val l: Int, val t: Int, val r: Int, val b: Int, val text: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val binder = object : IEngineOcr.Stub() {
        override fun recognizeBitmap(bitmap: Bitmap?, modelId: String?, callback: IEngineOcrCallback?) {
            if (bitmap == null || modelId == null || callback == null) return
            scope.launch {
                val result = runCatching {
                    OcrDependencyAccess.inferenceService(this@EngineOcrService)
                        ?.recognizeBitmap(modelId, bitmap)
                }.getOrNull()
                runCatching {
                    when (result) {
                        is OcrRecognizeResult.Success -> callback.onTextResult(true, result.text)
                        is OcrRecognizeResult.Failure -> callback.onTextResult(false, result.reason)
                        null -> callback.onTextResult(false, "engine unavailable")
                    }
                }.onFailure { Log.w(TAG, "reply recognizeBitmap failed", it) }
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }

        override fun recognizeLines(bitmap: Bitmap?, modelId: String?, callback: IEngineOcrCallback?) {
            if (bitmap == null || modelId == null || callback == null) return
            scope.launch {
                val lines = runCatching {
                    OcrDependencyAccess.inferenceService(this@EngineOcrService)
                        ?.recognizePpOcrLines(modelId, bitmap)
                }.getOrNull()
                val payload = runCatching {
                    json.encodeToString(
                        lines.orEmpty().map { line ->
                            OcrLineDto(
                                l = line.bounds.left,
                                t = line.bounds.top,
                                r = line.bounds.right,
                                b = line.bounds.bottom,
                                text = line.text,
                            )
                        }
                    )
                }.getOrDefault("")
                runCatching { callback.onLinesResult(payload) }
                    .onFailure { Log.w(TAG, "reply recognizeLines failed", it) }
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "EngineOcrService"

        /** 供客户端把 JSON 解回行框（两边共用同一份 DTO 约定）。 */
        internal fun decodeLines(payload: String): List<OcrRecognizedLine> {
            if (payload.isBlank()) return emptyList()
            return runCatching {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<OcrLineDto>>(payload)
                    .map { OcrRecognizedLine(bounds = Rect(it.l, it.t, it.r, it.b), text = it.text) }
            }.getOrDefault(emptyList())
        }
    }
}
