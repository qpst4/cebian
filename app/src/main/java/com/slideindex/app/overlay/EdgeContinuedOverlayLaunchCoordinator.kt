package com.slideindex.app.overlay

import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.slideindex.app.settings.AppSettings
import java.util.ArrayDeque

/**
 * Launches edge-continued overlays outside the edge capture touch dispatch stack.
 * Buffers MOVE/UP until the target overlay is ready to consume forwarded events.
 */
internal object EdgeContinuedOverlayLaunchCoordinator {
    private const val TAG = "EdgeContinuedLaunch"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingEvents = ArrayDeque<MotionEvent>()
    private var launchScheduled = false
    private var pendingLaunch: (() -> Unit)? = null

    fun isHandoffActive(): Boolean = EdgeContinuedOverlayHandoff.active

    fun onEdgeMoveWhileHandoff(event: MotionEvent): Boolean {
        if (!EdgeContinuedOverlayHandoff.active) return false
        if (tryForward(event)) return true
        pendingEvents.addLast(MotionEvent.obtain(event))
        return true
    }

    fun scheduleRegionalPick(
        context: android.content.Context,
        settings: AppSettings,
        rawX: Float,
        rawY: Float,
    ) {
        Log.i(TAG, "scheduleRegionalPick at ($rawX, $rawY)")
        armHandoff()
        RegionalPickOverlay.armContinuedHandoff()
        scheduleLaunch {
            Log.i(TAG, "launchRegionalPick at ($rawX, $rawY)")
            RegionalPickOverlay.launchFromEdge(context, settings, rawX, rawY)
        }
    }

    fun scheduleFloatingPointer(
        context: android.content.Context,
        settings: AppSettings,
        rawX: Float,
        rawY: Float,
    ) {
        Log.i(TAG, "scheduleFloatingPointer at ($rawX, $rawY)")
        armHandoff()
        FloatingPointerOverlayWindow.armContinuedHandoff()
        scheduleLaunch {
            Log.i(TAG, "launchFloatingPointer at ($rawX, $rawY)")
            FloatingPointerOverlayWindow.launchContinuedFromEdge(context, settings, rawX, rawY)
        }
    }

    fun flushPendingAfterLaunch() {
        while (pendingEvents.isNotEmpty()) {
            val event = pendingEvents.removeFirst()
            tryForward(event)
            event.recycle()
        }
    }

    fun clearAfterHandoffEnd() {
        pendingEvents.forEach { it.recycle() }
        pendingEvents.clear()
        pendingLaunch = null
        launchScheduled = false
        EdgeContinuedOverlayHandoff.clear()
    }

    private fun armHandoff() {
        EdgeContinuedOverlayHandoff.begin()
    }

    private fun scheduleLaunch(block: () -> Unit) {
        pendingLaunch = block
        if (launchScheduled) return
        launchScheduled = true
        mainHandler.post {
            launchScheduled = false
            val launch = pendingLaunch
            pendingLaunch = null
            if (launch == null) return@post
            launch()
            flushPendingAfterLaunch()
        }
    }

    private fun tryForward(event: MotionEvent): Boolean {
        if (RegionalPickOverlay.forwardContinuedTouch(event)) return true
        return FloatingPointerOverlayWindow.forwardContinuedTouch(event)
    }
}
