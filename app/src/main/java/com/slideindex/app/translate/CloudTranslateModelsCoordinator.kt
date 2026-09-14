package com.slideindex.app.translate

import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.ocr.vlm.VlmProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CloudTranslateModelsCoordinator @Inject constructor(
    private val modelCatalogClient: CloudLlmModelCatalogClient,
    private val vlmConfigManager: VlmOcrConfigManager,
) {
    private val _remoteModels = MutableStateFlow(CloudTranslateRemoteModelsUiState())
    val remoteModels: StateFlow<CloudTranslateRemoteModelsUiState> = _remoteModels.asStateFlow()

    suspend fun loadRemoteModels(provider: VlmProvider, forceRefresh: Boolean = false) {
        val fallback = provider.presetTranslateModels
        if (!vlmConfigManager.isProviderConfigured(provider)) {
            _remoteModels.value = CloudTranslateRemoteModelsUiState(
                loading = false,
                modelIds = fallback,
                fromNetwork = false,
                errorCode = "api_key_missing",
            )
            return
        }
        _remoteModels.value = _remoteModels.value.copy(loading = true, errorCode = null)
        when (
            val result = modelCatalogClient.fetchTextModels(
                apiKey = vlmConfigManager.getApiKey(provider),
                baseUrl = vlmConfigManager.getBaseUrl(provider),
                bypassCache = forceRefresh,
            )
        ) {
            is CloudLlmModelListResult.Success -> {
                _remoteModels.value = CloudTranslateRemoteModelsUiState(
                    loading = false,
                    modelIds = result.modelIds,
                    fromNetwork = true,
                    errorCode = null,
                )
            }
            is CloudLlmModelListResult.Failure -> {
                _remoteModels.value = CloudTranslateRemoteModelsUiState(
                    loading = false,
                    modelIds = fallback,
                    fromNetwork = false,
                    errorCode = result.code,
                )
            }
        }
    }
}

data class CloudTranslateRemoteModelsUiState(
    val loading: Boolean = false,
    val modelIds: List<String> = emptyList(),
    val fromNetwork: Boolean = false,
    val errorCode: String? = null,
)
