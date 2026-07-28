package com.slideindex.app.overlay.history

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashRepository

/**
 * Overlay panels use [OverlayComposeOwner] as [SavedStateRegistryOwner]; the default
 * [androidx.lifecycle.createSavedStateHandle] CreationExtras path is unavailable there.
 */
class HistoryPanelViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val stashRepository: StashRepository? = StashAccess.repository,
    private val clipboardRepository: ClipboardHistoryRepository? = ClipboardAccess.repository,
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        return HistoryPanelViewModel(
            savedStateHandle = handle,
            stashRepository = stashRepository,
            clipboardRepository = clipboardRepository,
        ) as T
    }
}
