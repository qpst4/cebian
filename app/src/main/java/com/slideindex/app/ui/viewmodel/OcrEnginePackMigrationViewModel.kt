package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.di.OcrEnginePackMigrationNoticeHolder
import com.slideindex.app.nativeengine.NativeEnginePackIds
import com.slideindex.app.nativeengine.NativeEnginePackMigrationRunner
import com.slideindex.app.nativeengine.OcrEnginePackMigrationNotice
import com.slideindex.app.service.NativeEnginePackDownloadService
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OcrEnginePackMigrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val migrationRunner: NativeEnginePackMigrationRunner,
    private val noticeHolder: OcrEnginePackMigrationNoticeHolder,
    private val settingsRepository: SettingsRepository,
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

    fun dismissNotice() {
        _notice.value?.let { migrationRunner.markNoticeDismissed(it) }
        noticeHolder.clearNotice()
        _notice.value = null
    }

    fun downloadOcrEnginePack() {
        viewModelScope.launch {
            val wifiOnly = settingsRepository.readSnapshot().ocrDownloadWifiOnly
            NativeEnginePackDownloadService.start(
                context = context,
                packId = NativeEnginePackIds.OCR,
                wifiOnly = wifiOnly,
            )
            dismissNotice()
        }
    }
}
