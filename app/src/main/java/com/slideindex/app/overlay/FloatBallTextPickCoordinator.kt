package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import com.slideindex.app.inspire.InspireCoordinator
import com.slideindex.app.overlay.FloatBallPickResult

/**
 * GestureEVO-style pick entry points. Delegates to [InspireCoordinator].
 */
object FloatBallTextPickCoordinator {
    fun pickAt(
        service: AccessibilityService,
        context: Context,
        rawX: Float,
        rawY: Float,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        onResult: (String?) -> Unit,
    ) = InspireCoordinator.pickAt(
        service,
        context,
        rawX,
        rawY,
        ocrFallbackEnabled,
        ocrModelId,
        onResult,
    )

    fun pickInRect(
        service: AccessibilityService,
        context: Context,
        rect: Rect,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        previewBoundsPick: Boolean = false,
        onResult: (FloatBallPickResult) -> Unit,
    ) = InspireCoordinator.pickInRect(
        service,
        context,
        rect,
        ocrFallbackEnabled,
        ocrModelId,
        previewBoundsPick,
        onResult,
    )

    fun pickOnRelease(
        service: AccessibilityService,
        context: Context,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        regionalRect: Boolean,
        ocrFallbackEnabled: Boolean,
        ocrModelId: String,
        onResult: (FloatBallPickResult) -> Unit,
    ) = InspireCoordinator.pickOnRelease(
        service,
        context,
        startX,
        startY,
        endX,
        endY,
        regionalRect,
        ocrFallbackEnabled,
        ocrModelId,
        onResult,
    )
}
