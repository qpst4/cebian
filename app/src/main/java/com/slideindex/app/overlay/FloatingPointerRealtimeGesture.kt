package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.util.Log

/**
 * Quick Cursor-style real-time gesture.
 *
 * While the user moves the pointer, each new position is injected as a continuation of the
 * current [GestureDescription.StrokeDescription] via [continueStroke]. This matches the
 * behaviour of QC's `mt0` class.
 */
internal class FloatingPointerRealtimeGesture(
    private val service: AccessibilityService,
    startX: Float,
    startY: Float,
    private val onError: () -> Unit = {},
    private val onFinished: () -> Unit = {}
) {
    private var currentStroke: GestureDescription.StrokeDescription?
    private var lastDispatchedPoint: Point
    private var dispatchInFlight = false
    private var finishing = false
    private var pendingX: Float? = null
    private var pendingY: Float? = null
    private val callback = RealtimeGestureCallback()

    var currentX: Float = startX
        private set
    var currentY: Float = startY
        private set

    init {
        lastDispatchedPoint = Point(startX.toInt(), startY.toInt())
        val path = pathFrom(startX, startY, startX, startY)
        currentStroke = GestureDescription.StrokeDescription(path, 0L, 1L, true)
        dispatchCurrentStroke("init")
    }

    fun updatePosition(x: Float, y: Float) {
        currentX = x
        currentY = y
        if (dispatchInFlight) {
            pendingX = x
            pendingY = y
            return
        }
        dispatchMoveTo(x, y)
    }

    fun finish() {
        if (finishing) return
        finishing = true
        if (dispatchInFlight) return
        dispatchFinishStroke()
    }

    private fun dispatchMoveTo(x: Float, y: Float) {
        val last = lastDispatchedPoint
        if (x.toInt() == last.x && y.toInt() == last.y) return
        val path = pathFrom(last.x.toFloat(), last.y.toFloat(), x, y)
        val stroke = currentStroke ?: return
        val continued = try {
            stroke.continueStroke(path, 0L, 1L, true)
        } catch (e: Exception) {
            Log.e(TAG, "continueStroke failed", e)
            reportError("continueStroke")
            return
        }
        currentStroke = continued
        lastDispatchedPoint = Point(x.toInt(), y.toInt())
        dispatchInFlight = true
        dispatch(continued, "move")
    }

    private fun dispatchFinishStroke() {
        val last = lastDispatchedPoint
        val path = pathFrom(last.x.toFloat(), last.y.toFloat(), currentX, currentY)
        val stroke = currentStroke ?: run {
            onFinished()
            return
        }
        val continued = try {
            stroke.continueStroke(path, 0L, 1L, false)
        } catch (e: Exception) {
            Log.e(TAG, "finish continueStroke failed", e)
            reportError("finish")
            return
        }
        currentStroke = continued
        lastDispatchedPoint = Point(currentX.toInt(), currentY.toInt())
        dispatchInFlight = true
        dispatch(continued, "finish")
    }

    private fun dispatchCurrentStroke(label: String) {
        dispatchInFlight = true
        val stroke = currentStroke ?: return
        dispatch(stroke, label)
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription, label: String) {
        val builder = GestureDescription.Builder().addStroke(stroke)
        val dispatched = try {
            service.dispatchGesture(builder.build(), callback, null)
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture failed ($label)", e)
            false
        }
        if (!dispatched) {
            dispatchInFlight = false
            reportError(label)
        }
    }

    private fun flushPendingMove() {
        val x = pendingX ?: return
        val y = pendingY ?: return
        pendingX = null
        pendingY = null
        if (finishing) return
        dispatchMoveTo(x, y)
    }

    private fun onDispatchCompleted() {
        dispatchInFlight = false
        if (pendingX != null && pendingY != null) {
            flushPendingMove()
            return
        }
        if (finishing) {
            dispatchFinishStroke()
            return
        }
    }

    private fun reportError(label: String) {
        Log.e(TAG, "RealtimeGesture error: $label")
        onError()
    }

    private fun pathFrom(startX: Float, startY: Float, endX: Float, endY: Float): Path =
        Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

    private inner class RealtimeGestureCallback : AccessibilityService.GestureResultCallback() {
        override fun onCancelled(gestureDescription: GestureDescription?) {
            dispatchInFlight = false
            if (finishing) {
                onFinished()
                return
            }
            flushPendingMove()
        }

        override fun onCompleted(gestureDescription: GestureDescription?) {
            if (finishing && currentStroke?.willContinue() != true) {
                dispatchInFlight = false
                onFinished()
                return
            }
            onDispatchCompleted()
        }
    }

    companion object {
        private const val TAG = "FpRealtimeGesture"
    }
}
