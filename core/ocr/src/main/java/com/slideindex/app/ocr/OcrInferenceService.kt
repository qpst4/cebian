package com.slideindex.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEnginePackIds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class OcrInferenceService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OcrModelRepository,
    private val catalogProvider: OcrModelCatalogProvider,
    private val nativeEnginePackCoordinator: NativeEnginePackCoordinator,
) {
    private companion object {
        private const val TAG = "OcrInferenceService"
    }

    private val mutex = Mutex()
    private var loadedModelId: String? = null
    private var loadedEngine: String? = null
    private var paddleOcr: PaddleOCR? = null
    private var openCvInitialized = false

    suspend fun recognizeBitmap(modelId: String, bitmap: Bitmap): String? {
        val entry = catalogProvider.findModel(modelId) ?: run {
            Log.w(TAG, "recognize skipped: unknown modelId=$modelId")
            return null
        }
        if (!repository.isInstalled(modelId)) {
            Log.w(TAG, "recognize skipped: model not installed modelId=$modelId")
            return null
        }
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                when (entry.engine) {
                    OcrEngines.PPOCR -> {
                        if (!nativeEnginePackCoordinator.ensurePackReady(NativeEnginePackIds.OCR)) {
                            Log.w(TAG, "recognize skipped: OCR native engine pack not ready")
                            return@withLock null
                        }
                        recognizeWithPpOcr(modelId, bitmap)
                    }
                    OcrEngines.MLKIT_CHINESE -> {
                        ensureEngine(modelId, entry.engine)
                        MlKitTextRecognizer.recognize(bitmap)
                    }
                    OcrEngines.TESSERACT -> {
                        if (!nativeEnginePackCoordinator.ensurePackReady(NativeEnginePackIds.OCR)) return@withLock null
                        ensureEngine(modelId, entry.engine)
                        TesseractTextRecognizer.recognize(
                            modelId = modelId,
                            dataRoot = repository.modelRoot(modelId),
                            bitmap = bitmap,
                        )
                    }
                    else -> null
                }
            }
        }
    }

    suspend fun release() {
        mutex.withLock {
            val modelId = loadedModelId
            releaseLoadedEngine()
            MlKitTextRecognizer.close()
            TesseractTextRecognizer.close(modelId)
        }
    }

    suspend fun invalidateIfModelChanged(selectedModelId: String?) {
        mutex.withLock {
            if (loadedModelId != null && loadedModelId != selectedModelId) {
                releaseLoadedEngine()
            }
        }
    }

    /** OCR 引擎包升级/删除后调用，避免继续复用旧的 PaddleOCR 会话。 */
    fun invalidateEngineBlocking() {
        kotlinx.coroutines.runBlocking(Dispatchers.Default) {
            mutex.withLock {
                releaseLoadedEngine()
            }
        }
    }

    private suspend fun recognizeWithPpOcr(modelId: String, bitmap: Bitmap): String? {
        ensureEngine(modelId, OcrEngines.PPOCR)
        val engine = paddleOcr ?: run {
            Log.w(TAG, "recognize skipped: PaddleOCR engine not initialized modelId=$modelId")
            return null
        }
        val result = engine.recognize(bitmap)
        val text = result.results
            .joinToString("\n") { item -> item.text }
            .trim()
        if (text.isEmpty()) {
            Log.i(TAG, "recognize empty: modelId=$modelId boxes=${result.lineCount}")
        }
        return text.takeIf { it.isNotEmpty() }
    }

    private suspend fun ensureEngine(modelId: String, engine: String) {
        if (loadedModelId == modelId && loadedEngine == engine) {
            if (engine == OcrEngines.PPOCR && paddleOcr != null) return
            if (engine != OcrEngines.PPOCR) return
        }
        releaseLoadedEngine()
        loadedModelId = modelId
        loadedEngine = engine

        if (engine != OcrEngines.PPOCR) return

        if (!ensureOpenCvReady()) {
            loadedModelId = null
            loadedEngine = null
            return
        }

        val det = repository.detModelFile(modelId)
        val rec = repository.recModelFile(modelId)
        val config = repository.recConfigFile(modelId)
        paddleOcr = try {
            PaddleOCR.createFromFiles(
                context = context,
                config = PaddleOCRConfig(),
                engineConfig = EngineConfig(numThreads = 4),
                detModelFilePath = det.absolutePath,
                recModelFilePath = rec.absolutePath,
                recConfigFilePath = config.absolutePath,
            )
        } catch (error: Throwable) {
            Log.e(TAG, "PaddleOCR init failed modelId=$modelId", error)
            loadedModelId = null
            loadedEngine = null
            null
        }
    }

    private fun ensureOpenCvReady(): Boolean {
        if (openCvInitialized) return true
        openCvInitialized = OpenCVUtils.init(context)
        if (!openCvInitialized) {
            Log.e(TAG, "OpenCV not ready; PP-OCR cannot run")
        }
        return openCvInitialized
    }

    private suspend fun releaseLoadedEngine() {
        paddleOcr?.release()
        paddleOcr = null
        if (loadedEngine == OcrEngines.MLKIT_CHINESE) {
            MlKitTextRecognizer.close()
        }
        if (loadedEngine == OcrEngines.TESSERACT) {
            TesseractTextRecognizer.close(loadedModelId)
        }
        loadedModelId = null
        loadedEngine = null
    }
}
