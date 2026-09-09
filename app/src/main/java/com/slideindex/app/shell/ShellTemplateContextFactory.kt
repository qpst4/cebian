package com.slideindex.app.shell

import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService

object ShellTemplateContextFactory {
    fun current(timestampMs: Long = System.currentTimeMillis()): ShellTemplateContext =
        ShellTemplateContext(
            foregroundPackage = OverlayService.foregroundPackage
                ?: SlideIndexAccessibilityService.currentForegroundPackage(),
            timestampMs = timestampMs
        )
}
