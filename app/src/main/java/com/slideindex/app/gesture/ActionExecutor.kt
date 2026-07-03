package com.slideindex.app.gesture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.InputTapUtil
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskExclusions
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AssistantLauncher
import com.slideindex.app.util.BrightnessControlHelper
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.FlashlightHelper
import com.slideindex.app.util.OverlayBrightnessControl
import com.slideindex.app.util.SystemGestureActions
import com.slideindex.app.util.VolumeControlHelper
import android.view.KeyEvent

class ActionExecutor(
    private val context: Context,
    private val appRepository: AppRepository,
    private val clickPassthroughHandler: ((Float, Float, () -> Unit) -> Unit)? = null,
    overlayBrightness: OverlayBrightnessControl? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val continuousAdjust = ContinuousAdjustController(context, overlayBrightness)

    fun beginContinuousAdjust(mode: ContinuousAdjustController.Mode, rawY: Float): Boolean =
        continuousAdjust.begin(mode, rawY)

    fun updateContinuousAdjust(mode: ContinuousAdjustController.Mode, rawY: Float) {
        continuousAdjust.update(mode, rawY)
    }

    fun endContinuousAdjust() {
        continuousAdjust.end()
    }

    fun applyAdjustOnce(
        mode: ContinuousAdjustController.Mode,
        anchorRawY: Float,
        targetRawY: Float,
    ): Float? = continuousAdjust.applyOnce(mode, anchorRawY, targetRawY)

    fun readCurrentAdjustFraction(mode: ContinuousAdjustController.Mode): Float =
        continuousAdjust.readCurrentFraction(mode)

    fun clearBrightnessPreview() {
        continuousAdjust.clearBrightnessPreview()
    }

    fun adjustMode(): ContinuousAdjustController.Mode? = continuousAdjust.currentMode()

    fun adjustFraction(): Float = continuousAdjust.currentFraction()

    fun readRingerMode(): Int = VolumeControlHelper.readRingerMode(context)

    fun cycleRingerMode(): Int? = VolumeControlHelper.cycleRingerMode(context)

    fun readInterruptionFilter(): Int = VolumeControlHelper.readInterruptionFilter(context)

    fun toggleDnd(): Int? = VolumeControlHelper.toggleDnd(context)

    fun readAutoBrightnessEnabled(): Boolean = BrightnessControlHelper.readAutoBrightnessEnabled(context)

    fun toggleAutoBrightness(): Boolean? = BrightnessControlHelper.toggleAutoBrightness(context)

    fun readDarkModeEnabled(): Boolean = BrightnessControlHelper.readDarkModeEnabled(context)

    fun toggleDarkMode(): Boolean? = BrightnessControlHelper.toggleDarkMode(context)

    fun readVolumeFraction(stream: VolumeControlHelper.Stream): Float =
        VolumeControlHelper.readFraction(context, stream)

    fun setVolumeFraction(stream: VolumeControlHelper.Stream, fraction: Float) {
        VolumeControlHelper.setFraction(context, stream, fraction)
    }

    fun execute(action: GestureAction, settings: AppSettings, longPressArmed: Boolean = false) {
        when (action) {
            GestureAction.OpenIndex, GestureAction.QuickLauncher, GestureAction.TaskSwitcher,
            GestureAction.ShellCommandPanel,
            GestureAction.None, GestureAction.ClickPassthrough,
            GestureAction.AdjustVolume, GestureAction.AdjustBrightness -> Unit
            is GestureAction.LaunchApp -> launchApp(action.packageName, settings, longPressArmed)
            is GestureAction.LaunchShortcut -> launchGestureShortcut(action, settings, longPressArmed)
            GestureAction.Back, GestureAction.Home, GestureAction.Recents -> {
                SlideIndexAccessibilityService.perform(action)
            }
            GestureAction.CloseCurrentApp -> closeCurrentApp()
            GestureAction.FreeWindowCurrentApp -> freeWindowForegroundApp(settings)
            GestureAction.Flashlight -> FlashlightHelper.toggle(context)
            GestureAction.LaunchAssistant -> AssistantLauncher.launchDefault(context)
            GestureAction.ToggleMute -> SystemGestureActions.toggleMute(context)
            GestureAction.MediaPlayPause -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            GestureAction.MediaPrevious -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            GestureAction.MediaNext -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            GestureAction.PreviousApp,
            GestureAction.OpenNotifications,
            GestureAction.OpenQuickSettings,
            GestureAction.LockScreen,
            GestureAction.Screenshot,
            GestureAction.PowerMenu,
            GestureAction.KeepScreenOn,
            GestureAction.ScrollToTop,
            GestureAction.ScrollToBottom,
            -> SlideIndexAccessibilityService.perform(action)
        }
    }

    fun launchQuickItem(item: QuickLauncherItem, settings: AppSettings, longPressArmed: Boolean = false): Boolean {
        return when (item.type) {
            QuickLauncherItemType.APP -> {
                launchApp(item.payload, settings, longPressArmed)
                false
            }
            QuickLauncherItemType.SHORTCUT ->
                launchQuickShortcut(item, settings, longPressArmed)
            QuickLauncherItemType.ACTION -> {
                QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                    execute(action, settings, longPressArmed)
                }
                false
            }
            QuickLauncherItemType.WIDGET -> false
        }
    }

    private fun launchGestureShortcut(
        action: GestureAction.LaunchShortcut,
        settings: AppSettings,
        longPressArmed: Boolean,
    ) {
        when (val decoded = GestureShortcutPayload.decode(action.payloadKey)) {
            is GestureShortcutPayload.Decoded.Dynamic -> {
                val item = QuickLauncherItem.dynamicShortcut(
                    packageName = decoded.packageName,
                    shortcutId = decoded.shortcutId,
                    label = decoded.label.ifBlank { action.label },
                )
                launchQuickShortcut(item, settings, longPressArmed)
            }
            is GestureShortcutPayload.Decoded.Component -> {
                launchShortcut(decoded.componentFlat, settings, longPressArmed)
            }
            is GestureShortcutPayload.Decoded.IntentShortcut -> {
                launchIntentShortcut(decoded.intentUri, settings, longPressArmed)
            }
            is GestureShortcutPayload.Decoded.IntentsShortcut -> {
                launchIntentShortcuts(decoded.intentUris, settings, longPressArmed)
            }
            null -> Unit
        }
    }

    fun dispatchClickPassthrough(rawX: Float, rawY: Float, onComplete: () -> Unit = {}) {
        val handler = clickPassthroughHandler
        if (handler != null) {
            handler(rawX, rawY, onComplete)
        } else {
            InputTapUtil.dispatchTap(rawX, rawY)
            onComplete()
        }
    }

    private fun launchApp(packageName: String, settings: AppSettings, longPressArmed: Boolean): Boolean {
        val app = appRepository.getCachedApps().firstOrNull { it.packageName == packageName }
            ?: appRepository.lookupApp(packageName)
            ?: return false
        val fullscreen = settings.shouldLaunchFullscreen(longPressArmed)
        return appRepository.launchApp(app, settings, fullscreen)
    }

    fun switchToRecentTask(
        taskId: Int,
        rawIdentifier: String,
        topComponent: String,
        packageName: String,
        settings: AppSettings,
    ) {
        Thread {
            val switched = runCatching {
                TaskManagerUtil.switchToTask(
                    taskId = taskId,
                    identifier = rawIdentifier,
                    topComponent = topComponent,
                )
            }.getOrElse { error ->
                Log.e(
                    TAG,
                    "switchToRecentTask failed taskId=$taskId raw=$rawIdentifier component=$topComponent",
                    error,
                )
                false
            }
            if (switched) {
                RecentTasksLoader.requestRefreshAfterSwitch(appRepository)
            } else {
                mainHandler.post {
                    launchRecentTaskFallback(topComponent, rawIdentifier, packageName, settings)
                }
            }
        }.start()
    }

    private fun launchRecentTaskFallback(
        topComponent: String,
        rawIdentifier: String,
        packageName: String,
        settings: AppSettings,
    ) {
        if (topComponent.isNotBlank() && launchComponent(topComponent, settings)) return
        if (launchRawIdentifier(rawIdentifier, packageName, settings)) return
        if (shouldLaunchPackageFallback(rawIdentifier, packageName) &&
            launchApp(packageName, settings, longPressArmed = false)
        ) {
            return
        }
        Log.w(
            TAG,
            "recents switch failed raw=$rawIdentifier package=$packageName component=$topComponent",
        )
    }

    private fun launchRawIdentifier(
        rawIdentifier: String,
        packageName: String,
        settings: AppSettings,
    ): Boolean {
        val raw = rawIdentifier.trim()
        if (raw.isBlank()) return false
        if (raw.contains('/') && launchComponent(raw, settings)) return true
        if (raw != packageName && raw.startsWith("$packageName.")) {
            return launchComponent(ComponentName(packageName, raw), settings)
        }
        return false
    }

    private fun shouldLaunchPackageFallback(rawIdentifier: String, packageName: String): Boolean {
        val raw = rawIdentifier.trim()
        return raw.isBlank() || raw == packageName
    }

    private fun launchComponent(componentRaw: String, settings: AppSettings): Boolean {
        val component = componentFromRawIdentifier(componentRaw) ?: return false
        return launchComponent(component, settings)
    }

    private fun launchComponent(component: ComponentName, settings: AppSettings): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setComponent(component)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                )
            }
            if (settings.freeWindowEnabled) {
                FreeWindowLauncher.launch(context, intent, settings, fullscreen = false)
            } else {
                context.startActivity(intent)
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "launchComponent($component) failed", error)
            false
        }
    }

    private fun componentFromRawIdentifier(rawIdentifier: String): ComponentName? {
        val trimmed = rawIdentifier.trim()
        if (!trimmed.contains('/')) return null
        val pkg = trimmed.substringBefore('/').trim()
        var cls = trimmed.substringAfter('/').trim()
        if (cls.startsWith('.')) cls = pkg + cls
        if (pkg.isEmpty() || cls.isEmpty()) return null
        return ComponentName(pkg, cls)
    }

    private fun launchQuickShortcut(
        item: QuickLauncherItem,
        settings: AppSettings,
        longPressArmed: Boolean,
    ): Boolean {
        QuickLauncherItemCodec.parseIntentPayload(item.payload)?.let { intentUri ->
            launchIntentShortcut(intentUri, settings, longPressArmed)
            return false
        }
        QuickLauncherItemCodec.parseIntentListPayload(item.payload)?.let { intentUris ->
            launchIntentShortcuts(intentUris, settings, longPressArmed)
            return false
        }
        val dynamic = QuickLauncherItemCodec.parseShortcutPayload(item.payload)
        if (dynamic != null) {
            val (packageName, shortcutId) = dynamic
            val launchResolved: (TaskSwitcherMenuItem) -> Unit = { menuItem ->
                if (longPressArmed && settings.freeWindowEnabled) {
                    launchShortcutInFreeWindow(packageName, menuItem, settings)
                } else {
                    AppShortcutLoader.launchShortcut(context, packageName, menuItem)
                }
            }
            val cached = AppShortcutLoader.peekResolvedShortcut(packageName, shortcutId)
            if (cached != null) {
                launchResolved(cached)
                return false
            }
            Thread {
                val resolved = AppShortcutLoader.resolveShortcutForLaunch(
                    context = context,
                    packageName = packageName,
                    shortcutId = shortcutId,
                    label = item.label,
                )
                mainHandler.post { launchResolved(resolved) }
            }.start()
            return true
        }
        launchShortcut(item.payload, settings, longPressArmed)
        return false
    }

    private fun launchShortcutInFreeWindow(
        packageName: String,
        item: TaskSwitcherMenuItem,
        settings: AppSettings,
    ) {
        val intent = item.shortcutIntent
        if (intent != null) {
            FreeWindowLauncher.launch(context, intent, settings, fullscreen = false)
            return
        }
        AppShortcutLoader.launchShortcut(context, packageName, item)
    }

    private fun launchShortcut(componentFlat: String, settings: AppSettings, longPressArmed: Boolean) {
        val component = ComponentName.unflattenFromString(componentFlat) ?: return
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setComponent(component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fullscreen = settings.shouldLaunchFullscreen(longPressArmed)
        if (fullscreen || !settings.freeWindowEnabled) {
            context.startActivity(intent)
        } else {
            FreeWindowLauncher.launch(context, intent, settings, fullscreen = false)
        }
    }

    private fun launchIntentShortcut(
        intentUri: String,
        settings: AppSettings,
        longPressArmed: Boolean,
    ) {
        launchIntentShortcuts(listOf(intentUri), settings, longPressArmed)
    }

    private fun launchIntentShortcuts(
        intentUris: List<String>,
        settings: AppSettings,
        longPressArmed: Boolean,
    ) {
        val intents = intentUris.mapNotNull { uri ->
            runCatching {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            }.getOrNull()?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intents.isEmpty()) return
        val fullscreen = settings.shouldLaunchFullscreen(longPressArmed)
        if (intents.size > 1) {
            runCatching { context.startActivities(intents.toTypedArray()) }
            return
        }
        val intent = intents[0]
        if (fullscreen || !settings.freeWindowEnabled) {
            runCatching {
                context.startActivity(
                    com.slideindex.app.service.LaunchTrampolineActivity.createIntent(context, intent),
                )
            }.onFailure {
                context.startActivity(intent)
            }
        } else {
            FreeWindowLauncher.launch(context, intent, settings, fullscreen = false)
        }
    }

    private fun closeCurrentApp() {
        if (!TaskManagerUtil.hasPermission()) return
        Thread { TaskManagerUtil.removeCurrentFrontAppTask() }.start()
    }

    private fun freeWindowForegroundApp(settings: AppSettings) {
        val effectiveSettings = settings.copy(freeWindowEnabled = true)
        val targetPackage = resolveFreeWindowTargetPackage()
        val runMove = Runnable {
            Thread {
                try {
                    if (!TaskManagerUtil.hasPermission()) {
                        if (targetPackage != null) {
                            mainHandler.post {
                                launchFreeWindowFallback(targetPackage, effectiveSettings)
                            }
                        }
                        return@Thread
                    }
                    TaskManagerUtil.ensureServiceBound()
                    var moved = false
                    if (targetPackage != null) {
                        moved = TaskManagerUtil.movePackageToFreeWindow(targetPackage, effectiveSettings)
                    }
                    if (!moved) {
                        moved = TaskManagerUtil.moveFrontTaskToFreeWindow(effectiveSettings)
                    }
                    if (!moved && targetPackage != null) {
                        mainHandler.post {
                            launchFreeWindowFallback(targetPackage, effectiveSettings)
                        }
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "freeWindowForegroundApp failed", error)
                }
            }.start()
        }
        // Let the overlay session finish so task queries target the foreground app.
        mainHandler.postDelayed(runMove, 120L)
    }

    private fun resolveFreeWindowTargetPackage(): String? {
        val selfPackage = context.packageName
        return listOfNotNull(
            OverlayService.gestureForegroundPackage,
            OverlayService.foregroundPackage,
        ).firstOrNull { !TaskExclusions.shouldSkipFreeWindow(it, selfPackage) }
    }

    private fun launchFreeWindowFallback(packageName: String, settings: AppSettings) {
        val app = appRepository.getCachedApps().firstOrNull { it.packageName == packageName }
            ?: appRepository.lookupApp(packageName)
            ?: return
        appRepository.launchApp(app, settings, fullscreen = false)
    }

    companion object {
        private const val TAG = "ActionExecutor"
    }
}
