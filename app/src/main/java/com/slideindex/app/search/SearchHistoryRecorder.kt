package com.slideindex.app.search

import android.content.Context
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.settings.SearchPanelHistoryCapacity

/** Fire-and-forget search history writes from overlay / pick-panel call sites. */
object SearchHistoryRecorder {
    fun record(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val repo = SearchHistoryAccess.repository ?: return
        val maxEntries = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.readSnapshot()
            ?.searchPanelHistoryMaxEntries
            ?: SearchPanelHistoryCapacity.DEFAULT
        repo.recordAsync(trimmed, maxEntries)
    }
}
