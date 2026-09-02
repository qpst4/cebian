package com.slideindex.app.overlay.holographic

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.data.AppRepository
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.overlay.ScreenOffDismissReceiver
import com.slideindex.app.util.PermissionHelper

@SuppressLint("StaticFieldLeak")
object HolographicLauncherOverlayWindow {
    private const val TAG = "HolographicLauncher"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var controller: HolographicLauncherOverlayController? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
    private var appContext: Context? = null

    val isShowing: Boolean get() = controller?.isVisible == true

    fun show(
        context: Context,
        settings: AppSettings,
        actionExecutor: ActionExecutor,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(context, settings, actionExecutor)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
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
            ?: run {
                Log.w(TAG, "show: app repository unavailable")
                return false
            }

        val apps = resolveApps(hostContext, settings, appRepository)
        if (apps.isEmpty()) {
            Log.w(TAG, "show: no launchable apps")
            return false
        }

        val overlayController = controller ?: HolographicLauncherOverlayController(hostContext, mainHandler).also {
            controller = it
        }
        val holographicSettings = settings.holographicLauncher
        val shown = overlayController.show(
            apps = apps,
            settings = holographicSettings,
            listener = object : HolographicLauncherOverlayView.Listener {
                override fun onLaunch(app: HolographicLauncherApp) {
                    screenOffDismissReceiver.unregister()
                    overlayController.removeNow()
                    releaseOverlayState()
                    actionExecutor.execute(
                        GestureAction.LaunchApp(app.packageName),
                        settings,
                    )
                }

                override fun onClosed() {
                    screenOffDismissReceiver.unregister()
                    overlayController.removeNow()
                    releaseOverlayState()
                }
            },
        )
        if (!shown) return false

        appContext = hostContext
        screenOffDismissReceiver.register(hostContext)
        return true
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        controller?.removeNow()
        screenOffDismissReceiver.unregister()
        releaseOverlayState()
    }

    private fun resolveApps(
        context: Context,
        settings: AppSettings,
        appRepository: AppRepository,
    ): List<HolographicLauncherApp> {
        val hidden = settings.holographicLauncher.hiddenAppPackages
        val selfPackage = context.packageName
        return appRepository.getCachedApps()
            .filter { it.packageName != selfPackage && it.packageName !in hidden }
            .map { info ->
                HolographicLauncherApp(
                    packageName = info.packageName,
                    label = info.label,
                    icon = appRepository.launchIconDrawable(info.packageName),
                )
            }
    }

    private fun releaseOverlayState() {
        controller = null
        appContext = null
    }
}
