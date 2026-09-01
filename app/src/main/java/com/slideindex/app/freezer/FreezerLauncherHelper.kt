package com.slideindex.app.freezer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.slideindex.app.R

object FreezerLauncherHelper {
    private const val TAG = "FreezerLauncherHelper"
    private const val SHORTCUT_ID = "freezer_panel"
    /** 旧版 activity-alias，升级后从 manifest 移除；启动时强制禁用以免 Flyme 残留「边栏」图标 */
    private const val LEGACY_ALIAS_CLASS = "com.slideindex.app.freezer.FreezerPanelLauncher"

    /**
     * 添加桌面快捷方式（系统 Pin 对话框）。不再使用 activity-alias，避免 Flyme 把它当成可卸载的应用。
     */
    fun requestPinShortcut(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        val appContext = context.applicationContext
        val shortcutManager = appContext.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) {
            return false
        }
        val launchIntent = Intent(appContext, FreezerPanelActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val shortcut = ShortcutInfo.Builder(appContext, SHORTCUT_ID)
            .setShortLabel(appContext.getString(R.string.freezer_shortcut_label))
            .setLongLabel(appContext.getString(R.string.freezer_shortcut_label))
            .setIcon(Icon.createWithResource(appContext, R.drawable.ic_freezer_launcher))
            .setIntent(launchIntent)
            .build()
        return runCatching {
            shortcutManager.requestPinShortcut(shortcut, null)
        }.onFailure { error ->
            Log.e(TAG, "requestPinShortcut failed", error)
        }.getOrDefault(false)
    }

    fun showPinShortcutFailedToast(context: Context) {
        Toast.makeText(context, R.string.freezer_pin_shortcut_failed, Toast.LENGTH_SHORT).show()
    }

    fun showUnpinHintToast(context: Context) {
        Toast.makeText(context, R.string.freezer_pin_shortcut_unpin_hint, Toast.LENGTH_LONG).show()
    }

    /** 升级清理：禁用旧 alias（若仍存在），并尽量去掉 Flyme 遗留的 duplicate 入口 */
    fun cleanupLegacyAlias(context: Context) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val legacy = ComponentName(appContext.packageName, LEGACY_ALIAS_CLASS)
        runCatching {
            pm.setComponentEnabledSetting(
                legacy,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                legacy,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure {
            // 新版本 manifest 已删除 alias，组件不存在时忽略
        }
    }
}
