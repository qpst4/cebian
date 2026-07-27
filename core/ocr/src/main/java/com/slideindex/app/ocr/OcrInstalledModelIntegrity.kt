package com.slideindex.app.ocr

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class OcrInstalledModelIntegrity @Inject constructor(
    private val repository: OcrModelRepository,
    private val catalogProvider: OcrModelCatalogProvider,
    private val mlKitChineseModuleInstaller: MlKitChineseModuleInstaller,
) {
    fun commitInstall(manifest: OcrModelInstallManifest): Boolean {
        if (!repository.verifyPendingInstall(manifest.modelId)) {
            Log.w(TAG, "pending install integrity failed: ${manifest.modelId}")
            repository.deleteModel(manifest.modelId)
            return false
        }
        repository.writeManifest(manifest)
        return true
    }

    suspend fun repairInstalledModels() = withContext(Dispatchers.IO) {
        catalogProvider.allModels().forEach { entry ->
            if (entry.builtin) return@forEach
            if (repository.readManifest(entry.id) == null) return@forEach
            if (repository.verifyInstalledIntegrity(entry.id)) return@forEach
            Log.w(TAG, "removing corrupt installed model: ${entry.id}")
            removeInstall(entry.id)
        }
    }

    suspend fun removeInstall(modelId: String) {
        val entry = catalogProvider.findModel(modelId)
        if (entry?.engine == OcrEngines.MLKIT_CHINESE) {
            mlKitChineseModuleInstaller.release()
        }
        repository.deleteModel(modelId)
    }

    private companion object {
        private const val TAG = "OcrModelIntegrity"
    }
}
