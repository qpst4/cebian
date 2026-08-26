package com.slideindex.app.xposed.hook

import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.hookMethod
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.util.Properties

/**
 * Hide apps from recent tasks list.
 * Portions inspired by hideRecent (https://github.com/Young-Lord/hideRecent) GPL-3.0.
 */
class RecentTasksHideHook {
    private val hideTaskPackages = mutableSetOf<String>()
    private val hidePreviewPackages = mutableSetOf<String>()

    fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
        reloadConfig()
        val handles = mutableListOf<XposedInterface.HookHandle>()
        handles += hookVisibleRecentTask(xposed, classLoader)
        handles += hookSnapshotMode(xposed, classLoader)
        XposedLog.i(TAG, "RecentTasksHideHook installed task=${hideTaskPackages.size} preview=${hidePreviewPackages.size}")
        return handles
    }

    fun reloadConfig() {
        hideTaskPackages.clear()
        hidePreviewPackages.clear()
        val file = File(CONFIG_PATH)
        if (!file.exists()) return
        runCatching {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            hideTaskPackages += props.getProperty("hide_task", "").split(',').filter { it.isNotBlank() }
            hidePreviewPackages += props.getProperty("hide_preview", "").split(',').filter { it.isNotBlank() }
        }.onFailure {
            XposedLog.w(TAG, "reloadConfig failed: ${it.message}")
        }
    }

    private fun hookVisibleRecentTask(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
        val taskClass = LibXposedReflect.findClassIfExists("com.android.server.wm.Task", classLoader) ?: return emptyList()
        val hook = object : LibXposedMethodHook() {
            override fun beforeHookedMethod(param: HookParam) {
                reloadConfig()
                val task = param.args.firstOrNull() ?: return
                if (shouldHideTask(task)) param.result = false
            }
        }
        val handles = mutableListOf<XposedInterface.HookHandle>()
        LibXposedReflect.findMethodExactIfExists(
            LibXposedReflect.findClass("com.android.server.wm.RecentTasks", classLoader),
            "isVisibleRecentTask",
            taskClass,
        )?.let { method ->
            handles += xposed.hookMethod(method, hook, id = "hide_recent_visible")
        }
        LibXposedReflect.findMethodExactIfExists(
            LibXposedReflect.findClass("com.android.server.wm.RecentTasks", classLoader),
            "isVisibleRecentTask",
            taskClass,
            Boolean::class.javaPrimitiveType,
        )?.let { method ->
            handles += xposed.hookMethod(method, hook, id = "hide_recent_visible_vivo")
        }
        return handles
    }

    private fun hookSnapshotMode(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
        val taskClass = LibXposedReflect.findClassIfExists("com.android.server.wm.Task", classLoader) ?: return emptyList()
        val hook = object : LibXposedMethodHook() {
            override fun beforeHookedMethod(param: HookParam) {
                reloadConfig()
                val task = param.args.firstOrNull() ?: return
                if (shouldHidePreview(task)) param.result = SNAPSHOT_MODE_APP_THEME
            }
        }
        val shouldUseThemeHook = object : LibXposedMethodHook() {
            override fun beforeHookedMethod(param: HookParam) {
                reloadConfig()
                val record = param.thisObject ?: return
                if (shouldHidePreview(record)) param.result = true
            }
        }
        val handles = mutableListOf<XposedInterface.HookHandle>()
        listOf("com.android.server.wm.TaskSnapshotController", "com.android.server.wm.AbsAppSnapshotController")
            .forEach { className ->
                LibXposedReflect.findClassIfExists(className, classLoader)?.let { clazz ->
                    LibXposedReflect.findMethodExactIfExists(clazz, "getSnapshotMode", taskClass)?.let { method ->
                        handles += xposed.hookMethod(method, hook, id = "hide_preview_mode_$className")
                    }
                }
            }
        LibXposedReflect.findClassIfExists("com.android.server.wm.ActivityRecord", classLoader)?.let { clazz ->
            LibXposedReflect.findMethodExactIfExists(clazz, "shouldUseAppThemeSnapshot")?.let { method ->
                handles += xposed.hookMethod(method, shouldUseThemeHook, id = "hide_preview_theme")
            }
        }
        return handles
    }

    private fun shouldHideTask(task: Any): Boolean {
        val pkg = extractPackageName(task) ?: return false
        return pkg in hideTaskPackages
    }

    private fun shouldHidePreview(task: Any): Boolean {
        val pkg = extractPackageName(task) ?: return false
        return pkg in hidePreviewPackages || pkg in hideTaskPackages
    }

    private fun extractPackageName(task: Any): String? = runCatching {
        val baseIntent = LibXposedReflect.callMethod(task, "getBaseIntent") as? android.content.Intent
        baseIntent?.component?.packageName
            ?: baseIntent?.`package`
            ?: LibXposedReflect.getObjectField(task, "mCallingPackage") as? String
    }.getOrNull()

    companion object {
        private const val TAG = "RecentTasksHide"
        private const val SNAPSHOT_MODE_APP_THEME = 1
        private const val CONFIG_PATH = "/data/data/com.slideindex.app/files/xposed/hide_recent.properties"
    }
}
