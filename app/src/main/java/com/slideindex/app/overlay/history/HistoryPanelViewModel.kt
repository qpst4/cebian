package com.slideindex.app.overlay.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.stash.matchesQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview

enum class HistoryPanelTab {
    Stash,
    Clipboard,
}

@OptIn(FlowPreview::class)
class HistoryPanelViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val stashRepository: StashRepository?,
    private val clipboardRepository: ClipboardHistoryRepository?,
) : ViewModel() {

    val stashSearchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_STASH_SEARCH, "")

    val clipboardSearchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_CLIPBOARD_SEARCH, "")

    val selectedTab: StateFlow<HistoryPanelTab> =
        savedStateHandle.getStateFlow(KEY_SELECTED_TAB, HistoryPanelTab.Stash)

    val stashEntries: StateFlow<List<StashEntry>> = stashRepository?.entries
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())

    private val _debouncedStashSearchQuery = MutableStateFlow("")

    val filteredStashEntries: StateFlow<List<StashEntry>> = combine(
        stashEntries,
        _debouncedStashSearchQuery,
    ) { entries, query ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) entries else entries.filter { it.matchesQuery(trimmed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val clipboardEntryCount: StateFlow<Int> = clipboardRepository?.entryCount
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
        ?: MutableStateFlow(0)

    private val _clipboardPagedEntries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    private val _clipboardSearchResults = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    private val _clipboardListLoading = MutableStateFlow(false)
    private var clipboardReachedEnd = false
    private var clipboardLoadJob: Job? = null
    private var clipboardActivateJob: Job? = null
    private var clipboardPagesInitialized = false

    val clipboardListLoading: StateFlow<Boolean> = _clipboardListLoading.asStateFlow()

    val filteredClipboardEntries: StateFlow<List<ClipboardEntry>> = combine(
        _clipboardPagedEntries,
        _clipboardSearchResults,
        clipboardSearchQuery,
    ) { paged, searched, query ->
        if (query.trim().isEmpty()) paged else searched
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expandedEntryIds: StateFlow<Set<String>> =
        savedStateHandle.getStateFlow(KEY_EXPANDED_IDS, emptySet())

    val selectedImageIndices: StateFlow<Map<String, Int>> =
        savedStateHandle.getStateFlow(KEY_IMAGE_INDICES, emptyMap())

    init {
        viewModelScope.launch {
            stashSearchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { _debouncedStashSearchQuery.value = it }
        }
        viewModelScope.launch {
            clipboardSearchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { query ->
                    runSearch(query)
                }
        }
        viewModelScope.launch {
            clipboardRepository?.revision?.collect { revision ->
                if (revision == 0L) return@collect
                if (clipboardSearchQuery.value.isNotBlank()) {
                    runSearch(clipboardSearchQuery.value)
                    return@collect
                }
                syncPagedListAfterRevision()
            }
        }
    }

    fun setStashSearchQuery(query: String) {
        savedStateHandle[KEY_STASH_SEARCH] = query
    }

    fun setClipboardSearchQuery(query: String) {
        savedStateHandle[KEY_CLIPBOARD_SEARCH] = query
    }

    fun setSelectedTab(tab: HistoryPanelTab) {
        savedStateHandle[KEY_SELECTED_TAB] = tab
    }

    fun toggleExpanded(entryId: String) {
        val current = expandedEntryIds.value
        savedStateHandle[KEY_EXPANDED_IDS] = if (entryId in current) {
            current - entryId
        } else {
            current + entryId
        }
    }

    fun setSelectedImageIndex(entryId: String, index: Int) {
        savedStateHandle[KEY_IMAGE_INDICES] = selectedImageIndices.value + (entryId to index)
    }

    fun onClipboardTabActivated(context: android.content.Context) {
        clipboardActivateJob?.cancel()
        clipboardActivateJob = viewModelScope.launch {
            delay(CLIPBOARD_TAB_ACTIVATE_DELAY_MS)
            clipboardRepository?.refreshClipboardWithFocus(
                context,
                force = true,
                promoteExistingOnMatch = false,
            )
            if (!clipboardPagesInitialized || _clipboardPagedEntries.value.isEmpty()) {
                refreshClipboardPages(showInitialLoading = _clipboardPagedEntries.value.isEmpty())
            }
        }
    }

    fun ensureClipboardPagesLoaded() {
        if (_clipboardListLoading.value) return
        if (!clipboardPagesInitialized || _clipboardPagedEntries.value.isEmpty()) {
            refreshClipboardPages(showInitialLoading = _clipboardPagedEntries.value.isEmpty())
        }
    }

    fun refreshClipboardPages(showInitialLoading: Boolean = false) {
        if (_clipboardListLoading.value) return
        clipboardLoadJob?.cancel()
        clipboardLoadJob = viewModelScope.launch {
            if (showInitialLoading) {
                _clipboardListLoading.value = true
            }
            try {
                val loaded = loadClipboardPageBatch(more = false)
                _clipboardPagedEntries.value = loaded
                clipboardPagesInitialized = true
            } finally {
                _clipboardListLoading.value = false
            }
        }
    }

    fun loadMoreClipboard() {
        if (_clipboardListLoading.value || clipboardReachedEnd) return
        if (clipboardSearchQuery.value.isNotBlank()) return
        clipboardLoadJob?.cancel()
        clipboardLoadJob = viewModelScope.launch {
            _clipboardListLoading.value = true
            try {
                loadClipboardPageBatch(more = true)
            } finally {
                _clipboardListLoading.value = false
            }
        }
    }

    private suspend fun runSearch(query: String) {
        val repo = clipboardRepository ?: return
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _clipboardSearchResults.value = emptyList()
            return
        }
        _clipboardListLoading.value = true
        try {
            _clipboardSearchResults.value = withContext(Dispatchers.IO) {
                repo.searchHistory(trimmed)
            }
        } finally {
            _clipboardListLoading.value = false
        }
    }

    private suspend fun syncPagedListAfterRevision() {
        val repo = clipboardRepository ?: return
        val current = _clipboardPagedEntries.value
        val freshTop = withContext(Dispatchers.IO) {
            repo.loadHistoryPage(
                createdBeforeMs = null,
                limit = HistoryFloatPagination.PAGE_SIZE,
            )
        }.entries
        if (freshTop.isEmpty()) {
            _clipboardPagedEntries.value = emptyList()
            clipboardPagesInitialized = true
            clipboardReachedEnd = true
            return
        }
        val lastCreated = freshTop.lastOrNull()?.createdAtEpochMs
        val preservedTail = if (lastCreated != null && current.size > freshTop.size) {
            current.filter { it.createdAtEpochMs < lastCreated }
        } else {
            emptyList()
        }
        _clipboardPagedEntries.value = freshTop + preservedTail
        clipboardPagesInitialized = true
        clipboardReachedEnd = freshTop.size < HistoryFloatPagination.PAGE_SIZE && preservedTail.isEmpty()
    }

    private suspend fun loadClipboardPageBatch(more: Boolean): List<ClipboardEntry> {
        val repo = clipboardRepository ?: return emptyList()
        if (!more) {
            clipboardReachedEnd = false
        } else if (clipboardReachedEnd) {
            return _clipboardPagedEntries.value
        }
        val current = if (more) _clipboardPagedEntries.value.toMutableList() else mutableListOf()
        var batchCount = 0
        var cursor: Long? = if (more) current.lastOrNull()?.createdAtEpochMs else null
        while (!clipboardReachedEnd) {
            val page = withContext(Dispatchers.IO) {
                repo.loadHistoryPage(
                    createdBeforeMs = cursor,
                    limit = HistoryFloatPagination.PAGE_SIZE,
                )
            }
            if (page.entries.isEmpty()) {
                clipboardReachedEnd = true
                break
            }
            val existingIds = current.mapTo(mutableSetOf()) { it.id }
            val itemsToAdd = if (current.isEmpty()) {
                page.entries.distinctBy { it.id }
            } else {
                page.entries.filter { existingIds.add(it.id) }
            }
            if (itemsToAdd.isNotEmpty()) {
                current.addAll(itemsToAdd)
                batchCount += itemsToAdd.size
            }
            clipboardReachedEnd = !page.hasMore
            if (batchCount >= HistoryFloatPagination.PAGE_SIZE) break
            if (itemsToAdd.isEmpty()) {
                clipboardReachedEnd = true
                break
            }
            cursor = current.lastOrNull()?.createdAtEpochMs
        }
        if (more) {
            _clipboardPagedEntries.value = current
        }
        clipboardPagesInitialized = current.isNotEmpty() || clipboardReachedEnd
        return current
    }

    companion object {
        private const val KEY_STASH_SEARCH = "stash_search"
        private const val KEY_CLIPBOARD_SEARCH = "clipboard_search"
        private const val KEY_EXPANDED_IDS = "expanded_entry_ids"
        private const val KEY_IMAGE_INDICES = "selected_image_indices"
        private const val KEY_SELECTED_TAB = "selected_tab"
        /** 侧栏入场动画 + chrome z-order 抬升后再刷新剪贴板，避免与 WM/DB 并发。 */
        private const val CLIPBOARD_TAB_ACTIVATE_DELAY_MS = 450L
    }
}
