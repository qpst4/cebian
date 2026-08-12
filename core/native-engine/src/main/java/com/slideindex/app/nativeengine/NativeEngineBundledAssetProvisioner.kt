package com.slideindex.app.nativeengine

import android.content.Context
import java.io.File

/**
 * 从 APK assets 内置的引擎 zip 解压到 files，避免网络下载。
 * zip 在构建时由 Gradle 从 [build/native-engine-packs] 复制进 assets。
 */
internal object NativeEngineBundledAssetProvisioner {
    private const val ASSET_DIR = "bundled-native-engine"

    fun bundledAssetPath(packId: String): String = "$ASSET_DIR/$packId.zip"

    fun hasBundledAsset(context: Context, packId: String): Boolean =
        runCatching {
            context.assets.open(bundledAssetPath(packId)).close()
            true
        }.getOrDefault(false)

    fun provisionFromAssetsIfNeeded(
        context: Context,
        packId: String,
        entry: NativeEnginePackEntry,
        repository: NativeEnginePackRepository,
        catalogVersion: Int,
    ): Boolean {
        if (!hasBundledAsset(context, packId)) return false
        val zipFile = repository.downloadZipFile(packId)
        if (zipFile.exists()) zipFile.delete()
        zipFile.parentFile?.mkdirs()
        context.assets.open(bundledAssetPath(packId)).use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        }
        // Bundled zips are built into the signed APK; skip remote-catalog sha256 check.
        repository.packRoot(packId).deleteRecursively()
        NativeEnginePackExtractor.extractZip(zipFile, packId, repository)
        zipFile.delete()

        val libDir = repository.nativeLibDir(packId)
        val missingLibrary = entry.libraries.firstOrNull { name ->
            !File(libDir, name).isFile
        }
        if (missingLibrary != null) {
            repository.deletePack(packId)
            return false
        }
        for (assetPath in entry.assetPaths) {
            if (!repository.assetFile(packId, assetPath).isFile) {
                repository.deletePack(packId)
                return false
            }
        }
        repository.writeManifest(
            NativeEnginePackInstallManifest(
                packId = packId,
                catalogVersion = catalogVersion,
                packRevision = entry.packRevision,
                displayVersion = entry.displayVersion,
                installedAtEpochMs = System.currentTimeMillis(),
                sizeBytes = entry.sizeBytes,
            ),
        )
        return true
    }
}
