package com.slideindex.app.nativeengine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeEnginePackMigrationNoticeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShowNotice(packId: String, targetRevision: Int): Boolean {
        val key = noticeKey(packId, targetRevision)
        return !prefs.getBoolean(key, false)
    }

    fun markNoticeShown(packId: String, targetRevision: Int) {
        prefs.edit()
            .putBoolean(noticeKey(packId, targetRevision), true)
            .apply()
    }

    fun setPendingUpgrade(packId: String, targetRevision: Int) {
        prefs.edit()
            .putInt(pendingRevisionKey(packId), targetRevision)
            .apply()
    }

    fun hasPendingUpgrade(packId: String, targetRevision: Int): Boolean =
        prefs.getInt(pendingRevisionKey(packId), -1) == targetRevision

    fun clearPendingUpgrade(packId: String) {
        prefs.edit()
            .remove(pendingRevisionKey(packId))
            .apply()
    }

    fun setAwaitingNotice(
        packId: String,
        targetRevision: Int,
        kind: AwaitingNoticeKind,
        previousRevision: Int?,
    ) {
        prefs.edit()
            .putInt(awaitingRevisionKey(packId), targetRevision)
            .putString(awaitingKindKey(packId), kind.name)
            .putInt(awaitingPreviousKey(packId), previousRevision ?: -1)
            .apply()
    }

    fun getAwaitingNotice(packId: String): AwaitingNoticeData? {
        val revision = prefs.getInt(awaitingRevisionKey(packId), -1)
        if (revision < 0) return null
        val kindName = prefs.getString(awaitingKindKey(packId), null) ?: return null
        val kind = runCatching { AwaitingNoticeKind.valueOf(kindName) }.getOrNull() ?: return null
        val previous = prefs.getInt(awaitingPreviousKey(packId), -1)
        return AwaitingNoticeData(
            targetRevision = revision,
            kind = kind,
            previousRevision = if (previous < 0) null else previous,
        )
    }

    fun clearAwaitingNotice(packId: String) {
        prefs.edit()
            .remove(awaitingRevisionKey(packId))
            .remove(awaitingKindKey(packId))
            .remove(awaitingPreviousKey(packId))
            .apply()
    }

    fun clearMigrationState(packId: String) {
        clearPendingUpgrade(packId)
        clearAwaitingNotice(packId)
    }

    enum class AwaitingNoticeKind {
        UPGRADED,
        DOWNLOAD_REQUIRED,
    }

    data class AwaitingNoticeData(
        val targetRevision: Int,
        val kind: AwaitingNoticeKind,
        val previousRevision: Int?,
    )

    private fun noticeKey(packId: String, targetRevision: Int): String =
        "notice_shown_${packId}_rev_$targetRevision"

    private fun pendingRevisionKey(packId: String): String =
        "pending_upgrade_${packId}_revision"

    private fun awaitingRevisionKey(packId: String): String =
        "awaiting_notice_${packId}_revision"

    private fun awaitingKindKey(packId: String): String =
        "awaiting_notice_${packId}_kind"

    private fun awaitingPreviousKey(packId: String): String =
        "awaiting_notice_${packId}_previous"

    private companion object {
        private const val PREFS_NAME = "native_engine_migration_notice"
    }
}
