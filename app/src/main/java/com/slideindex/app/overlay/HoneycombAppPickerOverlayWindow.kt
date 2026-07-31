package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper

object HoneycombAppPickerOverlayWindow {
    private const val TAG = "HoneycombOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var appContext: Context? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    private var visibleState = mutableStateOf(false)
    private var externalTrackingState = mutableStateOf(false)
    private var settingsState = mutableStateOf(AppSettings())
    private var itemsState = mutableStateOf<List<QuickLauncherItem>>(emptyList())
    private var appsState = mutableStateOf<List<AppInfo>>(emptyList())
    private var anchorXState = mutableFloatStateOf(0f)
    private var anchorYState = mutableFloatStateOf(0f)
    private var pointerXState = mutableFloatStateOf(0f)
    private var pointerYState = mutableFloatStateOf(0f)
    private var selectedIndexState = mutableIntStateOf(-1)

    private var launchHandler: ((QuickLauncherItem) -> Unit)? = null
    private var lastHapticIndex = -1

    val isShowing: Boolean get() = composeView != null

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        externalTracking: Boolean,
        onLaunch: (QuickLauncherItem) -> Unit,
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

        val items = settings.honeycombLauncher.filter {
            it.type == QuickLauncherItemType.APP || it.type == QuickLauncherItemType.SHORTCUT
        }
        if (items.isEmpty()) {
            Log.w(TAG, "show: honeycomb launcher list is empty")
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
        val apps = deps?.appRepository?.getCachedApps().orEmpty()

        if (isShowing) {
            updateRuntimeState(
                settings = settings,
                items = items,
                apps = apps,
                anchorRawX = anchorRawX,
                anchorRawY = anchorRawY,
                pointerRawX = anchorRawX,
                pointerRawY = anchorRawY,
                externalTracking = externalTracking,
                onLaunch = onLaunch,
            )
            return true
        }

        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return false
        val dialogOwner = OverlayComposeOwner()
        launchHandler = onLaunch
        updateRuntimeState(
            settings = settings,
            items = items,
            apps = apps,
            anchorRawX = anchorRawX,
            anchorRawY = anchorRawY,
            pointerRawX = anchorRawX,
            pointerRawY = anchorRawY,
            externalTracking = externalTracking,
            onLaunch = onLaunch,
        )

        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                SlideIndexTheme {
                    if (!visibleState.value) return@SlideIndexTheme
                    val appsByPackage = appsState.value.associateBy { it.packageName }
                    HoneycombPickerPanel(
                        state = HoneycombPickerState(
                            items = itemsState.value,
                            appsByPackage = appsByPackage,
                            settings = settingsState.value,
                            anchorX = anchorXState.floatValue,
                            anchorY = anchorYState.floatValue,
                            externalTracking = externalTrackingState.value,
                            pointerX = pointerXState.floatValue,
                            pointerY = pointerYState.floatValue,
                            selectedIndex = selectedIndexState.intValue,
                            onSelectionChanged = { index ->
                                if (index != selectedIndexState.intValue) {
                                    selectedIndexState.intValue = index
                                    maybeHaptic(index)
                                }
                            },
                            onLaunch = { item ->
                                launchHandler?.invoke(item)
                                dismiss()
                            },
                            onDismiss = { dismiss() },
                        ),
                    )
                }
            }
        }

        val params = buildLayoutParams(hostContext, externalTracking)
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            launchHandler = null
            return false
        }

        windowManager = wm
        composeView = view
        owner = dialogOwner
        appContext = hostContext
        registerScreenOffReceiver(hostContext)
        view.post { visibleState.value = true }
        return true
    }

    fun updatePointer(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePointer(rawX, rawY) }
            return
        }
        pointerXState.floatValue = rawX
        pointerYState.floatValue = rawY
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
        val items = itemsState.value
        val localX = rawX - anchorXState.floatValue
        val localY = rawY - anchorYState.floatValue
        val pitchPx = composeView?.resources?.displayMetrics?.density?.times(52f * 1.08f) ?: 140f
        val layoutPoints = HoneycombGeometry.compactPoints(items.size, pitchPx)
        val index = HoneycombGeometry.hitScaled(
            centers = layoutPoints,
            x = localX,
            y = localY,
            iconSize = pitchPx / 1.08f,
            effectCenterX = localX,
            effectCenterY = localY,
            effectRadius = pitchPx * 2.8f,
            centerScale = 1.12f,
            edgeScale = 0.86f,
        ).takeIf { it >= 0 } ?: selectedIndexState.intValue
        val item = items.getOrNull(index)
        dismiss()
        if (item != null) {
            composeView?.let { HapticHelper.confirmLaunch(it, settings) }
            actionExecutor.launchQuickItem(item, settings)
        }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        if (!isShowing) return
        visibleState.value = false
        cleanup()
    }

    private fun updateRuntimeState(
        settings: AppSettings,
        items: List<QuickLauncherItem>,
        apps: List<AppInfo>,
        anchorRawX: Float,
        anchorRawY: Float,
        pointerRawX: Float,
        pointerRawY: Float,
        externalTracking: Boolean,
        onLaunch: (QuickLauncherItem) -> Unit,
    ) {
        settingsState.value = settings
        itemsState.value = items
        appsState.value = apps
        anchorXState.floatValue = anchorRawX
        anchorYState.floatValue = anchorRawY
        pointerXState.floatValue = pointerRawX
        pointerYState.floatValue = pointerRawY
        externalTrackingState.value = externalTracking
        launchHandler = onLaunch
        lastHapticIndex = -1
        selectedIndexState.intValue = -1
        composeView?.let { view ->
            val params = buildLayoutParams(view.context, externalTracking)
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun maybeHaptic(index: Int) {
        if (index < 0 || index == lastHapticIndex) return
        lastHapticIndex = index
        composeView?.let { view -> HapticHelper.appTick(view, settingsState.value) }
    }

    private fun buildLayoutParams(context: Context, externalTracking: Boolean): WindowManager.LayoutParams {
        val touchableFlags = if (externalTracking) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            0
        }
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            touchableFlags
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun cleanup() {
        val view = composeView
        val wm = windowManager
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        owner?.destroy()
        owner = null
        composeView = null
        windowManager = null
        appContext = null
        screenOffReceiver = null
        launchHandler = null
        lastHapticIndex = -1
        selectedIndexState.intValue = -1
        visibleState.value = false
    }
}
