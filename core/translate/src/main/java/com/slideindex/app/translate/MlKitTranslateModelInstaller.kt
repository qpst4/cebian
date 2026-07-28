package com.slideindex.app.translate

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.slideindex.app.nativeengine.NativeEnginePackCatalogProvider
import com.slideindex.app.nativeengine.NativeEnginePackDownloader
import com.slideindex.app.nativeengine.NativeEnginePackIds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class MlKitTranslateModelInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TranslateModelRepository,
    private val progressTracker: TranslateDownloadProgressTracker,
    private val nativeEnginePackCatalogProvider: NativeEnginePackCatalogProvider,
    private val nativeEnginePackDownloader: NativeEnginePackDownloader,
) {
    suspend fun isModelDownloaded(languageCode: String): Boolean {
        val mlKitCode = toMlKitLanguage(languageCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlKitCode).build()
        return suspendCancellableCoroutine { continuation ->
            RemoteModelManager.getInstance()
                .isModelDownloaded(model)
                .addOnSuccessListener { downloaded ->
                    if (continuation.isActive) {
                        continuation.resume(downloaded)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
        }
    }

    fun download(languageCode: String, wifiOnly: Boolean): Flow<TranslateDownloadState> = channelFlow {
        val mlKitCode = toMlKitLanguage(languageCode)
        if (mlKitCode == null) {
            trySend(
                TranslateDownloadState(
                    languageCode = languageCode,
                    phase = TranslateDownloadPhase.FAILED,
                    errorMessage = "unsupported_language",
                ),
            )
            close()
            return@channelFlow
        }

        if (wifiOnly && !isOnWifi()) {
            trySend(
                TranslateDownloadState(
                    languageCode = languageCode,
                    phase = TranslateDownloadPhase.FAILED,
                    errorMessage = "wifi_required",
                ),
            )
            close()
            return@channelFlow
        }

        val model = TranslateRemoteModel.Builder(mlKitCode).build()
        val remoteModelManager = RemoteModelManager.getInstance()

        if (repository.isInstalled(languageCode) && isModelDownloaded(languageCode)) {
            trySend(TranslateDownloadState(languageCode = languageCode, phase = TranslateDownloadPhase.READY))
            close()
            return@channelFlow
        }

        val engineBytes = nativeEnginePackCatalogProvider.findPack(NativeEnginePackIds.TRANSLATE)
            ?.sizeBytes
            ?.coerceAtLeast(1L)
            ?: run {
                trySend(
                    TranslateDownloadState(
                        languageCode = languageCode,
                        phase = TranslateDownloadPhase.FAILED,
                        errorMessage = "translate_engine_pack_not_found",
                    ),
                )
                close()
                return@channelFlow
            }

        val engineReady = nativeEnginePackDownloader.ensurePackDownloaded(
            packId = NativeEnginePackIds.TRANSLATE,
            wifiOnly = wifiOnly,
        ) { engineState ->
            trySend(
                TranslateEnginePackDownloadMapper.mapEngineState(
                    engineState = engineState,
                    languageCode = languageCode,
                    engineBytes = engineBytes,
                ),
            )
        }
        if (!engineReady) {
            trySend(
                TranslateDownloadState(
                    languageCode = languageCode,
                    phase = TranslateDownloadPhase.FAILED,
                    errorMessage = "translate_engine_download_failed",
                ),
            )
            close()
            return@channelFlow
        }

        fun emitDownloading(bytesDownloaded: Long = 0L, totalBytes: Long? = null) {
            trySend(
                TranslateEnginePackDownloadMapper.withLanguageStep(
                    TranslateDownloadState(
                        languageCode = languageCode,
                        phase = TranslateDownloadPhase.DOWNLOADING,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                    ),
                    engineBytes = engineBytes,
                    languageBytes = totalBytes,
                ),
            )
        }

        emitDownloading()

        var failedMessage: String? = null
        var completed = false

        val downloadTask = remoteModelManager.download(
            model,
            DownloadConditions.Builder().apply {
                if (wifiOnly) requireWifi()
            }.build(),
        )

        downloadTask
            .addOnSuccessListener {
                repository.writeInstalled(languageCode)
                completed = true
                trySend(TranslateDownloadState(languageCode = languageCode, phase = TranslateDownloadPhase.READY))
                close()
            }
            .addOnFailureListener { error ->
                failedMessage = error.message ?: "download_failed"
                if (!completed) {
                    trySend(
                        TranslateDownloadState(
                            languageCode = languageCode,
                            phase = TranslateDownloadPhase.FAILED,
                            errorMessage = failedMessage,
                        ),
                    )
                    close()
                }
            }

        val progressJob = launch {
            while (isActive && !completed && failedMessage == null) {
                val progress = progressTracker.queryProgress(mlKitCode)
                if (progress != null) {
                    emitDownloading(progress.bytesDownloaded, progress.totalBytes)
                }
                if (isModelDownloaded(languageCode)) {
                    break
                }
                delay(PROGRESS_POLL_INTERVAL_MS)
            }
        }

        awaitClose {
            progressJob.cancel()
        }
    }

    suspend fun delete(languageCode: String) {
        val mlKitCode = toMlKitLanguage(languageCode) ?: return
        val model = TranslateRemoteModel.Builder(mlKitCode).build()
        suspendCancellableCoroutine { continuation ->
            RemoteModelManager.getInstance()
                .deleteDownloadedModel(model)
                .addOnCompleteListener {
                    repository.deleteLanguage(languageCode)
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
        }
    }

    fun toMlKitLanguage(languageCode: String): String? {
        val option = TranslateLanguageCatalog.find(languageCode) ?: return null
        return when (option.mlKitLanguage) {
            "zh" -> TranslateLanguage.CHINESE
            "en" -> TranslateLanguage.ENGLISH
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "es" -> TranslateLanguage.SPANISH
            "ru" -> TranslateLanguage.RUSSIAN
            else -> null
        }
    }

    private fun isOnWifi(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private companion object {
        private const val PROGRESS_POLL_INTERVAL_MS = 350L
    }
}
