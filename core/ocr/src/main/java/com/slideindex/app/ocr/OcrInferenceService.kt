package com.slideindex.app.ocr

import android.content.Context
import android.graphics.Bitmap
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
    private val mutex = Mutex()
    private var loadedModelId: String? = null
    private var loadedEngine: String? = null
    private var paddleOcr: PaddleOCR? = null
    private var openCvInitialized = false

    suspend fun recognizeBitmap(modelId: String, bitmap: Bitmap): String? {
        val entry = catalogProvider.findModel(modelId) ?: return null
        if (!repository.isInstalled(modelId)) return null
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                when (entry.engine) {
                    OcrEngines.PPOCR -> {
                        if (!nativeEnginePackCoordinator.ensurePackReady(NativeEnginePackIds.OCR)) return@withLock null
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
            paddleOcr?.release()
            paddleOcr = null
            MlKitTextRecognizer.close()
            TesseractTextRecognizer.close(loadedModelId)
            loadedModelId = null
            loadedEngine = null
        }
    }

    suspend fun invalidateIfModelChanged(selectedModelId: String?) {
        mutex.withLock {
            if (loadedModelId != null && loadedModelId != selectedModelId) {
                paddleOcr?.release()
                paddleOcr = null
                MlKitTextRecognizer.close()
                TesseractTextRecognizer.close(loadedModelId)
                loadedModelId = null
                loadedEngine = null
            }
        }
    }

    private suspend fun recognizeWithPpOcr(modelId: String, bitmap: Bitmap): String? {
        ensureEngine(modelId, OcrEngines.PPOCR)
        val engine = paddleOcr ?: return null
        val result = engine.recognize(bitmap)
        return result.results
            .joinToString("\n") { item -> item.text }
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private suspend fun ensureEngine(modelId: String, engine: String) {
        if (loadedModelId == modelId && loadedEngine == engine) {
            if (engine == OcrEngines.PPOCR && paddleOcr != null) return
            if (engine != OcrEngines.PPOCR) return
        }
        paddleOcr?.release()
        paddleOcr = null
        if (loadedEngine == OcrEngines.MLKIT_CHINESE) {
            MlKitTextRecognizer.close()
        }
        if (loadedEngine == OcrEngines.TESSERACT) {
            TesseractTextRecognizer.close(loadedModelId)
        }
        loadedModelId = modelId
        loadedEngine = engine

        if (engine != OcrEngines.PPOCR) return

        if (!openCvInitialized) {
            OpenCVUtils.init(context)
            openCvInitialized = true
        }

        val det = repository.detModelFile(modelId)
        val rec = repository.recModelFile(modelId)
        val config = repository.recConfigFile(modelId)
        paddleOcr = PaddleOCR.createFromFiles(
            context = context,
            config = PaddleOCRConfig(),
            engineConfig = EngineConfig(numThreads = 4),
            detModelFilePath = det.absolutePath,
            recModelFilePath = rec.absolutePath,
            recConfigFilePath = config.absolutePath,
        )
    }
}
