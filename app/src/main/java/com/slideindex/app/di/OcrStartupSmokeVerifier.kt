package com.slideindex.app.di

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.slideindex.app.BuildConfig
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrEngines
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class OcrStartupSmokeVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val applicationScope: CoroutineScope,
) {
    fun start() {
        if (!BuildConfig.DEBUG) return
        applicationScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.readSnapshot()
            val modelId = settings.floatBallOcrModelId
            if (!settings.floatBallOcrFallbackEnabled || modelId.isBlank()) {
                Log.i(TAG, "smoke skipped: ocr disabled or model not selected")
                return@launch
            }
            val entry = OcrDependencyAccess.catalogProvider(context)?.findModel(modelId)
            if (entry?.engine != OcrEngines.PPOCR) {
                Log.i(TAG, "smoke skipped: non-ppocr modelId=$modelId")
                return@launch
            }
            if (OcrDependencyAccess.modelRepository(context)?.isInstalled(modelId) != true) {
                Log.w(TAG, "smoke skipped: model not installed modelId=$modelId")
                return@launch
            }
            val inference = OcrDependencyAccess.inferenceService(context)
            if (inference == null) {
                Log.w(TAG, "smoke skipped: inference service unavailable")
                return@launch
            }
            val bitmap = createTestBitmap()
            val text = withContext(Dispatchers.Default) {
                runCatching { inference.recognizeBitmap(modelId, bitmap) }
                    .onFailure { error -> Log.e(TAG, "smoke recognize failed modelId=$modelId", error) }
                    .getOrNull()
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            if (text.isNullOrBlank()) {
                Log.e(TAG, "smoke FAILED: empty result modelId=$modelId")
            } else {
                Log.i(TAG, "smoke OK modelId=$modelId len=${text.length} sample=${text.take(32)}")
            }
        }
    }

    private fun createTestBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(480, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 56f
        }
        canvas.drawText("SlideIndex OCR", 24f, 82f, paint)
        return bitmap
    }

    private companion object {
        private const val TAG = "OcrStartupSmoke"
    }
}
