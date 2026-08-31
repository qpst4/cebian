package com.slideindex.app.ocr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class MlKitChineseModuleInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OcrModelRepository,
    private val catalogProvider: OcrModelCatalogProvider,
) {
    private val textRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun isModuleAvailable(): Boolean = try {
        suspendCancellableCoroutine { continuation ->
            try {
                ModuleInstall.getClient(context)
                    .areModulesAvailable(textRecognizer)
                    .addOnSuccessListener { response ->
                        if (continuation.isActive) {
                            continuation.resume(response.areModulesAvailable())
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
            } catch (e: Throwable) {
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
    } catch (e: Throwable) {
        false
    }

    fun install(modelId: String, wifiOnly: Boolean): Flow<OcrModelDownloadState> = callbackFlow {
        if (wifiOnly && !isOnWifi()) {
            trySend(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.FAILED,
                    errorMessage = "wifi_required",
                ),
            )
            close()
            return@callbackFlow
        }

        val moduleInstallClient = try {
            ModuleInstall.getClient(context)
        } catch (e: Throwable) {
            trySend(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.FAILED,
                    errorMessage = e.message ?: "module_install_unavailable",
                ),
            )
            close()
            return@callbackFlow
        }

        if (isModuleAvailable()) {
            writeInstalledManifest(modelId)
            trySend(OcrModelDownloadState(modelId = modelId, phase = OcrModelDownloadPhase.READY))
            close()
            return@callbackFlow
        }

        trySend(
            OcrModelDownloadState(
                modelId = modelId,
                phase = OcrModelDownloadPhase.DOWNLOADING,
                totalBytes = MLKIT_CHINESE_SIZE_BYTES,
            ),
        )

        val listener = InstallStatusListener { update ->
            try {
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                        writeInstalledManifest(modelId)
                        trySend(
                            OcrModelDownloadState(
                                modelId = modelId,
                                phase = OcrModelDownloadPhase.READY,
                                bytesDownloaded = MLKIT_CHINESE_SIZE_BYTES,
                                totalBytes = MLKIT_CHINESE_SIZE_BYTES,
                            ),
                        )
                        close()
                    }
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> {
                        trySend(
                            OcrModelDownloadState(
                                modelId = modelId,
                                phase = OcrModelDownloadPhase.FAILED,
                                errorMessage = "mlkit_module_install_failed",
                            ),
                        )
                        close()
                    }
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
                        trySend(OcrModelDownloadState(modelId = modelId, phase = OcrModelDownloadPhase.CANCELLED))
                        close()
                    }
                    else -> {
                        val progressInfo = update.progressInfo ?: return@InstallStatusListener
                        trySend(
                            OcrModelDownloadState(
                                modelId = modelId,
                                phase = OcrModelDownloadPhase.DOWNLOADING,
                                bytesDownloaded = progressInfo.bytesDownloaded,
                                totalBytes = progressInfo.totalBytesToDownload.takeIf { it > 0L }
                                    ?: MLKIT_CHINESE_SIZE_BYTES,
                            ),
                        )
                    }
                }
            } catch (e: Throwable) {
                trySend(
                    OcrModelDownloadState(
                        modelId = modelId,
                        phase = OcrModelDownloadPhase.FAILED,
                        errorMessage = e.message ?: "mlkit_install_failed",
                    ),
                )
                close()
            }
        }

        try {
            val request = ModuleInstallRequest.newBuilder()
                .addApi(textRecognizer)
                .setListener(listener)
                .build()

            moduleInstallClient.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled()) {
                        writeInstalledManifest(modelId)
                        trySend(OcrModelDownloadState(modelId = modelId, phase = OcrModelDownloadPhase.READY))
                        close()
                    }
                }
                .addOnFailureListener { error ->
                    trySend(
                        OcrModelDownloadState(
                            modelId = modelId,
                            phase = OcrModelDownloadPhase.FAILED,
                            errorMessage = error.message ?: "mlkit_install_failed",
                        ),
                    )
                    close()
                }
        } catch (e: Throwable) {
            trySend(
                OcrModelDownloadState(
                    modelId = modelId,
                    phase = OcrModelDownloadPhase.FAILED,
                    errorMessage = e.message ?: "mlkit_install_failed",
                ),
            )
            close()
        }

        awaitClose {
            try {
                moduleInstallClient.unregisterListener(listener)
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    suspend fun release() {
        try {
            suspendCancellableCoroutine { continuation ->
                try {
                    ModuleInstall.getClient(context)
                        .releaseModules(textRecognizer)
                        .addOnCompleteListener {
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }
                } catch (e: Throwable) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        } catch (e: Throwable) {
            // Ignore
        }
    }

    private fun writeInstalledManifest(modelId: String) {
        repository.writeManifest(
            OcrModelInstallManifest(
                modelId = modelId,
                catalogVersion = catalogProvider.catalog.version,
                installedAtEpochMs = System.currentTimeMillis(),
                sizeBytes = MLKIT_CHINESE_SIZE_BYTES,
            ),
        )
    }

    private fun isOnWifi(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private companion object {
        private const val MLKIT_CHINESE_SIZE_BYTES = 26_214_400L
    }
}
