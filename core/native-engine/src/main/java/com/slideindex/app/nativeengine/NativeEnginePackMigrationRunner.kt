package com.slideindex.app.nativeengine

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface OcrEnginePackMigrationNotice {
    val targetRevision: Int
    val displayVersion: String?

    data class Upgraded(
        override val displayVersion: String?,
        override val targetRevision: Int,
        val previousRevision: Int?,
    ) : OcrEnginePackMigrationNotice

    data class DownloadRequired(
        override val displayVersion: String?,
        override val targetRevision: Int,
    ) : OcrEnginePackMigrationNotice
}

@Singleton
class NativeEnginePackMigrationRunner @Inject constructor(
    private val coordinator: NativeEnginePackCoordinator,
    private val catalogProvider: NativeEnginePackCatalogProvider,
    private val noticeStore: NativeEnginePackMigrationNoticeStore,
) {
    suspend fun runOcrStartupMigration(): OcrEnginePackMigrationNotice? = withContext(Dispatchers.IO) {
        val packId = NativeEnginePackIds.OCR
        val entry = catalogProvider.findPack(packId) ?: return@withContext null
        when (val result = coordinator.upgradePackIfOutdated(packId)) {
            is NativeEnginePackUpgradeResult.Upgraded -> {
                if (!noticeStore.shouldShowNotice(packId, entry.packRevision)) return@withContext null
                OcrEnginePackMigrationNotice.Upgraded(
                    displayVersion = result.displayVersion,
                    targetRevision = entry.packRevision,
                    previousRevision = result.previousRevision,
                )
            }
            is NativeEnginePackUpgradeResult.UpgradeFailed -> {
                if (!noticeStore.shouldShowNotice(packId, entry.packRevision)) return@withContext null
                OcrEnginePackMigrationNotice.DownloadRequired(
                    displayVersion = result.displayVersion,
                    targetRevision = entry.packRevision,
                )
            }
            else -> null
        }
    }

    fun markNoticeDismissed(notice: OcrEnginePackMigrationNotice) {
        noticeStore.markNoticeShown(NativeEnginePackIds.OCR, notice.targetRevision)
    }
}
