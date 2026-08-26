package com.slideindex.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.slideindex.app.R
import com.slideindex.app.search.SearchEngineIconStorage
import com.slideindex.app.search.SearchEngineImportResult
import com.slideindex.app.search.SearchEngineImporter
import com.slideindex.app.search.SearchEngineValidator
import com.slideindex.app.search.SearchHistoryRepository
import com.slideindex.app.settings.AggregatedImageSearchEngineConfig
import com.slideindex.app.settings.AggregatedImageSearchEnginePreferencesStore
import com.slideindex.app.settings.SearchPanelHistoryCapacity
import com.slideindex.app.settings.SearchPanelSectionAliasSettings
import com.slideindex.app.settings.SearchPanelInputBehavior
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

data class SearchEngineDraft(
    val engineId: String = "",
    val name: String = "",
    val aliasCode: String = "",
    val engineType: SearchEngineType = SearchEngineType.DIRECT_LINK,
    val searchLink: String = "",
    val externJumpLink: String = "",
    val externJumpPackage: String = "",
    val targetPackage: String = "",
    val targetActivity: String = "",
    val autoInputEnter: Boolean = true,
    val pendingIconUri: Uri? = null,
    val pendingIconPath: String? = null,
    val pendingTextIcon: String? = null,
)

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
    private val searchHistoryRepository: SearchHistoryRepository,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    private val _editorDraft = MutableStateFlow<SearchEngineDraft?>(null)
    val editorDraft: StateFlow<SearchEngineDraft?> = _editorDraft.asStateFlow()

    fun initDraft(initialEngine: SearchEngineConfig?, category: com.slideindex.app.ui.SearchEngineEditorCategory) {
        val currentDraft = _editorDraft.value
        if (currentDraft != null && currentDraft.engineId == initialEngine?.id.orEmpty()) {
            return
        }
        val isLegacyExternalLink = initialEngine?.engineType == SearchEngineType.EXTERN_JUMP_LINK
        val normalizedEngineType = when {
            category == com.slideindex.app.ui.SearchEngineEditorCategory.IMAGE_SHARE ->
                SearchEngineType.SHARE_IMAGE_TO_APP
            isLegacyExternalLink -> SearchEngineType.DIRECT_LINK
            else -> initialEngine?.engineType ?: SearchEngineType.DIRECT_LINK
        }
        val normalizedSearchLink = initialEngine?.searchLink.orEmpty().ifBlank {
            if (isLegacyExternalLink) initialEngine.externJumpLink.orEmpty() else ""
        }
        val normalizedTargetPackage = initialEngine?.targetPackage.orEmpty().ifBlank {
            if (isLegacyExternalLink) initialEngine.externJumpPackage.orEmpty() else ""
        }
        _editorDraft.value = SearchEngineDraft(
            engineId = initialEngine?.id.orEmpty(),
            name = initialEngine?.name.orEmpty(),
            aliasCode = initialEngine?.aliasCode.orEmpty(),
            engineType = normalizedEngineType,
            searchLink = normalizedSearchLink,
            externJumpLink = initialEngine?.externJumpLink.orEmpty(),
            externJumpPackage = initialEngine?.externJumpPackage.orEmpty(),
            targetPackage = normalizedTargetPackage,
            targetActivity = initialEngine?.targetActivity.orEmpty(),
            autoInputEnter = initialEngine?.autoInputEnter ?: true,
            pendingIconPath = initialEngine?.iconPath?.takeIf { initialEngine.iconType == SearchIconType.URI },
            pendingTextIcon = initialEngine?.textIcon?.takeIf { initialEngine.iconType == SearchIconType.TEXT },
        )
    }

    fun updateDraft(transform: (SearchEngineDraft) -> SearchEngineDraft) {
        _editorDraft.value = _editorDraft.value?.let(transform)
    }

    fun clearDraft() {
        _editorDraft.value = null
    }

    private val _importPreviewState = MutableStateFlow<SearchEngineImportPreviewState?>(null)
    val importPreviewState: StateFlow<SearchEngineImportPreviewState?> = _importPreviewState.asStateFlow()

    val searchHistoryEntryCount: StateFlow<Int> = searchHistoryRepository.entries
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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

    fun setDefaultEngineId(id: String?) = launchSettingsWrite {
        settingsRepository.setSearchPanelDefaultEngineId(id)
    }

    fun setSearchPanelInputBehavior(behavior: SearchPanelInputBehavior) = launchSettingsWrite {
        settingsRepository.setSearchPanelInputBehavior(behavior)
    }

    fun setSearchPanelContactSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelContactSearchEnabled(enabled)
    }

    fun setSearchPanelFileSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileSearchEnabled(enabled)
    }

    fun setSearchPanelAppSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelAppSearchEnabled(enabled)
    }

    fun setSearchPanelSettingsSearchEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelSettingsSearchEnabled(enabled)
    }

    fun setSearchPanelFileTypesEnabled(types: Set<String>) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileTypesEnabled(types)
    }

    fun setSearchPanelFileShowFolders(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileShowFolders(enabled)
    }

    fun setSearchPanelFileShowSystemFiles(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileShowSystemFiles(enabled)
    }

    fun setSearchPanelFilePreviewsEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelFilePreviewsEnabled(enabled)
    }

    fun setSearchPanelFileFolderWhitelist(patterns: Set<String>) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileFolderWhitelist(patterns)
    }

    fun setSearchPanelFileFolderBlacklist(patterns: Set<String>) = launchSettingsWrite {
        settingsRepository.setSearchPanelFileFolderBlacklist(patterns)
    }

    fun setSearchPanelPresentationMode(mode: com.slideindex.app.settings.SearchPanelPresentationMode) =
        launchSettingsWrite {
            settingsRepository.setSearchPanelPresentationMode(mode)
        }

    fun setSearchPanelBarPosition(position: com.slideindex.app.settings.SearchPanelBarPosition) =
        launchSettingsWrite {
            settingsRepository.setSearchPanelBarPosition(position)
        }

    fun setSearchPanelListOrder(order: com.slideindex.app.settings.SearchPanelListOrder) =
        launchSettingsWrite {
            settingsRepository.setSearchPanelListOrder(order)
        }

    fun setSearchPanelAppDisplayStyle(style: com.slideindex.app.settings.SearchPanelAppDisplayStyle) =
        launchSettingsWrite {
            settingsRepository.setSearchPanelAppDisplayStyle(style)
        }

    fun setSearchPanelCalculatorEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelCalculatorEnabled(enabled)
    }

    fun setSearchPanelWebSuggestionsEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setSearchPanelWebSuggestionsEnabled(enabled)
    }

    fun setSearchPanelWebSuggestionsCount(count: Int) = launchSettingsWrite {
        settingsRepository.setSearchPanelWebSuggestionsCount(count)
    }

    fun setSearchPanelHistoryMaxEntries(maxEntries: Int) = launchSettingsWrite {
        val coerced = SearchPanelHistoryCapacity.coerce(maxEntries)
        val result = settingsRepository.setSearchPanelHistoryMaxEntries(coerced)
        if (result.isSuccess) {
            searchHistoryRepository.trimToMax(coerced)
        }
        result
    }

    fun setSearchPanelSectionAliases(aliases: SearchPanelSectionAliasSettings) = launchSettingsWrite {
        settingsRepository.setSearchPanelSectionAliases(aliases)
    }

    fun clearSearchHistory() = launchRepositoryWrite {
        runCatching { searchHistoryRepository.clear() }
    }

    fun setSearchPanelBackgroundStyle(style: Int) = launchSettingsWrite {
        settingsRepository.setSearchPanelBackgroundStyle(style)
    }

    fun setSearchPanelBlurRadiusDp(value: Int) = launchSettingsWrite {
        settingsRepository.setSearchPanelBlurRadiusDp(value)
    }

    fun setSearchPanelDimPercent(value: Int) = launchSettingsWrite {
        settingsRepository.setSearchPanelDimPercent(value)
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

    fun setImageSearchPickPanelTransparency(value: Float) = launchSettingsWrite {
        settingsRepository.setFloatBallImageSearchPickPanelTransparency(value)
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
