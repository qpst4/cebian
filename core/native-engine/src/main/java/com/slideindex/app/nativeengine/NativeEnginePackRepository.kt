package com.slideindex.app.nativeengine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NativeEnginePackInstallManifest(
    val packId: String,
    val catalogVersion: Int,
    val packRevision: Int = 1,
    val displayVersion: String? = null,
    val installedAtEpochMs: Long,
    val sizeBytes: Long,
)

@Singleton
class NativeEnginePackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun packsRoot(): File = File(context.filesDir, "native-engine-packs")

    fun packRoot(packId: String): File = File(packsRoot(), packId)

    fun manifestFile(packId: String): File = File(packRoot(packId), "manifest.json")

    fun nativeLibDir(packId: String): File = File(packRoot(packId), "lib/$ABI")

    fun assetsRoot(packId: String): File = File(packRoot(packId), "assets")

    fun assetFile(packId: String, relativePath: String): File = File(assetsRoot(packId), relativePath)

    fun downloadZipFile(packId: String): File = File(packsRoot(), "$packId.download.zip")

    fun isInstalled(packId: String): Boolean {
        val manifest = readManifest(packId) ?: return false
        val libDir = nativeLibDir(packId)
        if (!libDir.isDirectory) return false
        return libDir.listFiles()?.any { it.isFile && it.name.endsWith(".so") } == true &&
            manifest.packId == packId
    }

    fun installedCatalogVersion(packId: String): Int? = readManifest(packId)?.catalogVersion

    fun needsCatalogUpgrade(packId: String, catalogVersion: Int): Boolean {
        if (!isInstalled(packId)) return false
        val installedVersion = installedCatalogVersion(packId) ?: return true
        return installedVersion < catalogVersion
    }

    fun needsPackRevisionUpgrade(packId: String, requiredRevision: Int): Boolean {
        if (!isInstalled(packId)) return false
        val installedRevision = readManifest(packId)?.packRevision ?: return true
        return installedRevision < requiredRevision
    }

    fun readManifest(packId: String): NativeEnginePackInstallManifest? {
        val file = manifestFile(packId)
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString<NativeEnginePackInstallManifest>(file.readText())
        }.getOrNull()
    }

    fun writeManifest(manifest: NativeEnginePackInstallManifest) {
        val file = manifestFile(manifest.packId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(NativeEnginePackInstallManifest.serializer(), manifest))
    }

    fun deletePack(packId: String) {
        packRoot(packId).deleteRecursively()
        downloadZipFile(packId).delete()
    }

    fun ensurePackDirectory(packId: String): File = packRoot(packId).also { it.mkdirs() }

    private companion object {
        private const val ABI = "arm64-v8a"
    }
}
