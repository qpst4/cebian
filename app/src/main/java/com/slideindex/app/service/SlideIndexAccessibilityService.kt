package com.slideindex.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.EdgeOverlayHost
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.util.TriggerEnvironmentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SlideIndexAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var edgeOverlayHost: EdgeOverlayHost? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var prevPackageName: String? = null
    private var currPackageName: String? = null
    private var screenLockReceiverRegistered = false

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    TriggerEnvironmentState.lockScreenActive = true
                    edgeOverlayHost?.refreshTriggerVisibility()
                }
                Intent.ACTION_USER_PRESENT -> {
                    TriggerEnvironmentState.lockScreenActive = false
                    edgeOverlayHost?.refreshTriggerVisibility()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> handleWindowsChanged()
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (packageName == applicationContext.packageName) return
        edgeOverlayHost?.updateForegroundPackage(packageName)
        if (!hasLaunchIntent(packageName)) return
        if (currPackageName == packageName) return
        prevPackageName = currPackageName
        currPackageName = packageName
        if (prevPackageName == null) {
            prevPackageName = currPackageName
        }
    }

    private fun handleWindowsChanged() {
        val activePkg = rootInActiveWindow?.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: return
        if (activePkg == packageName) return
        edgeOverlayHost?.updateForegroundPackage(activePkg)
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: SlideIndexAccessibilityService? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        private var pendingScrollRunnable: Runnable? = null

        fun perform(action: GestureAction): Boolean {
            val service = instance ?: return false
            return when (action) {
                GestureAction.Back -> service.performGlobalAction(GLOBAL_ACTION_BACK)
                GestureAction.Home -> service.performGlobalAction(GLOBAL_ACTION_HOME)
                GestureAction.Recents -> service.performGlobalAction(GLOBAL_ACTION_RECENTS)
                GestureAction.OpenNotifications -> service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                GestureAction.OpenQuickSettings -> service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                GestureAction.LockScreen -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    } else {
                        false
                    }
                }
                GestureAction.Screenshot -> {
                    service.takeScreenshotDelayed()
                    true
                }
                GestureAction.PowerMenu -> service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                GestureAction.PreviousApp -> service.launchPreviousApp()
                GestureAction.KeepScreenOn -> service.toggleKeepScreenOn()
                GestureAction.ScrollToTop -> scheduleFastVerticalScroll(service, toTop = true)
                GestureAction.ScrollToBottom -> scheduleFastVerticalScroll(service, toTop = false)
                else -> false
            }
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
                return
            }
            @Suppress("DEPRECATION")
            node.recycle()
        }

        private const val TAG = "SlideIndexA11y"
        private const val TAP_DURATION_MS = 120L
        private const val GESTURE_TIMEOUT_MS = 600L
        private const val SCREENSHOT_DELAY_MS = 500L
        private const val SCROLL_GESTURE_DELAY_MS = 180L
        private const val SCROLL_BOTTOM_STROKE_COUNT = 10

        fun reloadApps() {
            instance?.edgeOverlayHost?.reloadApps()
        }

        fun setPreviewMode(
            enabled: Boolean,
            content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        ) {
            instance?.edgeOverlayHost?.setPreviewMode(enabled, content)
        }

        fun recoverOverlaysIfIdle() {
            instance?.edgeOverlayHost?.recoverOverlaysIfIdle()
        }

        /** Context for [TYPE_ACCESSIBILITY_OVERLAY] windows; null when the service is not connected. */
        fun overlayHostContext(): Context? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        edgeOverlayHost = EdgeOverlayHost(this, serviceScope).also { it.start() }
        registerScreenLockReceiver()
        Log.i(TAG, "onServiceConnected: edge overlays attached")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        edgeOverlayHost?.refreshTriggerVisibility()
    }

    override fun onDestroy() {
        unregisterScreenLockReceiver()
        edgeOverlayHost?.stop()
        edgeOverlayHost = null
        serviceScope.cancel()
        wakeLock?.release()
        wakeLock = null
        instance = null
        super.onDestroy()
    }

    private fun hasLaunchIntent(packageName: String): Boolean =
        packageManager.getLaunchIntentForPackage(packageName) != null

    private fun registerScreenLockReceiver() {
        if (screenLockReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenLockReceiver, filter)
        screenLockReceiverRegistered = true
    }

    private fun unregisterScreenLockReceiver() {
        if (!screenLockReceiverRegistered) return
        runCatching { unregisterReceiver(screenLockReceiver) }
        screenLockReceiverRegistered = false
        TriggerEnvironmentState.lockScreenActive = false
    }

    private fun launchPreviousApp(): Boolean {
        val prevPkgName = prevPackageName
        val curPkgName = currPackageName
        if (prevPkgName.isNullOrEmpty() || curPkgName.isNullOrEmpty()) return false
        if (currPackageNameError()) {
            return launchPackage(curPkgName)
        }
        if (prevPkgName == curPkgName) return false
        if (launchPackage(prevPkgName)) {
            prevPackageName = curPkgName
            currPackageName = prevPkgName
            return true
        }
        return false
    }

    private fun currPackageNameError(): Boolean {
        val activePkg = rootInActiveWindow?.packageName?.toString()
        return activePkg != currPackageName
    }

    private fun launchPackage(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun toggleKeepScreenOn(): Boolean {
        if (wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
            return true
        }
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return false
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "SlideIndex:KeepScreenOn",
        )
        return runCatching {
            wakeLock?.acquire()
            true
        }.getOrDefault(false)
    }

    private fun takeScreenshotDelayed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }, SCREENSHOT_DELAY_MS)
    }

    private fun fastVerticalScroll(toTop: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        val builder = GestureDescription.Builder()
        if (toTop) {
            val path = Path().apply {
                moveTo(centerX, centerY)
                lineTo(centerX, centerY + Int.MAX_VALUE)
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, 120))
        } else {
            val strokeCount = SCROLL_BOTTOM_STROKE_COUNT
                .coerceAtMost(GestureDescription.getMaxStrokeCount())
            repeat(strokeCount) { index ->
                val path = Path().apply {
                    moveTo(centerX, centerY)
                    lineTo(centerX, 0f)
                }
                builder.addStroke(
                    GestureDescription.StrokeDescription(path, index * 80L, 12),
                )
            }
        }
        val accepted = dispatchGesture(builder.build(), null, null)
        if (!accepted) {
            Log.w(TAG, "fastVerticalScroll(toTop=$toTop) rejected")
        }
        return accepted
    }
}
