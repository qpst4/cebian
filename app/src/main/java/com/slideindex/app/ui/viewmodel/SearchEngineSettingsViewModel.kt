package com.slideindex.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.slideindex.app.R
import com.slideindex.app.search.SearchEngineIconStorage
import com.slideindex.app.search.SearchEngineImportResult
import com.slideindex.app.search.SearchEngineImporter
import com.slideindex.app.search.SearchEngineValidator
import com.slideindex.app.settings.AggregatedImageSearchEngineConfig
import com.slideindex.app.settings.AggregatedImageSearchEnginePreferencesStore
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchIconType
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.SearchEngineEditorResult
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

data class SearchEngineImportPreviewState(
    val uri: Uri,
    val sourceLabel: String,
    val importedCount: Int,
    val skippedCount: Int,
    val mergedEngines: List<SearchEngineConfig>,
)

@HiltViewModel
class SearchEngineSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val _importPreviewState = MutableStateFlow<SearchEngineImportPreviewState?>(null)
    val importPreviewState: StateFlow<SearchEngineImportPreviewState?> = _importPreviewState.asStateFlow()

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            val existing = settings.value.searchEngines
            SearchEngineImporter.importFromUri(
                context = appContext,
                uri = uri,
                existing = existing,
                replaceExisting = false,
            ).fold(
                onSuccess = { result ->
                    _importPreviewState.value = result.toPreviewState(uri)
                },
                onFailure = { error ->
                    userMessageBus.showError(
                        appContext.getString(
                            R.string.search_engine_import_failed,
                            error.message.orEmpty(),
                        ),
                    )
                },
            )
        }
    }

    fun dismissImportPreview() {
        _importPreviewState.value = null
    }

    fun confirmImport(replaceExisting: Boolean) {
        val preview = _importPreviewState.value ?: return
        viewModelScope.launch {
            val existing = settings.value.searchEngines
            val result = if (replaceExisting) {
                SearchEngineImporter.importFromUri(
                    context = appContext,
                    uri = preview.uri,
                    existing = existing,
                    replaceExisting = true,
                )
            } else {
                Result.success(
                    SearchEngineImportResult(
                        importedCount = preview.importedCount,
                        skippedCount = preview.skippedCount,
                        sourceLabel = preview.sourceLabel,
                        mergedEngines = preview.mergedEngines,
                    ),
                )
            }
            result.fold(
                onSuccess = { importResult ->
                    persistEngines(importResult.mergedEngines) {
                        _importPreviewState.value = null
                        userMessageBus.showSuccess(
                            appContext.resources.getQuantityString(
                                R.plurals.search_engine_import_success,
                                importResult.importedCount,
                                importResult.importedCount,
                                importResult.sourceLabel,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    userMessageBus.showError(
                        appContext.getString(
                            R.string.search_engine_import_failed,
                            error.message.orEmpty(),
                        ),
                    )
                },
            )
        }
    }

    fun upsertEngine(result: SearchEngineEditorResult) {
        if (!SearchEngineValidator.validate(result.engine)) {
            userMessageBus.showError(appContext.getString(R.string.search_engine_validation_failed))
            return
        }
        viewModelScope.launch {
            val existing = settings.value.searchEngines
            val previous = existing.find { it.id == result.engine.id }
            var engine = result.engine
            when {
                result.savedIconPath != null -> {
                    previous?.iconPath?.let { oldPath ->
                        if (oldPath != result.savedIconPath) {
                            SearchEngineIconStorage.deleteIconIfOwned(appContext, oldPath)
                        }
                    }
                    engine = engine.copy(
                        iconType = SearchIconType.URI,
                        iconPath = result.savedIconPath,
                        textIcon = null,
                    )
                }
                result.iconUri != null -> {
                    val iconPath = SearchEngineIconStorage.saveIconFromUri(appContext, result.iconUri)
                    if (iconPath != null) {
                        previous?.iconPath?.let { oldPath ->
                            if (oldPath != iconPath) {
                                SearchEngineIconStorage.deleteIconIfOwned(appContext, oldPath)
                            }
                        }
                        engine = engine.copy(
                            iconType = SearchIconType.URI,
                            iconPath = iconPath,
                            textIcon = null,
                        )
                    }
                }
                result.engine.iconType == SearchIconType.TEXT -> {
                    previous?.iconPath?.let { oldPath ->
                        SearchEngineIconStorage.deleteIconIfOwned(appContext, oldPath)
                    }
                    engine = engine.copy(
                        iconType = SearchIconType.TEXT,
                        iconPath = null,
                        textIcon = result.engine.textIcon,
                    )
                }
            }
            val engines = existing.toMutableList()
            val index = engines.indexOfFirst { it.id == engine.id }
            if (index >= 0) {
                engines[index] = engine.copy(sortOrder = engines[index].sortOrder)
            } else {
                val order = (engines.maxOfOrNull { it.sortOrder } ?: -1) + 1
                engines += engine.copy(sortOrder = order)
            }
            persistEngines(engines.sortedBy { it.sortOrder }) {
                userMessageBus.showSuccess(appContext.getString(R.string.search_engine_saved))
            }
        }
    }

    fun deleteEngine(id: String) {
        viewModelScope.launch {
            val engines = settings.value.searchEngines
            val removed = engines.find { it.id == id } ?: return@launch
            val remaining = engines
                .filter { it.id != id }
                .sortedBy { it.sortOrder }
                .mapIndexed { index, engine -> engine.copy(sortOrder = index) }
            persistEngines(remaining) {
                SearchEngineIconStorage.deleteIconIfOwned(appContext, removed.iconPath)
                userMessageBus.showSuccess(appContext.getString(R.string.search_engine_deleted))
            }
        }
    }

    fun moveEngine(id: String, direction: Int) {
        moveEngineInCategory(id, direction) { true }
    }

    fun moveImageShareEngine(id: String, direction: Int) {
        moveEngineInCategory(id, direction) { it.engineType == SearchEngineType.SHARE_IMAGE_TO_APP }
    }

    private fun moveEngineInCategory(
        id: String,
        direction: Int,
        categoryFilter: (SearchEngineConfig) -> Boolean,
    ) {
        viewModelScope.launch {
            val sorted = settings.value.searchEngines.sortedBy { it.sortOrder }.toMutableList()
            val categoryIndices = sorted.mapIndexedNotNull { index, engine ->
                index.takeIf { categoryFilter(engine) }
            }
            val categoryPosition = categoryIndices.indexOfFirst { sorted[it].id == id }
            if (categoryPosition < 0) return@launch
            val targetPosition = categoryPosition + direction
            if (targetPosition !in categoryIndices.indices) return@launch
            val indexA = categoryIndices[categoryPosition]
            val indexB = categoryIndices[targetPosition]
            val itemA = sorted[indexA]
            sorted[indexA] = sorted[indexB]
            sorted[indexB] = itemA
            persistEngines(sorted.mapIndexed { i, engine -> engine.copy(sortOrder = i) })
        }
    }

    fun setGridColumns(value: Int) = launchSettingsWrite {
        settingsRepository.setSearchEngineGridColumns(value)
    }

    fun setGridRows(value: Int) = launchSettingsWrite {
        settingsRepository.setSearchEngineGridRows(value)
    }

    fun setShowLabels(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchEngineShowLabels(enabled)
    }

    fun reorderPickPanelEngines(ordered: List<SearchEngineConfig>) {
        viewModelScope.launch {
            val sorted = settings.value.searchEngines.sortedBy { it.sortOrder }
            val orderedIds = ordered.map { it.id }.toSet()
            val remaining = sorted.filter { it.id !in orderedIds }
            val merged = ordered + remaining
            persistEngines(merged.mapIndexed { index, engine -> engine.copy(sortOrder = index) })
        }
    }

    fun reorderImageShareEngines(ordered: List<SearchEngineConfig>) {
        viewModelScope.launch {
            val sorted = settings.value.searchEngines.sortedBy { it.sortOrder }
            val orderedIds = ordered.map { it.id }.toSet()
            val remaining = sorted.filter { it.id !in orderedIds }
            val merged = ordered + remaining
            persistEngines(merged.mapIndexed { index, engine -> engine.copy(sortOrder = index) })
        }
    }

    fun reorderAggregatedImageSearchEngines(ordered: List<AggregatedImageSearchEngineConfig>) {
        viewModelScope.launch {
            persistAggregatedEngines(ordered.mapIndexed { index, config -> config.copy(sortOrder = index) })
        }
    }

    fun moveAggregatedImageSearchEngine(engineId: String, direction: Int) {
        viewModelScope.launch {
            val sorted = settings.value.aggregatedImageSearchEngines
                .sortedBy { it.sortOrder }
                .toMutableList()
            val index = sorted.indexOfFirst { it.engineId == engineId }
            if (index < 0) return@launch
            val target = index + direction
            if (target !in sorted.indices) return@launch
            val item = sorted.removeAt(index)
            sorted.add(target, item)
            persistAggregatedEngines(sorted.mapIndexed { i, config -> config.copy(sortOrder = i) })
        }
    }

    fun setAggregatedImageSearchEngineShowInPanel(engineId: String, show: Boolean) {
        viewModelScope.launch {
            val updated = settings.value.aggregatedImageSearchEngines.map { config ->
                if (config.engineId != engineId) {
                    config
                } else {
                    config.copy(
                        showInPanel = show,
                        preloadOnOpen = if (!show) false else config.preloadOnOpen,
                    )
                }
            }
            persistAggregatedEngines(updated)
        }
    }

    fun setAggregatedImageSearchEnginePreload(engineId: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = settings.value.aggregatedImageSearchEngines.map { config ->
                if (config.engineId == engineId) config.copy(preloadOnOpen = enabled) else config
            }
            persistAggregatedEngines(updated)
        }
    }

    private suspend fun persistAggregatedEngines(configs: List<AggregatedImageSearchEngineConfig>) {
        val merged = AggregatedImageSearchEnginePreferencesStore.mergeWithCatalog(configs)
        settingsRepository.setAggregatedImageSearchEngines(merged)
            .onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
    }

    private suspend fun persistEngines(
        engines: List<SearchEngineConfig>,
        onSuccess: () -> Unit = {},
    ) {
        settingsRepository.setSearchEngines(engines)
            .onSuccess { onSuccess() }
            .onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
    }

    private fun SearchEngineImportResult.toPreviewState(uri: Uri) = SearchEngineImportPreviewState(
        uri = uri,
        sourceLabel = sourceLabel,
        importedCount = importedCount,
        skippedCount = skippedCount,
        mergedEngines = mergedEngines,
    )
}
