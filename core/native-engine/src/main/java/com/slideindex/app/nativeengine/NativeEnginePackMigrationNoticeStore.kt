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

    private fun noticeKey(packId: String, targetRevision: Int): String =
        "notice_shown_${packId}_rev_$targetRevision"

    private companion object {
        private const val PREFS_NAME = "native_engine_migration_notice"
    }
}
