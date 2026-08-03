package com.slideindex.app.ocr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Singleton
class OcrModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogProvider: OcrModelCatalogProvider,
    private val repository: OcrModelRepository,
    private val installIntegrity: OcrInstalledModelIntegrity,
    private val mlKitChineseModuleInstaller: MlKitChineseModuleInstaller,
) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(60.seconds)
        .readTimeout(10.minutes)
        .writeTimeout(10.minutes)
        .build()

    private val httpDownloader = OcrModelHttpDownloader(client)

    fun isDownloading(modelId: String): Boolean =
        OcrModelDownloadController.activeModelId == modelId

    suspend fun executeDownload(modelId: String, wifiOnly: Boolean) = withContext(Dispatchers.IO) {
        try {
            when (val result = runDownload(modelId, wifiOnly)) {
                is DownloadRunResult.Success -> emitState(result.state)
                is DownloadRunResult.Failed -> emitState(
                    OcrModelDownloadState(
                        modelId = modelId,
                        phase = OcrModelDownloadPhase.FAILED,
                        errorMessage = result.message,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            emitState(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.CANCELLED,
                ),
            )
            throw cancelled
        } catch (error: Throwable) {
            emitState(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.FAILED,
                    errorMessage = error.message ?: error.javaClass.simpleName,
                ),
            )
        }
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        installIntegrity.removeInstall(modelId)
    }

    private sealed interface DownloadRunResult {
        data class Success(val state: OcrModelDownloadState) : DownloadRunResult
        data class Failed(val message: String) : DownloadRunResult
    }

    private suspend fun runDownload(modelId: String, wifiOnly: Boolean): DownloadRunResult {
        val entry = catalogProvider.findModel(modelId)
            ?: return DownloadRunResult.Failed("model_not_found")

        if (entry.engine == OcrEngines.MLKIT_CHINESE) {
            return runMlKitDownload(modelId, wifiOnly)
        }

        if (wifiOnly && !isOnWifi()) {
            return DownloadRunResult.Failed("wifi_required")
        }

        repository.ensureModelDirectory(modelId)
        var totalDownloaded = 0L
        val totalBytes = entry.totalDownloadBytes.takeIf { it > 0L }

        for ((index, spec) in entry.files.withIndex()) {
            currentCoroutineContext().ensureActive()
            val target = repository.targetFile(modelId, spec.relativePath)
            val partial = repository.partialFile(modelId, spec.relativePath)
            target.parentFile?.mkdirs()

            if (isTargetReady(target, spec.sha256)) {
                totalDownloaded += fileByteCount(target, spec)
                continue
            }

            if (target.exists()) target.delete()
            cleanupCorruptPartial(partial, spec.sha256)

            emitState(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.DOWNLOADING,
                    bytesDownloaded = totalDownloaded,
                    totalBytes = totalBytes,
                    currentFileIndex = index + 1,
                    totalFiles = entry.files.size,
                ),
            )

            val downloadUrls = OcrModelDownloadSupport.resolveDownloadUrls(spec.mirrorUrls, spec.url)
            httpDownloader.downloadFileWithFallback(
                urls = downloadUrls,
                output = partial,
                relativePath = spec.relativePath,
            ) { fileBytesDownloaded ->
                currentCoroutineContext().ensureActive()
                emitState(
                    OcrModelDownloadState(
                        modelId = modelId,
                        phase = OcrModelDownloadPhase.DOWNLOADING,
                        bytesDownloaded = totalDownloaded + fileBytesDownloaded,
                        totalBytes = totalBytes,
                        currentFileIndex = index + 1,
                        totalFiles = entry.files.size,
                    ),
                )
            }

            if (!partial.isFile || partial.length() <= 0L) {
                partial.delete()
                return DownloadRunResult.Failed("download_empty:${spec.relativePath}")
            }

            emitState(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.VERIFYING,
                    bytesDownloaded = totalDownloaded,
                    totalBytes = totalBytes,
                    currentFileIndex = index + 1,
                    totalFiles = entry.files.size,
                ),
            )

            val sha256 = spec.sha256
            if (!sha256.isNullOrBlank()) {
                val actual = OcrModelChecksum.sha256Hex(partial)
                if (!actual.equals(sha256, ignoreCase = true)) {
                    partial.delete()
                    return DownloadRunResult.Failed("checksum_mismatch:${spec.relativePath}")
                }
            }

            emitState(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.FINALIZING,
                    bytesDownloaded = totalDownloaded,
                    totalBytes = totalBytes,
                    currentFileIndex = index + 1,
                    totalFiles = entry.files.size,
                ),
            )

            OcrModelDownloadSupport.finalizeDownloadedFile(partial, target)
            totalDownloaded += fileByteCount(target, spec)
        }

        if (!installIntegrity.commitInstall(
                OcrModelInstallManifest(
                    modelId = modelId,
                    catalogVersion = catalogProvider.catalog.version,
                    installedAtEpochMs = System.currentTimeMillis(),
                    sizeBytes = totalDownloaded,
                ),
            )
        ) {
            return DownloadRunResult.Failed("integrity_check_failed")
        }

        return DownloadRunResult.Success(
            OcrModelDownloadState(
                modelId = modelId,
                phase = OcrModelDownloadPhase.READY,
                bytesDownloaded = totalDownloaded,
                totalBytes = totalDownloaded,
                totalFiles = entry.files.size,
                currentFileIndex = entry.files.size,
            ),
        )
    }

    private fun fileByteCount(file: File, spec: OcrModelFileSpec): Long =
        spec.sizeBytes?.takeIf { it > 0L } ?: file.length()

    private suspend fun runMlKitDownload(modelId: String, wifiOnly: Boolean): DownloadRunResult {
        var lastState: OcrModelDownloadState? = null
        mlKitChineseModuleInstaller.install(modelId, wifiOnly).collect { state ->
            emitState(state)
            lastState = state
        }
        val state = lastState ?: return DownloadRunResult.Failed("mlkit_install_failed")
        return when (state.phase) {
            OcrModelDownloadPhase.READY -> DownloadRunResult.Success(state)
            OcrModelDownloadPhase.CANCELLED -> DownloadRunResult.Failed("cancelled")
            else -> DownloadRunResult.Failed(state.errorMessage ?: "mlkit_install_failed")
        }
    }

    private fun cleanupCorruptPartial(partial: File, sha256: String?) {
        if (!partial.exists()) return
        if (partial.length() <= 0L) {
            partial.delete()
            return
        }
        if (!sha256.isNullOrBlank()) return
        if (partial.length() < 1L) {
            partial.delete()
        }
    }

    private fun isTargetReady(target: File, sha256: String?): Boolean =
        OcrModelChecksum.matches(target, sha256)

    private fun emitState(state: OcrModelDownloadState) {
        OcrModelDownloadController.update(state)
    }

    private fun isOnWifi(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
