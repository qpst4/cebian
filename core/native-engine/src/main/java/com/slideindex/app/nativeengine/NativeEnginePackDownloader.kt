package com.slideindex.app.nativeengine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class NativeEnginePackDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogProvider: NativeEnginePackCatalogProvider,
    private val repository: NativeEnginePackRepository,
    private val coordinator: NativeEnginePackCoordinator,
) {
    private val httpDownloader = NativeEnginePackHttpDownloader()

    fun isDownloading(packId: String): Boolean =
        NativeEnginePackDownloadController.activePackId == packId

    /**
     * 若引擎包未安装则下载并加载；已安装则仅确保 native 库可用。
     * [onState] 用于串联下载（不写入 [NativeEnginePackDownloadController]）。
     */
    suspend fun ensurePackDownloaded(
        packId: String,
        wifiOnly: Boolean,
        onState: (NativeEnginePackDownloadState) -> Unit = ::emitState,
    ): Boolean = withContext(Dispatchers.IO) {
        if (coordinator.isPackInstalled(packId)) {
            return@withContext coordinator.ensurePackReady(packId)
        }
        when (val result = runDownload(packId, wifiOnly, onState)) {
            is DownloadRunResult.Success -> coordinator.ensurePackReady(packId)
            is DownloadRunResult.Failed -> false
        }
    }

    suspend fun executeDownload(packId: String, wifiOnly: Boolean) = withContext(Dispatchers.IO) {
        try {
            when (val result = runDownload(packId, wifiOnly, ::emitState)) {
                is DownloadRunResult.Success -> emitState(result.state)
                is DownloadRunResult.Failed -> emitState(
                    NativeEnginePackDownloadState(
                        packId = packId,
                        phase = NativeEnginePackDownloadPhase.FAILED,
                        errorMessage = result.message,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            emitState(
                NativeEnginePackDownloadState(
                    packId = packId,
                    phase = NativeEnginePackDownloadPhase.CANCELLED,
                ),
            )
            throw cancelled
        } catch (error: Throwable) {
            emitState(
                NativeEnginePackDownloadState(
                    packId = packId,
                    phase = NativeEnginePackDownloadPhase.FAILED,
                    errorMessage = error.message ?: error.javaClass.simpleName,
                ),
            )
        }
    }

    suspend fun deletePack(packId: String) = withContext(Dispatchers.IO) {
        repository.deletePack(packId)
    }

    private sealed interface DownloadRunResult {
        data class Success(val state: NativeEnginePackDownloadState) : DownloadRunResult
        data class Failed(val message: String) : DownloadRunResult
    }

    private suspend fun runDownload(
        packId: String,
        wifiOnly: Boolean,
        onState: (NativeEnginePackDownloadState) -> Unit,
    ): DownloadRunResult {
        val entry = catalogProvider.findPack(packId)
            ?: return DownloadRunResult.Failed("pack_not_found")

        if (wifiOnly && !isOnWifi()) {
            return DownloadRunResult.Failed("wifi_required")
        }

        val zipFile = repository.downloadZipFile(packId)
        if (zipFile.exists()) zipFile.delete()

        val urls = buildList {
            addAll(entry.mirrorUrls)
            add(entry.url)
        }.distinct()

        onState(
            NativeEnginePackDownloadState(
                packId = packId,
                phase = NativeEnginePackDownloadPhase.DOWNLOADING,
                totalBytes = entry.sizeBytes.takeIf { it > 0L },
            ),
        )

        httpDownloader.downloadFileWithFallback(
            urls = urls,
            output = zipFile,
        ) { bytesDownloaded ->
            onState(
                NativeEnginePackDownloadState(
                    packId = packId,
                    phase = NativeEnginePackDownloadPhase.DOWNLOADING,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = entry.sizeBytes.takeIf { it > 0L },
                ),
            )
        }

        onState(
            NativeEnginePackDownloadState(
                packId = packId,
                phase = NativeEnginePackDownloadPhase.VERIFYING,
                bytesDownloaded = zipFile.length(),
                totalBytes = entry.sizeBytes.takeIf { it > 0L },
            ),
        )

        if (!NativeEnginePackChecksum.matches(zipFile, entry.sha256)) {
            zipFile.delete()
            return DownloadRunResult.Failed("checksum_mismatch")
        }

        onState(
            NativeEnginePackDownloadState(
                packId = packId,
                phase = NativeEnginePackDownloadPhase.EXTRACTING,
                bytesDownloaded = zipFile.length(),
                totalBytes = entry.sizeBytes.takeIf { it > 0L },
            ),
        )

        repository.deletePack(packId)
        NativeEnginePackExtractor.extractZip(zipFile, packId, repository)
        zipFile.delete()

        val libDir = repository.nativeLibDir(packId)
        val missingLibrary = entry.libraries.firstOrNull { name ->
            !java.io.File(libDir, name).isFile
        }
        if (missingLibrary != null) {
            repository.deletePack(packId)
            return DownloadRunResult.Failed("native_library_missing:$missingLibrary")
        }

        for (assetPath in entry.assetPaths) {
            if (!repository.assetFile(packId, assetPath).isFile) {
                repository.deletePack(packId)
                return DownloadRunResult.Failed("asset_missing:$assetPath")
            }
        }

        repository.writeManifest(
            NativeEnginePackInstallManifest(
                packId = packId,
                catalogVersion = catalogProvider.catalog.version,
                installedAtEpochMs = System.currentTimeMillis(),
                sizeBytes = entry.sizeBytes,
            ),
        )

        return DownloadRunResult.Success(
            NativeEnginePackDownloadState(
                packId = packId,
                phase = NativeEnginePackDownloadPhase.READY,
                bytesDownloaded = entry.sizeBytes,
                totalBytes = entry.sizeBytes,
            ),
        )
    }

    private fun emitState(state: NativeEnginePackDownloadState) {
        NativeEnginePackDownloadController.update(state)
    }

    private fun isOnWifi(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
