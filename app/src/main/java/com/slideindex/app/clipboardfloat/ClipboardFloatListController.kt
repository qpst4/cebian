package com.slideindex.app.clipboardfloat

import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.settings.ClipboardFloatWindowMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipboardFloatListController(
    private val repository: ClipboardHistoryRepository?,
    private val scope: CoroutineScope,
) {
    private val _entries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = _entries.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loadJob: Job? = null

    init {
        scope.launch {
            repository?.revision?.collect {
                if (it == 0L) return@collect
                reload()
            }
        }
        reload()
    }

    fun reload() {
        if (repository == null) return
        loadJob?.cancel()
        loadJob = scope.launch {
            val showLoading = _entries.value.isEmpty()
            if (showLoading) _loading.value = true
            try {
                val page = withContext(Dispatchers.IO) {
                    repository.loadHistoryPage(
                        createdBeforeMs = null,
                        limit = ClipboardFloatWindowMetrics.PAGE_SIZE,
                    )
                }
                _entries.value = page.entries
            } finally {
                if (showLoading) _loading.value = false
            }
        }
    }
}
