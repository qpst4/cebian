package com.slideindex.app.di

import android.content.Context
import com.slideindex.app.service.SlideIndexAccessibilityService
import dagger.hilt.android.EntryPointAccessors

/** Resolves overlay DI without reaching into [SlideIndexAccessibilityService] fields. */
object OverlayDependencyAccess {
    fun overlayDependencies(context: Context): OverlayDependencies? =
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                OverlayEntryPoint::class.java,
            ).overlayDependencies()
        }.getOrNull()

    fun overlayHostContext(): Context? = SlideIndexAccessibilityService.overlayHostContext()
}
