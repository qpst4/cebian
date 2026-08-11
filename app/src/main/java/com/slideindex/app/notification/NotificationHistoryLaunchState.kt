package com.slideindex.app.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deep link / shortcut 打开通知滤盒时携带的待应用搜索词。
 */
object NotificationHistoryLaunchState {
    private val _pendingSearchQuery = MutableStateFlow<String?>(null)
    val pendingSearchQuery: StateFlow<String?> = _pendingSearchQuery.asStateFlow()

    fun setPendingSearchQuery(query: String?) {
        _pendingSearchQuery.value = query?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun consumePendingSearchQuery(): String? {
        val query = _pendingSearchQuery.value
        _pendingSearchQuery.value = null
        return query
    }
}
