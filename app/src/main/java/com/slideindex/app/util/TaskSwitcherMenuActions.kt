package com.slideindex.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.R

object TaskSwitcherMenuActions {
    private const val TAG = "TaskSwitcherMenuActions"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun buildMenuItems(context: Context): List<TaskSwitcherMenuItem> {
        return listOf(
            TaskSwitcherMenuItem(
                label = context.getString(R.string.task_switcher_menu_free_window),
                type = TaskSwitcherMenuItemType.FREE_WINDOW,
            ),
            TaskSwitcherMenuItem(
                label = context.getString(R.string.task_switcher_menu_app_info),
                type = TaskSwitcherMenuItemType.APP_INFO,
            ),
            TaskSwitcherMenuItem(
                label = context.getString(R.string.task_switcher_menu_force_stop),
                type = TaskSwitcherMenuItemType.FORCE_STOP,
            ),
        )
    }

    fun execute(
        context: Context,
        item: TaskSwitcherMenuItem,
        packageName: String,
        settings: AppSettings,
        appRepository: AppRepository,
        onSessionEnd: (() -> Unit)? = null,
    ) {
        when (item.type) {
            TaskSwitcherMenuItemType.SHORTCUT -> {
                AppShortcutLoader.launchShortcut(context, packageName, item)
            }
            TaskSwitcherMenuItemType.FREE_WINDOW -> {
                launchFreeWindow(
                    context,
                    packageName,
                    settings,
                    appRepository,
                    app = null,
                    onSessionEnd = onSessionEnd,
                )
            }
            TaskSwitcherMenuItemType.APP_INFO -> {
                openAppInfo(context, packageName)
            }
            TaskSwitcherMenuItemType.FORCE_STOP -> {
                Thread {
                    TaskManagerUtil.forceStopPackage(packageName)
                }.start()
            }
        }
    }

    fun launchFreeWindow(
        context: Context,
        packageName: String,
        settings: AppSettings,
        appRepository: AppRepository,
        app: AppInfo? = null,
        onSessionEnd: (() -> Unit)? = null,
    ) {
        val effective = settings.copy(freeWindowEnabled = true)
        val target = app ?: appRepository.lookupApp(packageName)
        if (target != null) {
            appRepository.launchApp(target, effective, fullscreen = false)
            onSessionEnd?.invoke()
            return
        }
        Thread {
            val moved = runCatching {
                TaskManagerUtil.movePackageToFreeWindow(packageName, effective)
            }.getOrDefault(false)
            mainHandler.post {
                if (!moved) {
                    appRepository.lookupApp(packageName)?.let {
                        appRepository.launchApp(it, effective, fullscreen = false)
                    }
                }
                onSessionEnd?.invoke()
            }
        }.start()
    }

    private fun openAppInfo(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
        }.onFailure { error ->
            Log.e(TAG, "openAppInfo($packageName) failed", error)
        }
    }
}
