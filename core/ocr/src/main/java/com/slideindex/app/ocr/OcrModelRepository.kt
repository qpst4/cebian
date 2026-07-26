package com.slideindex.app.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OcrModelInstallManifest(
    val modelId: String,
    val catalogVersion: Int,
    val installedAtEpochMs: Long,
    val sizeBytes: Long,
)

@Singleton
class OcrModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogProvider: OcrModelCatalogProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val modelsRoot: File
        get() = File(context.filesDir, "ocr-models").apply { mkdirs() }

    fun modelRoot(modelId: String): File = File(modelsRoot, modelId)

    fun isInstalled(modelId: String): Boolean {
        val entry = catalogProvider.findModel(modelId) ?: return false
        if (entry.builtin) return true
        val root = modelRoot(modelId)
        if (!manifestFile(modelId).exists()) return false
        if (entry.files.isEmpty()) return true
        return entry.files.all { spec ->
            OcrModelChecksum.matches(File(root, spec.relativePath), spec.sha256)
        }
    }

    fun installedModelIds(): Set<String> =
        catalogProvider.allModels()
            .filter { isInstalled(it.id) }
            .map { it.id }
            .toSet()

    fun detModelFile(modelId: String): File = File(modelRoot(modelId), "det/inference.onnx")

    fun recModelFile(modelId: String): File = File(modelRoot(modelId), "rec/inference.onnx")

    fun recConfigFile(modelId: String): File = File(modelRoot(modelId), "rec/inference.yml")

    fun targetFile(modelId: String, relativePath: String): File =
        File(modelRoot(modelId), relativePath)

    fun partialFile(modelId: String, relativePath: String): File =
        File(modelRoot(modelId), "$relativePath.part")

    fun manifestFile(modelId: String): File = File(modelRoot(modelId), "manifest.json")

    fun readManifest(modelId: String): OcrModelInstallManifest? {
        val file = manifestFile(modelId)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<OcrModelInstallManifest>(file.readText())
        }.getOrNull()
    }

    fun writeManifest(manifest: OcrModelInstallManifest) {
        val file = manifestFile(manifest.modelId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(OcrModelInstallManifest.serializer(), manifest))
    }

    fun deleteModel(modelId: String) {
        modelRoot(modelId).deleteRecursively()
    }

    fun ensureModelDirectory(modelId: String) {
        modelRoot(modelId).mkdirs()
    }
}
