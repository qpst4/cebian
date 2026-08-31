package com.slideindex.app.ui.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.slideindex.app.R

enum class AppIconTheme(
    val id: Int,
    val titleRes: Int,
    val descRes: Int,
    val iconRes: Int,
    val componentSimpleName: String,
) {
    VIOLET(
        id = 0,
        titleRes = R.string.app_icon_violet,
        descRes = R.string.app_icon_violet_desc,
        iconRes = R.drawable.ic_launcher_preview_violet,
        componentSimpleName = "MainActivity",
    ),
    OCEAN_BLUE(
        id = 1,
        titleRes = R.string.app_icon_blue,
        descRes = R.string.app_icon_blue_desc,
        iconRes = R.drawable.ic_launcher_preview_blue,
        componentSimpleName = "MainActivityBlue",
    ),
    CYBER_GREEN(
        id = 2,
        titleRes = R.string.app_icon_green,
        descRes = R.string.app_icon_green_desc,
        iconRes = R.drawable.ic_launcher_preview_green,
        componentSimpleName = "MainActivityGreen",
    ),
    MATERIAL_YOU(
        id = 3,
        titleRes = R.string.app_icon_monet,
        descRes = R.string.app_icon_monet_desc,
        iconRes = R.drawable.ic_launcher_preview_monet,
        componentSimpleName = "MainActivityMonet",
    );

    companion object {
        private const val PREFS_NAME = "app_icon_theme_prefs"
        private const val KEY_ICON_THEME_ID = "selected_icon_theme_id"

        fun fromId(id: Int): AppIconTheme = entries.firstOrNull { it.id == id } ?: VIOLET

        fun getSelected(context: Context): AppIconTheme {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedId = prefs.getInt(KEY_ICON_THEME_ID, VIOLET.id)
            return fromId(savedId)
        }

        fun applyIconTheme(context: Context, targetTheme: AppIconTheme) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_ICON_THEME_ID, targetTheme.id).apply()

            val pm = context.packageManager
            val pkg = context.packageName

            // 先启用目标组件，再禁用其他组件，防止所有入口同时被禁用
            val targetComponent = ComponentName(pkg, "$pkg.${targetTheme.componentSimpleName}")
            try {
                pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            } catch (_: Throwable) {
            }

            entries.filter { it != targetTheme }.forEach { theme ->
                val componentName = ComponentName(pkg, "$pkg.${theme.componentSimpleName}")
                try {
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                } catch (_: Throwable) {
                }
            }
        }

        fun ensureSelectedThemeEnabled(context: Context) {
            val selected = getSelected(context)
            val pm = context.packageManager
            val pkg = context.packageName
            val targetComponent = ComponentName(pkg, "$pkg.${selected.componentSimpleName}")
            val currentState = runCatching {
                pm.getComponentEnabledSetting(targetComponent)
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

            if (currentState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                applyIconTheme(context, selected)
            }
        }
    }
}
