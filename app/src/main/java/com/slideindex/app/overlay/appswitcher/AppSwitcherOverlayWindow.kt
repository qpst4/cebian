package com.slideindex.app.overlay.appswitcher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.FloatBallLayout
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.overlay.OverlayDisplayMetrics
import com.slideindex.app.overlay.HoneycombIconLoader
import com.slideindex.app.overlay.HoneycombTargetResolver
import com.slideindex.app.overlay.layout.FvAppSwitcherSide
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FvAppSwitcherSettings
import com.slideindex.app.service.CreateShortcutTrampoline
import com.slideindex.app.ui.appswitcher.AppSwitcherSlotConfigSheet
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
object AppSwitcherOverlayWindow {
    private const val TAG = "AppSwitcherOverlay"
    /** FV CircleAppContainer 贴边 inset：n5.q.a(20) */
    private const val FV_EDGE_INSET_DP = 20f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var controller: AppSwitcherOverlayController? = null
    private var slotConfigDialogHost: OverlayComposeDialogHost? = null
    private var lastSettings: AppSettings = AppSettings()
    private var appContext: Context? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var externalTracking = false
    private var persistAfterPin = false

    val isShowing: Boolean get() = controller?.isVisible() == true

    fun openSlotPicker(slotIndex: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { openSlotPicker(slotIndex) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: return
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext) ?: return
        val settings = lastSettings
        val fvSettings = settings.fvAppSwitcher
        val currentItem = fvSettings.itemAt(slotIndex)
        val appDeps = deps as? com.slideindex.app.di.AppDependencies
            ?: dagger.hilt.android.EntryPointAccessors.fromApplication(
                hostContext.applicationContext,
                com.slideindex.app.di.AppGraphEntryPoint::class.java,
            ).dependencies()
        val apps = deps.appRepository.getCachedApps()

        val dialogHost = slotConfigDialogHost ?: OverlayComposeDialogHost(
            context = hostContext,
            themeSettings = { lastSettings },
        ).also { slotConfigDialogHost = it }

        dialogHost.show(
            onBackPressed = {
                dialogHost.dismiss()
                true
            },
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.slideindex.app.ui.compose.LocalAppDependencies provides appDeps,
            ) {
                AppSwitcherSlotConfigSheet(
                    slotIndex = slotIndex,
                    currentItem = currentItem,
                    apps = apps,
                    activityShortcuts = settings.activityShortcuts,
                    shellCommands = settings.shellCommands,
                    onDismiss = { dialogHost.dismiss() },
                    onSelectItem = { selectedItem ->
                        settingsScope.launch {
                            val itemToSet = selectedItem ?: QuickLauncherItem(QuickLauncherItemType.APP, "", "")
                            deps.settingsRepository.setFvAppSwitcherSlot(slotIndex, itemToSet)
                            refreshFromSettings()
                        }
                    },
                    launchCreateShortcut = { createHost, onResult ->
                        CreateShortcutTrampoline.launch(
                            context = hostContext,
                            host = createHost,
                            onPrepare = { AppSwitcherOverlayWindow.dismiss() },
                            onResult = onResult,
                        )
                    },
                )
            }
        }
    }

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        externalTracking: Boolean,
        onLaunch: (QuickLauncherItem, Boolean) -> Unit,
    ): Boolean {
        lastSettings = settings
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

        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }

        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return false
            }
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext)
        val appRepository = deps?.appRepository
        val apps = appRepository?.getCachedApps().orEmpty()
        val appsByPackage = apps.associateBy { it.packageName }
        val fvSettings = settings.fvAppSwitcher
        val targets = resolveTargets(hostContext, fvSettings, appsByPackage, appRepository, settings)

        val wm = hostContext.getSystemService(android.view.WindowManager::class.java)
        val (density, screenWidth) = FloatBallOverlay.overlayLayoutMetrics(settings)
            ?: run {
                val layoutMetrics = OverlayDisplayMetrics.resolve(hostContext, wm, null)
                layoutMetrics.density to OverlayDisplayMetrics.screenWidthPx(hostContext, wm, layoutMetrics)
            }
        val side = if (anchorRawX > screenWidth / 2f) {
            FvAppSwitcherSide.RIGHT
        } else {
            FvAppSwitcherSide.LEFT
        }
        val edgeInsetPx = FV_EDGE_INSET_DP * density
        val anchorX = when (side) {
            FvAppSwitcherSide.LEFT -> edgeInsetPx
            FvAppSwitcherSide.RIGHT -> screenWidth - edgeInsetPx
        }

        val overlayController = controller ?: AppSwitcherOverlayController(overlayContext, mainHandler).also {
            controller = it
        }
        this.externalTracking = externalTracking
        persistAfterPin = false

        val launchCallback = onLaunch
        val resolvedItems = targets.filterNotNull().map { it.item }
        if (appRepository != null && resolvedItems.isNotEmpty()) {
            val iconSizePx = (FvCircleLayoutEngine.ICON_SIZE_DP * density).toInt()
            settingsScope.launch {
                HoneycombIconLoader.warmAppIcons(appRepository, resolvedItems, iconSizePx)
            }
            com.slideindex.app.tasks.TaskSwitcherRepository.refreshAsync(appRepository) { recentEntries ->
                if (recentEntries.isNotEmpty()) {
                    refreshFromSettings()
                }
            }
        }

        FloatBallOverlay.hideChromeForAppSwitcher()

        val shown = overlayController.show(
            settings = settings,
            fvSettings = fvSettings,
            targets = targets,
            appsByPackage = appsByPackage,
            side = side,
            anchorX = anchorX,
            anchorY = anchorRawY,
            externalTracking = externalTracking,
            layoutDensity = density,
            screenWidth = screenWidth,
            listener = object : AppSwitcherOverlayController.Listener {
                override fun onLaunch(target: com.slideindex.app.overlay.HoneycombRuntimeTarget, longPressArmed: Boolean) {
                    unregisterScreenOffReceiver()
                    releaseOverlayState()
                    launchCallback(target.item, longPressArmed)
                }

                override fun onClosed() {
                    if (persistAfterPin && overlayController.isVisible()) return
                    unregisterScreenOffReceiver()
                    releaseOverlayState()
                }

                override fun onCircleCountChange(circleCount: Int) {
                    val repository = deps?.settingsRepository ?: return
                    settingsScope.launch {
                        repository.setFvAppSwitcherCircleCount(circleCount)
                        val refreshedSettings = repository.settings.first()
                        val refreshedFv = refreshedSettings.fvAppSwitcher
                        val refreshedTargets = resolveTargets(
                            hostContext,
                            refreshedFv,
                            appsByPackage,
                            appRepository,
                            refreshedSettings,
                        )
                        mainHandler.post {
                            if (controller === overlayController && overlayController.isVisible()) {
                                overlayController.refreshTargets(refreshedFv, refreshedTargets, appsByPackage)
                            }
                        }
                    }
                }
            },
        )
        if (!shown) {
            FloatBallOverlay.restoreChromeAfterAppSwitcher()
            return false
        }

        appContext = overlayContext
        registerScreenOffReceiver(overlayContext)
        if (externalTracking) {
            overlayController.externalMove(anchorRawX, anchorRawY)
        }
        if (appRepository != null && targets.any { it?.icon == null }) {
            HoneycombIconLoader.loadMissingIconsAsync(
                context = hostContext,
                targets = targets.filterNotNull(),
                appsByPackage = appsByPackage,
                appRepository = appRepository,
                activityShortcuts = settings.activityShortcuts,
                shellCommands = settings.shellCommands,
                onIconsReady = {
                    if (controller === overlayController && overlayController.isVisible()) {
                        val refreshedTargets = resolveTargets(
                            hostContext,
                            fvSettings,
                            appsByPackage,
                            appRepository,
                            settings,
                        )
                        overlayController.refreshTargets(fvSettings, refreshedTargets, appsByPackage)
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
        val wasExternalTracking = externalTracking
        controller?.externalUp(rawX, rawY, cancelled = false)
        if (wasExternalTracking) {
            externalTracking = false
            persistAfterPin = true
        }
    }

    fun refreshFromSettings() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshFromSettings() }
            return
        }
        val overlayController = controller ?: return
        if (!overlayController.isVisible()) return
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: return
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext) ?: return
        val appRepository = deps.appRepository
        val apps = appRepository.getCachedApps()
        val appsByPackage = apps.associateBy { it.packageName }
        settingsScope.launch {
            val settings = deps.settingsRepository.settings.first()
            val fvSettings = settings.fvAppSwitcher
            val targets = resolveTargets(
                hostContext,
                fvSettings,
                appsByPackage,
                appRepository,
                settings,
            )
            mainHandler.post {
                if (controller === overlayController && overlayController.isVisible()) {
                    overlayController.refreshTargets(fvSettings, targets, appsByPackage)
                }
            }
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
        slotConfigDialogHost?.dismiss()
        slotConfigDialogHost = null
        controller?.removeNow()
        unregisterScreenOffReceiver()
        releaseOverlayState()
    }

    private fun resolveTargets(
        context: Context,
        fvSettings: FvAppSwitcherSettings,
        appsByPackage: Map<String, AppInfo>,
        appRepository: com.slideindex.app.data.AppRepository?,
        settings: AppSettings,
    ): List<com.slideindex.app.overlay.HoneycombRuntimeTarget?> {
        val slotCount = fvSettings.slotCount()
        val explicitSlots = mutableMapOf<Int, com.slideindex.app.overlay.HoneycombRuntimeTarget>()
        val usedPackages = mutableSetOf<String>()

        for (i in 0 until slotCount) {
            val item = fvSettings.itemAt(i)?.takeIf { it.payload.isNotBlank() }
            if (item != null) {
                val target = HoneycombTargetResolver.resolve(
                    context,
                    listOf(item),
                    appsByPackage,
                    appRepository,
                    settings.activityShortcuts,
                    settings.shellCommands,
                ).firstOrNull()
                if (target != null) {
                    explicitSlots[i] = target
                    if (item.type == QuickLauncherItemType.APP) {
                        usedPackages.add(item.payload)
                    }
                }
            }
        }

        val autoFillQueue = ArrayDeque<AppInfo>()
        if (appRepository != null) {
            val cachedRecents = com.slideindex.app.tasks.TaskSwitcherRepository.getCachedEntries().map { it.app }
            for (app in cachedRecents) {
                val pkg = app.packageName
                if (pkg.isNotBlank() &&
                    pkg in appsByPackage &&
                    pkg !in usedPackages &&
                    !isSystemInternalPackage(pkg)
                ) {
                    autoFillQueue.add(app)
                    usedPackages.add(pkg)
                }
            }
        }
        for (app in appsByPackage.values) {
            val pkg = app.packageName
            if (pkg.isNotBlank() &&
                pkg !in usedPackages &&
                !isSystemInternalPackage(pkg)
            ) {
                autoFillQueue.add(app)
                usedPackages.add(pkg)
            }
        }

        return List(slotCount) { index ->
            explicitSlots[index] ?: run {
                val autoApp = autoFillQueue.removeFirstOrNull() ?: return@run null
                val item = QuickLauncherItem(
                    type = QuickLauncherItemType.APP,
                    payload = autoApp.packageName,
                    label = autoApp.label,
                )
                HoneycombTargetResolver.resolve(
                    context,
                    listOf(item),
                    appsByPackage,
                    appRepository,
                    settings.activityShortcuts,
                    settings.shellCommands,
                ).firstOrNull()
            }
        }
    }

    private fun isSystemInternalPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        val lower = pkg.lowercase()
        return lower == "android" ||
            lower.startsWith("com.android.internal") ||
            lower.contains("intentresolver") ||
            lower == "com.android.settings.intelligence" ||
            lower == "com.android.permissioncontroller"
    }

    private fun releaseOverlayState() {
        FloatBallOverlay.restoreChromeAfterAppSwitcher()
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
