package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.service.ShareImageOcrHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ShareImageOcrHistoryViewModel @Inject constructor(
    val historyRepository: ShareImageOcrHistoryRepository,
) : ViewModel() {
    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clear()
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            historyRepository.delete(id)
        }
    }
}
