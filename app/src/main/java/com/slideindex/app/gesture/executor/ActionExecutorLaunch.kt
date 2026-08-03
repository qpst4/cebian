package com.slideindex.app.gesture.executor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import com.slideindex.app.activity.ActivityShortcutLauncher
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskExclusions
import com.slideindex.app.util.TaskManagerUtil

internal class ActionExecutorLaunch(
    private val context: Context,
    private val appRepository: AppRepository,
    private val mainHandler: Handler,
) {
    fun launchQuickItem(
        item: QuickLauncherItem,
        settings: AppSettings,
        longPressArmed: Boolean = false,
        anchorRawY: Float? = null,
        execute: (GestureAction, AppSettings, Boolean, Float?) -> Boolean,
    ): Boolean {
        return when (item.type) {
            QuickLauncherItemType.APP -> {
                launchApp(item.payload, settings, longPressArmed)
                false
            }
            QuickLauncherItemType.SHORTCUT ->
                launchQuickShortcut(item, settings, longPressArmed)
            QuickLauncherItemType.ACTION -> {
                QuickLauncherItemCodec.parseActionPayload(item.payload)?.let { action ->
                    execute(action, settings, longPressArmed, anchorRawY)
                }
                false
            }
            QuickLauncherItemType.WIDGET -> false
        }
    }

    fun launchGestureShortcut(
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
                launchActivityComponent(decoded.componentFlat, settings, longPressArmed)
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

    fun launchApp(packageName: String, settings: AppSettings, longPressArmed: Boolean): Boolean {
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
                    ActionExecutor.TAG,
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

    fun closeCurrentApp() {
        if (!TaskManagerUtil.hasPermission()) return
        Thread { TaskManagerUtil.removeCurrentFrontAppTask() }.start()
    }

    fun freeWindowForegroundApp(settings: AppSettings) {
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
                    Log.e(ActionExecutor.TAG, "freeWindowForegroundApp failed", error)
                }
            }.start()
        }
        mainHandler.postDelayed(runMove, 120L)
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
            ActionExecutor.TAG,
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
            Log.w(ActionExecutor.TAG, "launchComponent($component) failed", error)
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
                val fullscreen = settings.shouldLaunchFullscreen(longPressArmed)
                if (!fullscreen && settings.freeWindowEnabled) {
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
        launchActivityComponent(item.payload, settings, longPressArmed)
        return false
    }

    private fun launchActivityComponent(
        componentFlat: String,
        settings: AppSettings,
        longPressArmed: Boolean,
    ) {
        val component = componentFromRawIdentifier(componentFlat) ?: return
        ActivityShortcutLauncher.launch(
            context = context,
            packageName = component.packageName,
            activityClassName = component.className,
            settings = settings,
            longPressTriggered = longPressArmed,
        )
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
}
