package com.slideindex.app.gesture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.shouldLaunchFullscreen
import com.slideindex.app.util.FreeWindowLauncher
import com.slideindex.app.util.InputTapUtil
import com.slideindex.app.util.TaskExclusions
import com.slideindex.app.util.TaskManagerUtil

class ActionExecutor(
    private val context: Context,
    private val appRepository: AppRepository,
    private val clickPassthroughHandler: ((Float, Float, () -> Unit) -> Unit)? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun execute(action: GestureAction, settings: AppSettings, longPressArmed: Boolean = false) {
        when (action) {
            GestureAction.OpenIndex, GestureAction.QuickLauncher, GestureAction.TaskSwitcher,
            GestureAction.None, GestureAction.ClickPassthrough -> Unit
            is GestureAction.LaunchApp -> launchApp(action.packageName, settings, longPressArmed)
            GestureAction.Back, GestureAction.Home, GestureAction.Recents -> {
                SlideIndexAccessibilityService.perform(action)
            }
            GestureAction.CloseCurrentApp -> closeCurrentApp()
            GestureAction.FreeWindowCurrentApp -> freeWindowForegroundApp(settings)
        }
    }

    fun launchQuickItem(item: QuickLauncherItem, settings: AppSettings, longPressArmed: Boolean = false) {
        when (item.type) {
            QuickLauncherItemType.APP -> launchApp(item.payload, settings, longPressArmed)
            QuickLauncherItemType.SHORTCUT -> launchShortcut(item.payload, settings, longPressArmed)
            QuickLauncherItemType.WIDGET -> Unit
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
            if (!switched) {
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

    private fun closeCurrentApp() {
        if (!TaskManagerUtil.hasPermission()) return
        Thread { TaskManagerUtil.removeCurrentFrontAppTask() }.start()
    }

    private fun freeWindowForegroundApp(settings: AppSettings) {
        val effectiveSettings = settings.copy(freeWindowEnabled = true)
        if (TaskManagerUtil.hasPermission()) {
            Thread {
                try {
                    val moved = TaskManagerUtil.moveFrontTaskToFreeWindow(effectiveSettings)
                    if (!moved) {
                        val packageName = resolveFreeWindowTargetPackage() ?: return@Thread
                        mainHandler.post {
                            runCatching {
                                launchFreeWindowFallback(packageName, effectiveSettings)
                            }.onFailure { error ->
                                Log.e(TAG, "launchFreeWindowFallback failed", error)
                            }
                        }
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "freeWindowForegroundApp failed", error)
                }
            }.start()
            return
        }
        runCatching {
            val packageName = resolveFreeWindowTargetPackage() ?: return
            launchFreeWindowFallback(packageName, effectiveSettings)
        }.onFailure { error ->
            Log.e(TAG, "freeWindowForegroundApp failed", error)
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

    companion object {
        private const val TAG = "ActionExecutor"
    }
}
