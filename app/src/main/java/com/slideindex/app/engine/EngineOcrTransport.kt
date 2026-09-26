package com.slideindex.app.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import com.slideindex.app.ocr.OcrRecognizeResult
import com.slideindex.app.ocr.OcrRecognizedLine
import com.slideindex.app.ocr.OcrRemoteTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 调用方进程里的 OCR 传输实现：绑定 `:engine` 的 [EngineOcrService]，把位图送过去、拿结果回来。
 *
 * 任何一步没走通（绑定失败、超时、异常）都返回 null / failure，让 [com.slideindex.app.ocr.OcrInferenceService]
 * 回退本地推理——宁可临时多占内存，也不能让取词功能失效。
 */
class EngineOcrTransport(context: Context) : OcrRemoteTransport {

    private val appContext = context.applicationContext

    @Volatile
    private var binder: IEngineOcr? = null

    @Volatile
    private var connection: ServiceConnection? = null

    override suspend fun recognizeBitmap(modelId: String, bitmap: Bitmap): OcrRecognizeResult? {
        val service = awaitService() ?: return null
        val deferred = CompletableDeferred<OcrRecognizeResult?>()
        val callback = object : IEngineOcrCallback.Stub() {
            override fun onTextResult(success: Boolean, textOrReason: String?) {
                deferred.complete(
                    if (success) {
                        OcrRecognizeResult.Success(textOrReason.orEmpty())
                    } else {
                        OcrRecognizeResult.Failure(textOrReason.orEmpty())
                    }
                )
            }

            override fun onLinesResult(linesJson: String?) = Unit
        }
        val sent = runCatching { service.recognizeBitmap(bitmap, modelId, callback) }.isSuccess
        if (!sent) return null
        return withTimeoutOrNull(RECOGNIZE_TIMEOUT_MS) { deferred.await() }
    }

    override suspend fun recognizePpOcrLines(
        modelId: String,
        bitmap: Bitmap,
    ): Result<List<OcrRecognizedLine>?> {
        val service = awaitService() ?: return Result.failure(IllegalStateException("engine not bound"))
        val deferred = CompletableDeferred<String?>()
        val callback = object : IEngineOcrCallback.Stub() {
            override fun onTextResult(success: Boolean, textOrReason: String?) = Unit

            override fun onLinesResult(linesJson: String?) {
                deferred.complete(linesJson)
            }
        }
        val sent = runCatching { service.recognizeLines(bitmap, modelId, callback) }.isSuccess
        if (!sent) return Result.failure(IllegalStateException("call failed"))
        val payload = withTimeoutOrNull(RECOGNIZE_TIMEOUT_MS) { deferred.await() }
            ?: return Result.failure(IllegalStateException("engine timeout"))
        return Result.success(EngineOcrService.decodeLines(payload))
    }

    private suspend fun awaitService(): IEngineOcr? {
        binder?.let { return it }
        return withContext(Dispatchers.Main.immediate) {
            binder?.let { return@withContext it }
            suspendCancellableCoroutine<IEngineOcr?> { cont ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val resolved = IEngineOcr.Stub.asInterface(service)
                        binder = resolved
                        if (cont.isActive) cont.resume(resolved)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        binder = null
                    }
                }
                connection = conn
                val bound = runCatching {
                    appContext.bindService(
                        Intent(appContext, EngineOcrService::class.java),
                        conn,
                        Context.BIND_AUTO_CREATE,
                    )
                }.getOrDefault(false)
                if (!bound && cont.isActive) cont.resume(null)
            }
        }
    }

    private companion object {
        private const val TAG = "EngineOcrTransport"
        /** 首次调用要把引擎加载起来，给足时间；超时就回退本地。 */
        private const val RECOGNIZE_TIMEOUT_MS = 25_000L
    }
}
