package com.slideindex.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.gesture.GestureAction

class SlideIndexAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: SlideIndexAccessibilityService? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        fun perform(action: GestureAction): Boolean {
            val service = instance ?: return false
            val globalAction = when (action) {
                GestureAction.Back -> GLOBAL_ACTION_BACK
                GestureAction.Home -> GLOBAL_ACTION_HOME
                GestureAction.Recents -> GLOBAL_ACTION_RECENTS
                else -> return false
            }
            return service.performGlobalAction(globalAction)
        }

        /**
         * Inject tap at screen coordinates (async – safe to call from any thread).
         * Gesture first, then node ACTION_CLICK fallback.
         */
        fun dispatchTap(rawX: Float, rawY: Float, onFinished: (Boolean) -> Unit) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post { dispatchTap(rawX, rawY, onFinished) }
                return
            }
            val service = instance
            if (service == null) {
                Log.w(TAG, "dispatchTap($rawX, $rawY): service instance is null")
                onFinished(false)
                return
            }
            Log.i(TAG, "dispatchTap start ($rawX, $rawY)")
            dispatchGestureTap(service, rawX, rawY) { gestureOk ->
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

        /** Synchronous wrapper – only call off the main thread. */
        fun dispatchTapSync(rawX: Float, rawY: Float): Boolean {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Log.e(TAG, "dispatchTapSync must not run on main thread (deadlock risk)")
                return false
            }
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            dispatchTap(rawX, rawY) { ok ->
                result = ok
                latch.countDown()
            }
            latch.await(GESTURE_TIMEOUT_MS + 200, java.util.concurrent.TimeUnit.MILLISECONDS)
            return result
        }

        private fun dispatchGestureTap(
            service: AccessibilityService,
            rawX: Float,
            rawY: Float,
            onFinished: (Boolean) -> Unit,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Log.w(TAG, "dispatchGesture unavailable: API < 24")
                onFinished(false)
                return
            }
            val path = Path().apply {
                moveTo(rawX, rawY)
                lineTo(rawX + 2f, rawY + 2f)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
                .build()
            val accepted = service.dispatchGesture(
                gesture,
                object : GestureResultCallback() {
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
            val root = rootForTap(service, rawX, rawY) ?: run {
                Log.w(TAG, "clickNodeAt: no foreign root at ($rawX, $rawY)")
                return false
            }
            Log.d(TAG, "clickNodeAt: root pkg=${root.packageName}")
            try {
                val target = findDeepestNodeAt(root, rawX, rawY) ?: run {
                    Log.w(TAG, "clickNodeAt: no node at ($rawX, $rawY)")
                    return false
                }
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

        private fun rootForTap(
            service: AccessibilityService,
            rawX: Float,
            rawY: Float,
        ): AccessibilityNodeInfo? {
            val px = rawX.toInt()
            val py = rawY.toInt()
            val selfPkg = service.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
                        Log.d(TAG, "rootForTap: TYPE_APPLICATION pkg=$pkg layer=${window.layer}")
                        return copyNode(root)
                    }
                    if (fallback == null) {
                        fallback = copyNode(root)
                    }
                }
                if (fallback != null) return fallback
            }
            val active = service.rootInActiveWindow
            if (active == null) {
                Log.w(TAG, "rootForTap: rootInActiveWindow is null")
                return null
            }
            if (active.packageName?.toString() == selfPkg) {
                Log.w(TAG, "rootForTap: rootInActiveWindow is self pkg=$selfPkg")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+ nodes are recycled automatically when GC'd.
                return
            }
            @Suppress("DEPRECATION")
            node.recycle()
        }

        private const val TAG = "SlideIndexA11y"
        private const val TAP_DURATION_MS = 120L
        private const val GESTURE_TIMEOUT_MS = 600L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i("SlideIndexA11y", "onServiceConnected")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
