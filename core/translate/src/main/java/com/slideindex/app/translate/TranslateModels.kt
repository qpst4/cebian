package com.slideindex.app.translate

import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState

sealed class TranslateResult {
    data class Success(
        val translatedText: String,
        val detectedSourceLanguage: String? = null,
    ) : TranslateResult()

    data class Failure(val message: String) : TranslateResult()
}

enum class TranslateDownloadPhase {
    DOWNLOADING,
    READY,
    FAILED,
    CANCELLED,
}

enum class TranslateDownloadStep {
    ENGINE,
    LANGUAGE,
}

data class TranslateDownloadState(
    val languageCode: String,
    val phase: TranslateDownloadPhase,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
    val step: TranslateDownloadStep = TranslateDownloadStep.LANGUAGE,
    val stepIndex: Int = 1,
    val stepCount: Int = 1,
) {
    val progress: Float?
        get() = when {
            phase == TranslateDownloadPhase.READY -> 1f
            totalBytes != null && totalBytes > 0L ->
                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f)
            else -> null
        }
}

internal object TranslateEnginePackDownloadMapper {
    fun mapEngineState(
        engineState: NativeEnginePackDownloadState,
        languageCode: String,
        engineBytes: Long,
    ): TranslateDownloadState {
        val engineProgress = engineStepProgress(engineState)
        val combinedDownloaded = (engineProgress * engineBytes).toLong().coerceAtMost(engineBytes)
        return TranslateDownloadState(
            languageCode = languageCode,
            phase = mapPhase(engineState.phase),
            bytesDownloaded = combinedDownloaded,
            totalBytes = engineBytes.takeIf { it > 0L },
            step = TranslateDownloadStep.ENGINE,
            stepIndex = 1,
            stepCount = 2,
            errorMessage = engineState.errorMessage,
        )
    }

    fun withLanguageStep(
        state: TranslateDownloadState,
        engineBytes: Long,
        languageBytes: Long?,
    ): TranslateDownloadState {
        val combinedTotal = languageBytes?.let { engineBytes + it } ?: engineBytes.takeIf { it > 0L }
        return state.copy(
            bytesDownloaded = engineBytes + state.bytesDownloaded,
            totalBytes = combinedTotal,
            step = TranslateDownloadStep.LANGUAGE,
            stepIndex = 2,
            stepCount = 2,
        )
    }

    private fun engineStepProgress(state: NativeEnginePackDownloadState): Float =
        when (state.phase) {
            NativeEnginePackDownloadPhase.READY -> 1f
            NativeEnginePackDownloadPhase.VERIFYING -> 0.92f
            NativeEnginePackDownloadPhase.EXTRACTING -> 0.97f
            NativeEnginePackDownloadPhase.DOWNLOADING -> {
                val total = state.totalBytes
                if (total != null && total > 0L) {
                    (state.bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 0.9f)
                } else {
                    0f
                }
            }
            else -> 0f
        }

    private fun mapPhase(phase: NativeEnginePackDownloadPhase): TranslateDownloadPhase =
        when (phase) {
            NativeEnginePackDownloadPhase.DOWNLOADING -> TranslateDownloadPhase.DOWNLOADING
            NativeEnginePackDownloadPhase.VERIFYING,
            NativeEnginePackDownloadPhase.EXTRACTING,
            -> TranslateDownloadPhase.DOWNLOADING
            NativeEnginePackDownloadPhase.READY -> TranslateDownloadPhase.DOWNLOADING
            NativeEnginePackDownloadPhase.FAILED -> TranslateDownloadPhase.FAILED
            NativeEnginePackDownloadPhase.CANCELLED -> TranslateDownloadPhase.CANCELLED
        }
}
