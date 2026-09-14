package com.slideindex.app.gesture

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.actionFor
import com.slideindex.app.gesture.isEffective
import kotlin.math.hypot

internal object EdgeTriggerDoubleTapCoordinator {

    private val handler = Handler(Looper.getMainLooper())
    private var lastTapUpTime = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f
    private var lastSide: PanelSide? = null
    private var lastHandleId: String? = null
    private var pendingSingle: PendingSingle? = null

    private data class PendingSingle(
        val runnable: Runnable,
    )

    fun shouldDeferSingleTap(settings: AppSettings, side: PanelSide, handleId: String): Boolean {
        val interval = settings.triggerDoubleTapIntervalMs
        if (interval <= 0) return false
        val action = settings.actionFor(side, GestureTriggerType.SHORT_DOUBLE_TAP, handleId)
        return action.isEffective()
    }

    fun onSingleTapCandidate(
        settings: AppSettings,
        side: PanelSide,
        handleId: String,
        upX: Float,
        upY: Float,
        slopPx: Float,
        onSingleTap: () -> Unit,
        onDoubleTap: () -> Unit,
    ) {
        if (!shouldDeferSingleTap(settings, side, handleId)) {
            onSingleTap()
            return
        }
        val interval = settings.triggerDoubleTapIntervalMs.toLong()
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = now - lastTapUpTime <= interval &&
            lastSide == side &&
            lastHandleId == handleId &&
            hypot(upX - lastTapUpX, upY - lastTapUpY) <= slopPx * 2f
        if (isDoubleTap) {
            cancelPendingSingle()
            lastTapUpTime = 0L
            onDoubleTap()
            return
        }
        cancelPendingSingle()
        lastTapUpTime = now
        lastTapUpX = upX
        lastTapUpY = upY
        lastSide = side
        lastHandleId = handleId
        val runnable = Runnable {
            pendingSingle = null
            onSingleTap()
        }
        pendingSingle = PendingSingle(runnable)
        handler.postDelayed(runnable, interval)
    }

    private fun cancelPendingSingle() {
        pendingSingle?.runnable?.let { handler.removeCallbacks(it) }
        pendingSingle = null
    }
}
