package com.slideindex.app.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
    /** Positive cache only; invalidated on install/delete. Download already verified SHA-256. */
    private val installedCache = ConcurrentHashMap<String, Long>()

    private val modelsRoot: File
        get() = File(context.filesDir, "ocr-models").apply { mkdirs() }

    fun modelRoot(modelId: String): File = File(modelsRoot, modelId)

    fun isInstalled(modelId: String): Boolean {
        val manifest = manifestFile(modelId)
        if (!manifest.exists()) {
            installedCache.remove(modelId)
            return computeIsInstalled(modelId)
        }
        val manifestMtime = manifest.lastModified()
        if (installedCache[modelId] == manifestMtime) {
            return true
        }
        val installed = computeIsInstalled(modelId)
        if (installed) {
            installedCache[modelId] = manifestMtime
        } else {
            installedCache.remove(modelId)
        }
        return installed
    }

    /** Full SHA-256 verification after [writeManifest]; not for pick/OCR hot paths. */
    fun verifyInstalledIntegrity(modelId: String): Boolean {
        val entry = catalogProvider.findModel(modelId) ?: return false
        if (entry.builtin) return true
        if (!manifestFile(modelId).exists()) return false
        if (entry.files.isEmpty()) return true
        return verifyModelFileChecksums(modelId, entry)
    }

    /** Full SHA-256 verification before first manifest write (install finalize). */
    fun verifyPendingInstall(modelId: String): Boolean {
        val entry = catalogProvider.findModel(modelId) ?: return false
        if (entry.builtin) return true
        if (entry.files.isEmpty()) return true
        return verifyModelFileChecksums(modelId, entry)
    }

    private fun verifyModelFileChecksums(modelId: String, entry: OcrModelEntry): Boolean {
        val root = modelRoot(modelId)
        return entry.files.all { spec ->
            OcrModelChecksum.matches(File(root, spec.relativePath), spec.sha256)
        }
    }

    private fun computeIsInstalled(modelId: String): Boolean {
        val entry = catalogProvider.findModel(modelId) ?: return false
        if (entry.builtin) return true
        if (!manifestFile(modelId).exists()) return false
        if (entry.files.isEmpty()) return true
        val root = modelRoot(modelId)
        return entry.files.all { spec -> hasInstalledFile(root, spec) }
    }

    private fun hasInstalledFile(root: File, spec: OcrModelFileSpec): Boolean {
        val file = File(root, spec.relativePath)
        if (!file.isFile || file.length() <= 0L) return false
        val expectedSize = spec.sizeBytes ?: return true
        return expectedSize <= 0L || file.length() == expectedSize
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
        installedCache.remove(manifest.modelId)
    }

    fun deleteModel(modelId: String) {
        modelRoot(modelId).deleteRecursively()
        installedCache.remove(modelId)
    }

    fun ensureModelDirectory(modelId: String) {
        modelRoot(modelId).mkdirs()
    }
}
