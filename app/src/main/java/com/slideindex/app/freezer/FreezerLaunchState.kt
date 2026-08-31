package com.slideindex.app.freezer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 手势 / 快捷方式打开冰箱时携带的待应用 Tab。 */
object FreezerLaunchState {
    private val _pendingInitialTab = MutableStateFlow<FreezerTab?>(null)
    val pendingInitialTab: StateFlow<FreezerTab?> = _pendingInitialTab.asStateFlow()

    fun setPendingInitialTab(tab: FreezerTab?) {
        _pendingInitialTab.value = tab
    }

    fun consumePendingInitialTab(): FreezerTab? {
        val tab = _pendingInitialTab.value
        _pendingInitialTab.value = null
        return tab
    }
}
