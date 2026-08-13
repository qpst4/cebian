package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.forLandscapeEditing
import com.slideindex.app.settings.mergeLandscapeEdits
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.withUpdatedTriggerHandle
import com.slideindex.app.settings.withUpdatedTriggerHandleDesign
import com.slideindex.app.settings.withUpdatedTriggerHandleDistances
import com.slideindex.app.settings.withUpdatedTriggerHandleEdgeWidth
import com.slideindex.app.ui.trigger.TriggerSettingsLandscapeSession

fun AppSettings.withOverlayLayoutPreview(): AppSettings {
    val landscapePreview = TriggerSettingsLandscapeSession.active
    var result = if (landscapePreview) forLandscapeEditing() else this
    val preview = OverlayLayoutPreviewStore.triggerHandlePreview
    if (preview != null) {
        val side = preview.side
        val handleId = preview.handleId
        if (preview.edgeWidthDp != null) {
            result = result.withUpdatedTriggerHandleEdgeWidth(side, handleId, preview.edgeWidthDp)
        }
        if (preview.topFraction != null || preview.bottomFraction != null) {
            val handle = result.triggerHandle(side, handleId)
            if (handle != null) {
                val top = preview.topFraction ?: handle.topFraction
                val bottom = preview.bottomFraction ?: handle.bottomFraction
                val height = (bottom - top).coerceAtLeast(0.05f)
                result = result.withUpdatedTriggerHandle(side, handleId, top, height)
            }
        }
        if (preview.shortSwipeDistanceDp != null || preview.longSwipeDistanceDp != null) {
            result = result.withUpdatedTriggerHandleDistances(
                side = side,
                handleId = handleId,
                shortSwipeDistanceDp = preview.shortSwipeDistanceDp,
                longSwipeDistanceDp = preview.longSwipeDistanceDp,
            )
        }
        if (preview.design != null) {
            result = result.withUpdatedTriggerHandleDesign(side, handleId, preview.design)
        }
    }
    if (landscapePreview) {
        result = mergeLandscapeEdits(result)
    }
    OverlayLayoutPreviewStore.indexHeightFraction?.let { fraction ->
        result = result.copy(indexHeightFraction = fraction)
    }
    return result
}
