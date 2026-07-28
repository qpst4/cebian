package com.slideindex.app.ocr

import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState

internal object OcrEnginePackDownloadMapper {
    fun mapEngineState(
        engineState: NativeEnginePackDownloadState,
        modelId: String,
        engineBytes: Long,
        modelBytes: Long,
    ): OcrModelDownloadState {
        val combinedTotal = (engineBytes + modelBytes).takeIf { it > 0L }
        val engineProgress = engineStepProgress(engineState)
        val combinedDownloaded = (engineProgress * engineBytes).toLong().coerceAtMost(engineBytes)
        return OcrModelDownloadState(
            modelId = modelId,
            phase = mapPhase(engineState.phase),
            bytesDownloaded = combinedDownloaded,
            totalBytes = combinedTotal,
            step = OcrModelDownloadStep.ENGINE,
            stepIndex = 1,
            stepCount = 2,
            errorMessage = engineState.errorMessage,
        )
    }

    fun withModelStep(
        state: OcrModelDownloadState,
        engineBytes: Long,
        modelBytes: Long,
    ): OcrModelDownloadState {
        val combinedTotal = (engineBytes + modelBytes).takeIf { it > 0L }
        return state.copy(
            bytesDownloaded = engineBytes + state.bytesDownloaded,
            totalBytes = combinedTotal,
            step = OcrModelDownloadStep.MODEL,
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

    private fun mapPhase(phase: NativeEnginePackDownloadPhase): OcrModelDownloadPhase =
        when (phase) {
            NativeEnginePackDownloadPhase.DOWNLOADING -> OcrModelDownloadPhase.DOWNLOADING
            NativeEnginePackDownloadPhase.VERIFYING -> OcrModelDownloadPhase.VERIFYING
            NativeEnginePackDownloadPhase.EXTRACTING -> OcrModelDownloadPhase.FINALIZING
            NativeEnginePackDownloadPhase.READY -> OcrModelDownloadPhase.DOWNLOADING
            NativeEnginePackDownloadPhase.FAILED -> OcrModelDownloadPhase.FAILED
            NativeEnginePackDownloadPhase.CANCELLED -> OcrModelDownloadPhase.CANCELLED
        }
}
