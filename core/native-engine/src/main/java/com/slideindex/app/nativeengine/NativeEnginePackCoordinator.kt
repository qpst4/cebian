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
    private val provisionMutex = Mutex()

    fun isPackInstalled(packId: String): Boolean {
        if (!repository.isInstalled(packId)) return false
        val entry = catalogProvider.findPack(packId) ?: return false
        val libDir = repository.nativeLibDir(packId)
        val librariesReady = entry.libraries.all { name -> File(libDir, name).isFile }
        if (!librariesReady) return false
        return entry.assetPaths.all { path -> repository.assetFile(packId, path).isFile }
    }

    fun assetFile(packId: String, relativePath: String): File? {
        if (!isPackInstalled(packId)) return null
        val file = repository.assetFile(packId, relativePath)
        return file.takeIf { it.exists() }
    }

    fun assetDirectory(packId: String, relativeDir: String): File? {
        if (!isPackInstalled(packId)) return null
        val dir = repository.assetFile(packId, relativeDir)
        return dir.takeIf { it.isDirectory }
    }

    suspend fun ensurePackReady(packId: String): Boolean = withContext(Dispatchers.IO) {
        ensurePackProvisioned(packId)
        if (!repository.isInstalled(packId)) return@withContext false
        val entry = catalogProvider.findPack(packId) ?: return@withContext false
        loadMutex.withLock {
            runCatching {
                val revision = repository.readManifest(packId)?.packRevision
                NativeEnginePackLoader.loadLibraries(
                    repository.nativeLibDir(packId),
                    entry.libraries,
                    packRevision = if (packId == NativeEnginePackIds.OCR) revision else null,
                )
                true
            }.getOrDefault(false)
        }
    }

    suspend fun deletePack(packId: String) = withContext(Dispatchers.IO) {
        repository.deletePack(packId)
    }

    fun installedPackRevision(packId: String): Int? =
        repository.readManifest(packId)?.packRevision

    fun installedDisplayVersion(packId: String): String? =
        repository.readManifest(packId)?.displayVersion

    fun isPackUpdateAvailable(packId: String): Boolean {
        val entry = catalogProvider.findPack(packId) ?: return false
        if (!repository.isInstalled(packId)) return false
        val manifest = repository.readManifest(packId) ?: return true
        if (manifest.catalogVersion < catalogProvider.catalog.version) return true
        return manifest.packRevision < entry.packRevision
    }

    fun packVersionState(packId: String): NativeEnginePackVersionState? {
        val entry = catalogProvider.findPack(packId) ?: return null
        return NativeEnginePackVersionState(
            installedRevision = installedPackRevision(packId),
            installedDisplayVersion = installedDisplayVersion(packId),
            latestRevision = entry.packRevision,
            latestDisplayVersion = entry.displayVersion,
            updateAvailable = isPackUpdateAvailable(packId),
        )
    }

    /**
     * 若本地引擎包 revision/catalog 落后，则删除旧包并尝试从 APK 内置资源重装。
     * 用于应用升级后与 Java 层 ORT 版本对齐。
     */
    suspend fun upgradePackIfOutdated(packId: String): NativeEnginePackUpgradeResult =
        withContext(Dispatchers.IO) {
            val entry = catalogProvider.findPack(packId) ?: return@withContext NativeEnginePackUpgradeResult.UpToDate
            val catalogVersion = catalogProvider.catalog.version
            val manifest = repository.readManifest(packId)
            val hadInstalled = repository.isInstalled(packId)
            val hadOutdatedInstall = hadInstalled && (
                repository.needsCatalogUpgrade(packId, catalogVersion) ||
                    repository.needsPackRevisionUpgrade(packId, entry.packRevision)
                )
            val previousRevision = manifest?.packRevision

            ensurePackProvisioned(packId)

            when {
                hadOutdatedInstall && isPackInstalled(packId) ->
                    NativeEnginePackUpgradeResult.Upgraded(entry.displayVersion, previousRevision)
                hadOutdatedInstall && !isPackInstalled(packId) ->
                    NativeEnginePackUpgradeResult.UpgradeFailed(entry.displayVersion, entry.packRevision)
                !hadInstalled && isPackInstalled(packId) ->
                    NativeEnginePackUpgradeResult.FreshlyProvisioned
                else -> NativeEnginePackUpgradeResult.UpToDate
            }
        }

    private suspend fun ensurePackProvisioned(packId: String) {
        val entry = catalogProvider.findPack(packId) ?: return
        val catalogVersion = catalogProvider.catalog.version
        provisionMutex.withLock {
            val deletingOcrPack = packId == NativeEnginePackIds.OCR && (
                repository.needsCatalogUpgrade(packId, catalogVersion) ||
                    repository.needsPackRevisionUpgrade(packId, entry.packRevision)
                )
            if (repository.needsCatalogUpgrade(packId, catalogVersion)) {
                if (deletingOcrPack) {
                    NativeEngineRuntime.onOcrEnginePackInvalidated?.invoke()
                }
                repository.deletePack(packId)
            } else if (repository.needsPackRevisionUpgrade(packId, entry.packRevision)) {
                if (deletingOcrPack) {
                    NativeEngineRuntime.onOcrEnginePackInvalidated?.invoke()
                }
                repository.deletePack(packId)
            }
            if (isPackInstalled(packId)) return
            NativeEngineBundledAssetProvisioner.provisionFromAssetsIfNeeded(
                context = context,
                packId = packId,
                entry = entry,
                repository = repository,
                catalogVersion = catalogVersion,
            )
        }
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
