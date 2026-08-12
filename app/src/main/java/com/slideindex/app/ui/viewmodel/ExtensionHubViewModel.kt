package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.stash.StashRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ExtensionHubViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    stashRepository: StashRepository,
    clipboardHistoryRepository: ClipboardHistoryRepository,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    val stashEntryCount: StateFlow<Int> = stashRepository.entries
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = stashRepository.entries.value.size,
        )

    val clipboardEntryCount: StateFlow<Int> = clipboardHistoryRepository.entryCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = clipboardHistoryRepository.entryCount.value,
        )
}
