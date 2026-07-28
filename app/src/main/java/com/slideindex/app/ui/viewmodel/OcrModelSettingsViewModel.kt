package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.nativeengine.NativeEnginePackCatalogProvider
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEnginePackIds
import com.slideindex.app.ocr.OcrInferenceService
import com.slideindex.app.ocr.OcrModelCatalogProvider
import com.slideindex.app.ocr.OcrModelDownloadController
import com.slideindex.app.ocr.OcrModelDownloadPhase
import com.slideindex.app.ocr.OcrModelDownloadState
import com.slideindex.app.ocr.OcrModelDownloader
import com.slideindex.app.ocr.OcrModelEntry
import com.slideindex.app.ocr.OcrModelRepository
import com.slideindex.app.service.OcrModelDownloadService
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OcrModelSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext private val context: Context,
    private val catalogProvider: OcrModelCatalogProvider,
    private val modelRepository: OcrModelRepository,
    private val downloader: OcrModelDownloader,
    private val inferenceService: OcrInferenceService,
    private val nativeEnginePackCoordinator: NativeEnginePackCoordinator,
    private val nativeEnginePackCatalogProvider: NativeEnginePackCatalogProvider,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    val catalogModels: List<OcrModelEntry> = catalogProvider.allModels()

    val ocrEngineInstalled: Boolean
        get() = nativeEnginePackCoordinator.isPackInstalled(NativeEnginePackIds.OCR)

    val ocrEngineSizeBytes: Long
        get() = nativeEnginePackCatalogProvider.findPack(NativeEnginePackIds.OCR)?.sizeBytes ?: 0L

    private val _installedModelIds = MutableStateFlow(modelRepository.installedModelIds())
    val installedModelIds: StateFlow<Set<String>> = _installedModelIds.asStateFlow()

    val downloadState: StateFlow<OcrModelDownloadState?> = OcrModelDownloadController.state

    init {
        refreshInstalled()
        viewModelScope.launch {
            var previousPhase: OcrModelDownloadPhase? = null
            OcrModelDownloadController.state.collect { state ->
                val phase = state?.phase
                if (phase == OcrModelDownloadPhase.READY &&
                    previousPhase != OcrModelDownloadPhase.READY
                ) {
                    refreshInstalled()
                    selectModel(state.modelId)
                } else if (
                    (phase == OcrModelDownloadPhase.FAILED ||
                        phase == OcrModelDownloadPhase.CANCELLED) &&
                    phase != previousPhase
                ) {
                    refreshInstalled()
                }
                previousPhase = phase
            }
        }
    }

    fun refreshInstalled() {
        _installedModelIds.value = modelRepository.installedModelIds()
    }

    fun selectModel(modelId: String) = launchSettingsWrite {
        settingsRepository.setFloatBallOcrModelId(modelId).also {
            inferenceService.invalidateIfModelChanged(modelId)
        }
    }

    fun clearSelectedModel() = launchSettingsWrite {
        settingsRepository.setFloatBallOcrModelId("").also {
            inferenceService.invalidateIfModelChanged(null)
        }
    }

    fun setDownloadWifiOnly(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOcrDownloadWifiOnly(enabled)
    }

    fun downloadModel(modelId: String) {
        if (downloader.isDownloading(modelId)) return
        if (OcrModelDownloadController.activeModelId != null &&
            OcrModelDownloadController.activeModelId != modelId
        ) {
            OcrModelDownloadController.update(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.FAILED,
                    errorMessage = "another_download_in_progress",
                ),
            )
            return
        }
        val wifiOnly = settings.value.ocrDownloadWifiOnly
        OcrModelDownloadService.start(context, modelId, wifiOnly)
    }

    fun deleteOcrEngine() = viewModelScope.launch {
        nativeEnginePackCoordinator.deletePack(NativeEnginePackIds.OCR)
        inferenceService.invalidateIfModelChanged(settings.value.floatBallOcrModelId.ifBlank { null })
    }

    fun deleteModel(modelId: String) = viewModelScope.launch {
        downloader.deleteModel(modelId)
        refreshInstalled()
        if (settings.value.floatBallOcrModelId == modelId) {
            clearSelectedModel()
        }
        inferenceService.invalidateIfModelChanged(settings.value.floatBallOcrModelId.ifBlank { null })
    }
}
