package com.slideindex.app.di

import com.slideindex.app.nativeengine.NativeEnginePackMigrationRunner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class OcrEnginePackMigrationStartup @Inject constructor(
    private val noticeHolder: OcrEnginePackMigrationNoticeHolder,
    private val migrationRunner: NativeEnginePackMigrationRunner,
    private val applicationScope: CoroutineScope,
) {
    fun start() {
        applicationScope.launch(Dispatchers.IO) {
            noticeHolder.ensureChecked(migrationRunner)
        }
    }
}
