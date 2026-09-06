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

        noticeStore.getAwaitingNotice(packId)?.let { awaiting ->
            if (awaiting.targetRevision == entry.packRevision &&
                noticeStore.shouldShowNotice(packId, entry.packRevision)
            ) {
                return@withContext awaiting.toNotice(entry.displayVersion)
            }
            noticeStore.clearAwaitingNotice(packId)
        }

        when (val result = coordinator.upgradePackIfOutdated(packId)) {
            is NativeEnginePackUpgradeResult.Upgraded ->
                rememberUpgradedNotice(entry, result)
            is NativeEnginePackUpgradeResult.UpgradeFailed ->
                rememberDownloadNotice(entry, result.displayVersion, result.targetRevision)
            NativeEnginePackUpgradeResult.UpToDate -> {
                if (noticeStore.hasPendingUpgrade(packId, entry.packRevision) &&
                    !coordinator.isPackInstalled(packId)
                ) {
                    rememberDownloadNotice(entry, entry.displayVersion, entry.packRevision)
                } else if (
                    coordinator.isPackInstalled(packId) &&
                    coordinator.installedPackRevision(packId) == entry.packRevision &&
                    noticeStore.shouldShowNotice(packId, entry.packRevision)
                ) {
                    rememberUpgradedNotice(
                        entry = entry,
                        result = NativeEnginePackUpgradeResult.Upgraded(
                            displayVersion = entry.displayVersion,
                            previousRevision = null,
                        ),
                    )
                } else {
                    null
                }
            }
            NativeEnginePackUpgradeResult.FreshlyProvisioned -> {
                noticeStore.markNoticeShown(packId, entry.packRevision)
                null
            }
            else -> null
        }
    }

    fun markNoticeDismissed(notice: OcrEnginePackMigrationNotice) {
        noticeStore.markNoticeShown(NativeEnginePackIds.OCR, notice.targetRevision)
        noticeStore.clearMigrationState(NativeEnginePackIds.OCR)
    }

    private fun rememberUpgradedNotice(
        entry: NativeEnginePackEntry,
        result: NativeEnginePackUpgradeResult.Upgraded,
    ): OcrEnginePackMigrationNotice? {
        if (!noticeStore.shouldShowNotice(NativeEnginePackIds.OCR, entry.packRevision)) {
            noticeStore.clearMigrationState(NativeEnginePackIds.OCR)
            return null
        }
        val notice = OcrEnginePackMigrationNotice.Upgraded(
            displayVersion = result.displayVersion,
            targetRevision = entry.packRevision,
            previousRevision = result.previousRevision,
        )
        noticeStore.setAwaitingNotice(
            packId = NativeEnginePackIds.OCR,
            targetRevision = entry.packRevision,
            kind = NativeEnginePackMigrationNoticeStore.AwaitingNoticeKind.UPGRADED,
            previousRevision = result.previousRevision,
        )
        return notice
    }

    private fun rememberDownloadNotice(
        entry: NativeEnginePackEntry,
        displayVersion: String?,
        targetRevision: Int,
    ): OcrEnginePackMigrationNotice? {
        if (!noticeStore.shouldShowNotice(NativeEnginePackIds.OCR, targetRevision)) {
            return null
        }
        val notice = OcrEnginePackMigrationNotice.DownloadRequired(
            displayVersion = displayVersion,
            targetRevision = targetRevision,
        )
        noticeStore.setAwaitingNotice(
            packId = NativeEnginePackIds.OCR,
            targetRevision = targetRevision,
            kind = NativeEnginePackMigrationNoticeStore.AwaitingNoticeKind.DOWNLOAD_REQUIRED,
            previousRevision = null,
        )
        return notice
    }

    private fun NativeEnginePackMigrationNoticeStore.AwaitingNoticeData.toNotice(
        displayVersion: String?,
    ): OcrEnginePackMigrationNotice = when (kind) {
        NativeEnginePackMigrationNoticeStore.AwaitingNoticeKind.UPGRADED ->
            OcrEnginePackMigrationNotice.Upgraded(
                displayVersion = displayVersion,
                targetRevision = targetRevision,
                previousRevision = previousRevision,
            )
        NativeEnginePackMigrationNoticeStore.AwaitingNoticeKind.DOWNLOAD_REQUIRED ->
            OcrEnginePackMigrationNotice.DownloadRequired(
                displayVersion = displayVersion,
                targetRevision = targetRevision,
            )
    }
}
