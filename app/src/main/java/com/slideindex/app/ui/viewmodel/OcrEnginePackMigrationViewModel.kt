package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.di.OcrEnginePackMigrationNoticeHolder
import com.slideindex.app.nativeengine.NativeEnginePackMigrationRunner
import com.slideindex.app.nativeengine.OcrEnginePackMigrationNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OcrEnginePackMigrationViewModel @Inject constructor(
    private val migrationRunner: NativeEnginePackMigrationRunner,
    private val noticeHolder: OcrEnginePackMigrationNoticeHolder,
) : ViewModel() {
    private val _notice = MutableStateFlow<OcrEnginePackMigrationNotice?>(null)
    val notice: StateFlow<OcrEnginePackMigrationNotice?> = _notice.asStateFlow()

    fun checkOnLaunch() {
        viewModelScope.launch {
            noticeHolder.ensureChecked(migrationRunner)
            noticeHolder.notice.collect { pending ->
                _notice.value = pending
            }
        }
    }

    fun acknowledgeNotice() {
        _notice.value?.let { migrationRunner.acknowledgeNotice(it) }
        noticeHolder.clearNotice()
        _notice.value = null
    }

    fun deferNotice() {
        noticeHolder.clearNotice()
        _notice.value = null
    }
}
