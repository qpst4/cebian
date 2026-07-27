package com.slideindex.app.di

import com.slideindex.app.ocr.OcrInstalledModelIntegrity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class OcrInstalledModelStartupVerifier @Inject constructor(
    private val installIntegrity: OcrInstalledModelIntegrity,
    private val applicationScope: CoroutineScope,
) {
    fun start() {
        applicationScope.launch {
            installIntegrity.repairInstalledModels()
        }
    }
}
