package com.slideindex.app.message

import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppMessageForegroundPort @Inject constructor() : MessageForegroundPort {
    override fun foregroundPackage(): String? =
        OverlayService.foregroundPackage ?: SlideIndexAccessibilityService.currentForegroundPackage()
}
