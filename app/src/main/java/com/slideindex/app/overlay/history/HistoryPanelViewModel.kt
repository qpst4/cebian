package com.slideindex.app.overlay.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.stash.StashEntry
import com.slideindex.app.stash.StashRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class HistoryPanelTab {
    Stash,
    Clipboard,
}

class HistoryPanelViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val stashRepository: StashRepository?,
    private val clipboardRepository: ClipboardHistoryRepository?,
) : ViewModel() {

    val clipboardSearchQuery: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_CLIPBOARD_SEARCH, "")

    val selectedTab: StateFlow<HistoryPanelTab> =
        savedStateHandle.getStateFlow(KEY_SELECTED_TAB, HistoryPanelTab.Stash)

    val stashEntries: StateFlow<List<StashEntry>> = stashRepository?.entries
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())

    val clipboardEntries: StateFlow<List<ClipboardEntry>> = clipboardRepository?.entries
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        ?: MutableStateFlow(emptyList())

    val filteredClipboardEntries: StateFlow<List<ClipboardEntry>> = combine(
        clipboardEntries,
        clipboardSearchQuery,
    ) { entries, query ->
        val trimmed = query.trim()
        if (trimmed.isEmpty()) entries else entries.filter { it.matchesQuery(trimmed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expandedEntryIds: StateFlow<Set<String>> =
        savedStateHandle.getStateFlow(KEY_EXPANDED_IDS, emptySet())

    val selectedImageIndices: StateFlow<Map<String, Int>> =
        savedStateHandle.getStateFlow(KEY_IMAGE_INDICES, emptyMap())

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
        clipboardRepository?.refreshClipboardWithFocus(
            context,
            force = true,
            promoteExistingOnMatch = false,
        )
    }

    companion object {
        private const val KEY_CLIPBOARD_SEARCH = "clipboard_search"
        private const val KEY_EXPANDED_IDS = "expanded_entry_ids"
        private const val KEY_IMAGE_INDICES = "selected_image_indices"
        private const val KEY_SELECTED_TAB = "selected_tab"
    }
}
