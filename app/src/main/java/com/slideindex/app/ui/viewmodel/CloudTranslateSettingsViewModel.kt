package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.ocr.vlm.VlmFormulaOcrEngine
import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.ocr.vlm.VlmProvider
import com.slideindex.app.translate.CloudTranslateModelsCoordinator
import com.slideindex.app.translate.CloudTranslateRemoteModelsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CloudTranslateConnectionTestState(
    val testing: Boolean = false,
    val message: String? = null,
    val succeeded: Boolean? = null,
)

@HiltViewModel
class CloudTranslateSettingsViewModel @Inject constructor(
    val vlmConfigManager: VlmOcrConfigManager,
    private val modelsCoordinator: CloudTranslateModelsCoordinator,
    @ApplicationContext private val appContext: Context,
) : androidx.lifecycle.ViewModel() {

    val remoteModels: StateFlow<CloudTranslateRemoteModelsUiState> = modelsCoordinator.remoteModels

    private val _connectionTest = MutableStateFlow(CloudTranslateConnectionTestState())
    val connectionTest: StateFlow<CloudTranslateConnectionTestState> = _connectionTest.asStateFlow()

    fun loadRemoteModels(provider: VlmProvider, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            modelsCoordinator.loadRemoteModels(provider, forceRefresh)
        }
    }

    fun testConnection(provider: VlmProvider) {
        viewModelScope.launch {
            if (!vlmConfigManager.isProviderConfigured(provider)) {
                _connectionTest.value = CloudTranslateConnectionTestState(
                    testing = false,
                    message = null,
                    succeeded = false,
                )
                return@launch
            }
            _connectionTest.value = CloudTranslateConnectionTestState(testing = true, message = null, succeeded = null)
            val model = vlmConfigManager.getTranslateModel(provider)
            val (success, msg) = VlmFormulaOcrEngine.testConnection(
                context = appContext,
                apiKey = vlmConfigManager.getApiKey(provider),
                baseUrl = vlmConfigManager.getBaseUrl(provider),
                model = model,
            )
            _connectionTest.value = CloudTranslateConnectionTestState(
                testing = false,
                message = msg,
                succeeded = success,
            )
        }
    }

    fun clearConnectionTest() {
        _connectionTest.value = CloudTranslateConnectionTestState()
    }

    fun onApiCredentialsChanged(provider: VlmProvider) {
        clearConnectionTest()
        loadRemoteModels(provider, forceRefresh = true)
    }
}
