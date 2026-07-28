package com.slideindex.app.nativeengine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class NativeEnginePackCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogProvider: NativeEnginePackCatalogProvider,
    private val repository: NativeEnginePackRepository,
) {
    private val loadMutex = Mutex()

    fun isPackInstalled(packId: String): Boolean {
        if (!repository.isInstalled(packId)) return false
        val entry = catalogProvider.findPack(packId) ?: return false
        val libDir = repository.nativeLibDir(packId)
        val librariesReady = entry.libraries.all { name -> java.io.File(libDir, name).isFile }
        if (!librariesReady) return false
        return entry.assetPaths.all { path -> repository.assetFile(packId, path).isFile }
    }

    fun assetFile(packId: String, relativePath: String): File? {
        if (!repository.isInstalled(packId)) return null
        val file = repository.assetFile(packId, relativePath)
        return file.takeIf { it.exists() }
    }

    fun assetDirectory(packId: String, relativeDir: String): File? {
        if (!repository.isInstalled(packId)) return null
        val dir = repository.assetFile(packId, relativeDir)
        return dir.takeIf { it.isDirectory }
    }

    suspend fun ensurePackReady(packId: String): Boolean = withContext(Dispatchers.IO) {
        if (!repository.isInstalled(packId)) return@withContext false
        val entry = catalogProvider.findPack(packId) ?: return@withContext false
        loadMutex.withLock {
            runCatching {
                NativeEnginePackLoader.loadLibraries(
                    repository.nativeLibDir(packId),
                    entry.libraries,
                )
                true
            }.getOrDefault(false)
        }
    }

    suspend fun deletePack(packId: String) = withContext(Dispatchers.IO) {
        repository.deletePack(packId)
    }
}

internal object NativeEnginePackExtractor {
    fun extractZip(zipFile: File, packId: String, repository: NativeEnginePackRepository) {
        val packRoot = repository.ensurePackDirectory(packId)
        val libTarget = repository.nativeLibDir(packId)
        val assetsTarget = repository.assetsRoot(packId)
        libTarget.mkdirs()
        assetsTarget.mkdirs()

        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                val normalized = entry.name.replace('\\', '/').trimStart('/')
                val target = when {
                    normalized.startsWith("lib/$ABI/") ->
                        File(libTarget, normalized.removePrefix("lib/$ABI/"))
                    normalized.startsWith("$ABI/") ->
                        File(libTarget, normalized.removePrefix("$ABI/"))
                    normalized.startsWith("assets/") ->
                        File(assetsTarget, normalized.removePrefix("assets/"))
                    normalized.endsWith(".so") ->
                        File(libTarget, File(normalized).name)
                    else ->
                        File(assetsTarget, normalized)
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private const val ABI = "arm64-v8a"
}
