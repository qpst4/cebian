package com.slideindex.app.service

import android.content.Context
import com.slideindex.app.di.AppGraphEntryPoint
import dagger.hilt.android.EntryPointAccessors

object ShareImageOcrDependencyAccess {
    fun historyRepository(context: Context): ShareImageOcrHistoryRepository? =
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AppGraphEntryPoint::class.java,
            ).dependencies().shareImageOcrHistoryRepository
        }.getOrNull()
}
