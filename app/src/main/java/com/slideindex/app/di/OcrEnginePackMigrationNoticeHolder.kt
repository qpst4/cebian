package com.slideindex.app.di

import com.slideindex.app.nativeengine.NativeEnginePackMigrationRunner
import com.slideindex.app.nativeengine.OcrEnginePackMigrationNotice
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class OcrEnginePackMigrationNoticeHolder @Inject constructor() {
    private val _notice = MutableStateFlow<OcrEnginePackMigrationNotice?>(null)
    val notice: StateFlow<OcrEnginePackMigrationNotice?> = _notice.asStateFlow()

    private val started = AtomicBoolean(false)

    suspend fun ensureChecked(runner: NativeEnginePackMigrationRunner): OcrEnginePackMigrationNotice? {
        if (!started.compareAndSet(false, true)) {
            return _notice.value
        }
        val result = runner.runOcrStartupMigration()
        _notice.value = result
        return result
    }

    fun clearNotice() {
        _notice.value = null
    }
}
