package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.nativeengine.NativeEnginePackCatalogProvider
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEnginePackDownloadController
import com.slideindex.app.nativeengine.NativeEnginePackDownloadPhase
import com.slideindex.app.nativeengine.NativeEnginePackDownloadState
import com.slideindex.app.nativeengine.NativeEnginePackDownloader
import com.slideindex.app.nativeengine.NativeEnginePackEntry
import com.slideindex.app.service.NativeEnginePackDownloadService
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.slideindex.app.settings.AppSettings

@HiltViewModel
class NativeEnginePackSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val catalogProvider: NativeEnginePackCatalogProvider,
    private val coordinator: NativeEnginePackCoordinator,
    private val downloader: NativeEnginePackDownloader,
) : ViewModel() {
    val packs: List<NativeEnginePackEntry> = catalogProvider.allPacks()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _installedPackIds = MutableStateFlow(loadInstalledPackIds())
    val installedPackIds: StateFlow<Set<String>> = _installedPackIds.asStateFlow()

    val downloadState: StateFlow<NativeEnginePackDownloadState?> =
        NativeEnginePackDownloadController.state

    init {
        viewModelScope.launch {
            var previousPhase: NativeEnginePackDownloadPhase? = null
            NativeEnginePackDownloadController.state.collect { state ->
                val phase = state?.phase
                if (phase == NativeEnginePackDownloadPhase.READY &&
                    previousPhase != NativeEnginePackDownloadPhase.READY
                ) {
                    refreshInstalled()
                } else if (
                    (phase == NativeEnginePackDownloadPhase.FAILED ||
                        phase == NativeEnginePackDownloadPhase.CANCELLED) &&
                    phase != previousPhase
                ) {
                    refreshInstalled()
                }
                previousPhase = phase
            }
        }
    }

    fun refreshInstalled() {
        _installedPackIds.value = loadInstalledPackIds()
    }

    fun downloadPack(packId: String) {
        if (downloader.isDownloading(packId)) return
        if (NativeEnginePackDownloadController.activePackId != null &&
            NativeEnginePackDownloadController.activePackId != packId
        ) {
            NativeEnginePackDownloadController.update(
                NativeEnginePackDownloadState(
                    packId = packId,
                    phase = NativeEnginePackDownloadPhase.FAILED,
                    errorMessage = "another_download_in_progress",
                ),
            )
            return
        }
        viewModelScope.launch {
            val wifiOnly = settings.value.ocrDownloadWifiOnly
            NativeEnginePackDownloadService.start(context, packId, wifiOnly)
        }
    }

    fun deletePack(packId: String) = viewModelScope.launch {
        coordinator.deletePack(packId)
        refreshInstalled()
    }

    fun setDownloadWifiOnly(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setOcrDownloadWifiOnly(enabled)
    }

    private fun loadInstalledPackIds(): Set<String> =
        packs.map { it.id }.filter { coordinator.isPackInstalled(it) }.toSet()
}
