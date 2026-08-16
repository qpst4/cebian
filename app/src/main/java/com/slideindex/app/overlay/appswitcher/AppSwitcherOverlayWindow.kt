package com.slideindex.app.overlay.appswitcher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.HoneycombIconLoader
import com.slideindex.app.overlay.HoneycombTargetResolver
import com.slideindex.app.overlay.layout.AppSwitcherSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.resolveHoneycombLongPressArmed
import com.slideindex.app.util.PermissionHelper

@SuppressLint("StaticFieldLeak")
object AppSwitcherOverlayWindow {
    private const val TAG = "AppSwitcherOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var controller: AppSwitcherOverlayController? = null
    private var appContext: Context? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var externalTracking = false
    private var persistAfterPin = false

    val isShowing: Boolean get() = controller?.isVisible() == true

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        externalTracking: Boolean,
        onLaunch: (QuickLauncherItem, Boolean) -> Unit,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(context, settings, anchorRawX, anchorRawY, externalTracking, onLaunch)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }

        val items = settings.appSwitcherItems.appSwitcherRuntimeItems()
        if (items.isEmpty()) {
            Log.w(TAG, "show: app switcher list is empty")
            return false
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }

        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return false
            }
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext)
        val appRepository = deps?.appRepository
        val apps = appRepository?.getCachedApps().orEmpty()
        val appsByPackage = apps.associateBy { it.packageName }
        val targets = HoneycombTargetResolver.resolve(
            hostContext,
            items,
            appsByPackage,
            appRepository,
            settings.activityShortcuts,
            settings.shellCommands,
        )
        if (targets.isEmpty()) {
            Log.w(TAG, "show: no resolvable app switcher targets")
            return false
        }

        val screenWidth = hostContext.resources.displayMetrics.widthPixels
        val side = if (anchorRawX < screenWidth * 0.5f) {
            AppSwitcherSide.LEFT
        } else {
            AppSwitcherSide.RIGHT
        }

        val overlayController = controller ?: AppSwitcherOverlayController(hostContext, mainHandler).also {
            controller = it
        }
        this.externalTracking = externalTracking
        persistAfterPin = false

        val launchCallback = onLaunch
        if (appRepository != null) {
            val iconSizePx = (settings.appSwitcherDisplay.iconSizeDp * hostContext.resources.displayMetrics.density).toInt()
            HoneycombIconLoader.warmAppIcons(appRepository, items, iconSizePx)
        }

        val shown = overlayController.show(
            settings = settings,
            targets = targets,
            appsByPackage = appsByPackage,
            side = side,
            anchorRawY = anchorRawY,
            externalTracking = externalTracking,
            listener = object : AppSwitcherOverlayController.Listener {
                override fun onLaunch(target: com.slideindex.app.overlay.HoneycombRuntimeTarget, selectionPressDurationMs: Long) {
                    val longPressArmed = settings.resolveHoneycombLongPressArmed(
                        selectionPressDurationMs.coerceAtLeast(0L),
                    )
                    unregisterScreenOffReceiver()
                    releaseOverlayState()
                    launchCallback(target.item, longPressArmed)
                }

                override fun onClosed() {
                    if (persistAfterPin && overlayController.isVisible()) return
                    unregisterScreenOffReceiver()
                    releaseOverlayState()
                }
            },
        )
        if (!shown) return false

        appContext = hostContext
        registerScreenOffReceiver(hostContext)
        if (externalTracking) {
            overlayController.externalMove(anchorRawX, anchorRawY)
        }
        if (appRepository != null && targets.any { it.icon == null }) {
            HoneycombIconLoader.loadMissingIconsAsync(
                context = hostContext,
                targets = targets,
                appsByPackage = appsByPackage,
                appRepository = appRepository,
                activityShortcuts = settings.activityShortcuts,
                shellCommands = settings.shellCommands,
                onIconsReady = {
                    if (controller === overlayController && overlayController.isVisible()) {
                        val refreshed = HoneycombTargetResolver.resolve(
                            hostContext,
                            items,
                            appsByPackage,
                            appRepository,
                            settings.activityShortcuts,
                            settings.shellCommands,
                        )
                        overlayController.refreshTargets(refreshed, appsByPackage)
                    }
                },
            )
        }
        return true
    }

    fun updatePointer(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePointer(rawX, rawY) }
            return
        }
        controller?.externalMove(rawX, rawY)
    }

    fun confirmSelection(
        rawX: Float,
        rawY: Float,
        actionExecutor: ActionExecutor,
        settings: AppSettings,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { confirmSelection(rawX, rawY, actionExecutor, settings) }
            return
        }
        updatePointer(rawX, rawY)
        controller?.externalUp(rawX, rawY, cancelled = false)
        if (settings.appSwitcherDisplay.pinOnRelease && externalTracking) {
            externalTracking = false
            persistAfterPin = true
            controller?.enableDirectTouch()
        }
    }

    fun onGestureSessionEnd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onGestureSessionEnd() }
            return
        }
        if (persistAfterPin && controller?.isVisible() == true) return
        dismiss()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        controller?.removeNow()
        unregisterScreenOffReceiver()
        releaseOverlayState()
    }

    private fun releaseOverlayState() {
        controller = null
        appContext = null
        externalTracking = false
        persistAfterPin = false
    }

    private fun registerScreenOffReceiver(context: Context) {
        unregisterScreenOffReceiver()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        screenOffReceiver = null
    }
}
