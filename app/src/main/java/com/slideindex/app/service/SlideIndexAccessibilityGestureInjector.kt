package com.slideindex.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.gesture.PointerSwipeDirection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object SlideIndexAccessibilityGestureInjector {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingScrollRunnable: Runnable? = null

    fun perform(action: GestureAction, serviceProvider: () -> SlideIndexAccessibilityService?): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = CountDownLatch(1)
            mainHandler.post {
                result = performOnMain(action, serviceProvider)
                latch.countDown()
            }
            runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
            return result
        }
        return performOnMain(action, serviceProvider)
    }

    private fun performOnMain(
        action: GestureAction,
        serviceProvider: () -> SlideIndexAccessibilityService?,
    ): Boolean {
        val service = serviceProvider()
        if (service == null) {
            Log.w(TAG, "perform($action): accessibility service not connected")
            return false
        }
        val result = when (action) {
            GestureAction.Back -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            GestureAction.Home -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            GestureAction.Recents -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            GestureAction.OpenNotifications ->
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.OpenQuickSettings ->
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.LockScreen ->
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            GestureAction.Screenshot -> {
                service.takeScreenshotDelayed()
                true
            }
            GestureAction.PowerMenu ->
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            GestureAction.PreviousApp -> service.launchPreviousApp()
            GestureAction.KeepScreenOn -> service.toggleKeepScreenOn()
            GestureAction.ScrollToTop -> scheduleFastVerticalScroll(service, toTop = true)
            GestureAction.ScrollToBottom -> scheduleFastVerticalScroll(service, toTop = false)
            else -> false
        }
        if (result) {
            Log.i(TAG, "perform($action): ok")
        } else {
            Log.w(TAG, "perform($action): returned false")
        }
        return result
    }

    private fun scheduleFastVerticalScroll(service: SlideIndexAccessibilityService, toTop: Boolean): Boolean {
        pendingScrollRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingScrollRunnable = null
            service.fastVerticalScroll(toTop)
        }
        pendingScrollRunnable = runnable
        mainHandler.postDelayed(runnable, SCROLL_GESTURE_DELAY_MS)
        return true
    }

    fun dispatchPointerTap(
        service: AccessibilityService?,
        rawX: Float,
        rawY: Float,
        onFinished: (Boolean) -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatchPointerTap(service, rawX, rawY, onFinished) }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchPointerTap($rawX, $rawY): service instance is null")
            onFinished(false)
            return
        }
        dispatchGestureTap(service, rawX, rawY, POINTER_TAP_DURATION_MS) { gestureOk ->
            if (gestureOk) {
                onFinished(true)
                return@dispatchGestureTap
            }
            onFinished(clickNodeAt(service, rawX, rawY))
        }
    }

    fun dispatchTap(
        service: AccessibilityService?,
        rawX: Float,
        rawY: Float,
        onFinished: (Boolean) -> Unit,
        durationMs: Long = TAP_DURATION_MS,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatchTap(service, rawX, rawY, onFinished, durationMs) }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchTap($rawX, $rawY): service instance is null")
            onFinished(false)
            return
        }
        Log.i(TAG, "dispatchTap start ($rawX, $rawY) duration=${durationMs}ms")
        dispatchGestureTap(service, rawX, rawY, durationMs) { gestureOk ->
            if (gestureOk) {
                Log.i(TAG, "dispatchTap($rawX, $rawY): gesture completed")
                onFinished(true)
                return@dispatchGestureTap
            }
            val nodeOk = clickNodeAt(service, rawX, rawY)
            if (nodeOk) {
                Log.i(TAG, "dispatchTap($rawX, $rawY): node ACTION_CLICK succeeded")
            } else {
                Log.w(TAG, "dispatchTap($rawX, $rawY): all paths failed")
            }
            onFinished(nodeOk)
        }
    }

    fun dispatchPointerSwipe(
        service: AccessibilityService?,
        startX: Float,
        startY: Float,
        config: PointerSwipeConfig,
        onFinished: (Boolean) -> Unit = {},
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatchPointerSwipe(service, startX, startY, config, onFinished) }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchPointerSwipe: service instance is null")
            onFinished(false)
            return
        }
        val density = service.resources.displayMetrics.density
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenHeight = metrics.heightPixels.toFloat().coerceAtLeast(1f)
        val (dx, dy) = config.delta(density)
        val endX = startX + dx
        val endY = startY + dy
        val durationMs = config.durationMs.toLong().coerceIn(20L, 800L)
        val pointerCount = config.pointerCount.coerceIn(1, 5)
        val sanitized = sanitizeSwipeEndpoints(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            minDistancePx = 24f * density,
        )
        if (sanitized == null) {
            Log.w(TAG, "dispatchPointerSwipe: invalid path at ($startX, $startY)")
            val scrolled = scrollNodeAt(service, startX, startY, config.direction)
            onFinished(scrolled)
            return
        }
        val (safeStartX, safeStartY, safeEndX, safeEndY) = sanitized
        val builder = GestureDescription.Builder()
        for (index in 0 until pointerCount) {
            val spread = (index - (pointerCount - 1) / 2f) * 28f
            val offsetX = if (kotlin.math.abs(safeEndY - safeStartY) >= kotlin.math.abs(safeEndX - safeStartX)) {
                spread
            } else {
                0f
            }
            val offsetY = if (kotlin.math.abs(safeEndX - safeStartX) > kotlin.math.abs(safeEndY - safeStartY)) {
                spread
            } else {
                0f
            }
            val strokeEndpoints = sanitizeSwipeEndpoints(
                startX = safeStartX + offsetX,
                startY = safeStartY + offsetY,
                endX = safeEndX + offsetX,
                endY = safeEndY + offsetY,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                minDistancePx = 8f * density,
            ) ?: continue
            val path = buildSwipePath(
                startX = strokeEndpoints.startX,
                startY = strokeEndpoints.startY,
                endX = strokeEndpoints.endX,
                endY = strokeEndpoints.endY,
            )
            val stroke = runCatching {
                GestureDescription.StrokeDescription(path, 0, durationMs)
            }.getOrElse { error ->
                Log.w(TAG, "dispatchPointerSwipe: invalid stroke at ($safeStartX, $safeStartY)", error)
                null
            } ?: continue
            builder.addStroke(stroke)
        }
        val gesture = builder.build()
        if (gesture.strokeCount == 0) {
            Log.w(TAG, "dispatchPointerSwipe: no valid strokes at ($startX, $startY)")
            val scrolled = scrollNodeAt(service, startX, startY, config.direction)
            onFinished(scrolled)
            return
        }
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "dispatchPointerSwipe completed at ($startX, $startY)")
                    onFinished(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "dispatchPointerSwipe cancelled at ($startX, $startY)")
                    val scrolled = scrollNodeAt(service, startX, startY, config.direction)
                    onFinished(scrolled)
                }
            },
            null,
        )
        if (!accepted) {
            Log.w(TAG, "dispatchPointerSwipe rejected at ($startX, $startY)")
            val scrolled = scrollNodeAt(service, startX, startY, config.direction)
            onFinished(scrolled)
        }
    }

    internal fun buildSwipePath(startX: Float, startY: Float, endX: Float, endY: Float): Path {
        val steps = 8
        return Path().apply {
            moveTo(startX, startY)
            for (step in 1..steps) {
                val t = step / steps.toFloat()
                lineTo(
                    startX + (endX - startX) * t,
                    startY + (endY - startY) * t,
                )
            }
        }
    }

    internal data class SwipeEndpoints(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
    )

    internal fun sanitizeSwipeEndpoints(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        screenWidth: Float,
        screenHeight: Float,
        minDistancePx: Float,
    ): SwipeEndpoints? {
        if (!startX.isFinite() || !startY.isFinite() || !endX.isFinite() || !endY.isFinite()) {
            return null
        }
        val maxX = screenWidth.coerceAtLeast(1f)
        val maxY = screenHeight.coerceAtLeast(1f)
        var sx = startX.coerceIn(0f, maxX)
        var sy = startY.coerceIn(0f, maxY)
        var ex = endX.coerceIn(0f, maxX)
        var ey = endY.coerceIn(0f, maxY)
        var dx = ex - sx
        var dy = ey - sy
        var distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < minDistancePx) {
            val rawDx = endX - startX
            val rawDy = endY - startY
            val rawLen = kotlin.math.hypot(rawDx.toDouble(), rawDy.toDouble()).toFloat()
            if (rawLen < 0.1f) return null
            val unitX = rawDx / rawLen
            val unitY = rawDy / rawLen
            ex = (sx + unitX * minDistancePx).coerceIn(0f, maxX)
            ey = (sy + unitY * minDistancePx).coerceIn(0f, maxY)
            dx = ex - sx
            dy = ey - sy
            distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        }
        if (distance < 1f) return null
        return SwipeEndpoints(sx, sy, ex, ey)
    }

    private fun scrollNodeAt(
        service: AccessibilityService,
        rawX: Float,
        rawY: Float,
        direction: PointerSwipeDirection,
    ): Boolean {
        val scrollAction = when (direction) {
            PointerSwipeDirection.UP -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            PointerSwipeDirection.DOWN -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            PointerSwipeDirection.LEFT,
            PointerSwipeDirection.RIGHT,
            -> return false
        }
        val root = rootForTap(service, rawX, rawY) ?: return false
        try {
            val target = findDeepestNodeAt(root, rawX, rawY) ?: return false
            try {
                var node: AccessibilityNodeInfo? = target
                while (node != null) {
                    if (node.isScrollable && node.isEnabled && node.isVisibleToUser &&
                        node.actionList.any { it.id == scrollAction }
                    ) {
                        if (node.performAction(scrollAction)) {
                            Log.i(TAG, "scrollNodeAt fallback succeeded direction=$direction")
                            return true
                        }
                    }
                    node = node.parent
                }
                return false
            } finally {
                releaseNode(target)
            }
        } finally {
            releaseNode(root)
        }
    }

    fun dispatchTapSync(service: AccessibilityService?, rawX: Float, rawY: Float): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e(TAG, "dispatchTapSync must not run on main thread (deadlock risk)")
            return false
        }
        var result = false
        val latch = CountDownLatch(1)
        dispatchTap(service, rawX, rawY, onFinished = { ok ->
            result = ok
            latch.countDown()
        })
        latch.await(GESTURE_TIMEOUT_MS + 200, TimeUnit.MILLISECONDS)
        return result
    }

    private fun dispatchGestureTap(
        service: AccessibilityService,
        rawX: Float,
        rawY: Float,
        durationMs: Long,
        onFinished: (Boolean) -> Unit,
    ) {
        val path = Path().apply {
            moveTo(rawX, rawY)
            lineTo(rawX + 2f, rawY + 2f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onFinished(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "dispatchGesture cancelled at ($rawX, $rawY)")
                    onFinished(false)
                }
            },
            null,
        )
        if (!accepted) {
            Log.w(TAG, "dispatchGesture rejected at ($rawX, $rawY)")
            onFinished(false)
        }
    }

    private fun clickNodeAt(service: AccessibilityService, rawX: Float, rawY: Float): Boolean {
        val root = rootForTap(service, rawX, rawY) ?: return false
        try {
            val target = findDeepestNodeAt(root, rawX, rawY) ?: return false
            try {
                var node: AccessibilityNodeInfo? = target
                while (node != null) {
                    if (node.isEnabled && node.isVisibleToUser &&
                        (node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                    ) {
                        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            return true
                        }
                    }
                    node = node.parent
                }
                return false
            } finally {
                releaseNode(target)
            }
        } finally {
            releaseNode(root)
        }
    }

    private fun rootForTap(service: AccessibilityService, rawX: Float, rawY: Float): AccessibilityNodeInfo? {
        val px = rawX.toInt()
        val py = rawY.toInt()
        val selfPkg = service.packageName
        var fallback: AccessibilityNodeInfo? = null
        for (window in service.windows) {
            when (window.type) {
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                -> continue
            }
            val root = window.root ?: continue
            val bounds = Rect()
            root.getBoundsInScreen(bounds)
            if (!bounds.contains(px, py)) continue
            val pkg = root.packageName?.toString()
            if (pkg == selfPkg) continue
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                return copyNode(root)
            }
            if (fallback == null) {
                fallback = copyNode(root)
            }
        }
        if (fallback != null) return fallback
        val active = service.rootInActiveWindow ?: return null
        if (active.packageName?.toString() == selfPkg) {
            releaseNode(active)
            return null
        }
        return active
    }

    private fun findDeepestNodeAt(
        root: AccessibilityNodeInfo,
        rawX: Float,
        rawY: Float,
    ): AccessibilityNodeInfo? {
        val rect = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        val x = rawX.toInt()
        val y = rawY.toInt()
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val ownedChild = node !== root
            try {
                node.getBoundsInScreen(rect)
                if (rect.contains(x, y)) {
                    val area = rect.width() * rect.height()
                    if (area < bestArea) {
                        releaseNode(best)
                        best = copyNode(node)
                        bestArea = area
                    }
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { stack.add(it) }
                    }
                }
            } finally {
                if (ownedChild) releaseNode(node)
            }
        }
        return best
    }

    private fun copyNode(source: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(source)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(source)
        }

    private fun releaseNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }

    const val TAP_DURATION_MS = 120L
    const val POINTER_TAP_DURATION_MS = 80L
    const val POINTER_TAP_CHAIN_GAP_MS = 12L
    private const val TAG = "SlideIndexA11y"
    private const val GESTURE_TIMEOUT_MS = 600L
    private const val SCROLL_GESTURE_DELAY_MS = 180L
}
