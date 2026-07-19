package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.translate.MlKitTranslateModelInstaller
import com.slideindex.app.translate.TranslateDownloadPhase
import com.slideindex.app.translate.TranslateDownloadState
import com.slideindex.app.translate.TranslateModelRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class TranslateSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    private val modelRepository: TranslateModelRepository,
    private val modelInstaller: MlKitTranslateModelInstaller,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val _installedLanguageCodes = MutableStateFlow(modelRepository.installedLanguageCodes())
    val installedLanguageCodes: StateFlow<Set<String>> = _installedLanguageCodes.asStateFlow()

    private val _downloadState = MutableStateFlow<TranslateDownloadState?>(null)
    val downloadState: StateFlow<TranslateDownloadState?> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refreshInstalled()
    }

    fun refreshInstalled() {
        _installedLanguageCodes.value = modelRepository.installedLanguageCodes()
    }

    fun setInstantTranslate(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setFloatBallInstantTranslate(enabled)
    }

    fun setTranslateEngine(engine: com.slideindex.app.settings.FloatBallTranslateEngine) =
        launchSettingsWrite {
            settingsRepository.setFloatBallTranslateEngine(engine)
        }

    fun setTranslateTargetLang(languageCode: String) = launchSettingsWrite {
        settingsRepository.setFloatBallTranslateTargetLang(languageCode)
    }

    fun setDownloadWifiOnly(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setOcrDownloadWifiOnly(enabled)
    }

    fun downloadLanguage(languageCode: String) {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            val wifiOnly = settings.value.ocrDownloadWifiOnly
            modelInstaller.download(languageCode, wifiOnly).collect { state ->
                _downloadState.value = state
                when (state.phase) {
                    TranslateDownloadPhase.READY -> refreshInstalled()
                    TranslateDownloadPhase.FAILED,
                    TranslateDownloadPhase.CANCELLED,
                    -> refreshInstalled()
                    else -> Unit
                }
            }
        }
    }

    fun deleteLanguage(languageCode: String) = viewModelScope.launch {
        modelInstaller.delete(languageCode)
        refreshInstalled()
    }
}
