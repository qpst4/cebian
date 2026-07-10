package com.slideindex.app.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.slideindex.app.service.SlideIndexAccessibilityService

object SecureSettingsHelper {

    const val PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun adbGrantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} $PERMISSION"

    fun grantViaShizuku(context: Context): Boolean {
        if (!TaskManagerUtil.hasPermission()) return false
        val packageName = context.packageName
        val granted = TaskManagerUtil.runShellCommand("pm", "grant", packageName, PERMISSION)
        return granted && hasWriteSecureSettings(context)
    }

    /**
     * Re-enables this app's accessibility service via Secure settings when permission is granted.
     * Returns true when accessibility is enabled after the call.
     */
    fun ensureAccessibilityEnabled(context: Context): Boolean {
        if (!hasWriteSecureSettings(context)) return false
        if (PermissionHelper.isAccessibilityServiceEnabled(context)) return true

        val component = ComponentName(context, SlideIndexAccessibilityService::class.java)
        val serviceId = component.flattenToString()
        val shortId = component.flattenToShortString()

        val resolver = context.contentResolver
        val enabledServices = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val services = enabledServices.split(':')
            .filter { it.isNotBlank() }
            .toMutableSet()
        val alreadyListed = services.any {
            it.equals(serviceId, ignoreCase = true) ||
                it.equals(shortId, ignoreCase = true)
        }
        if (!alreadyListed) {
            services.add(serviceId)
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                TextUtils.join(":", services),
            )
        }
        Settings.Secure.putInt(
            resolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            1,
        )
        return PermissionHelper.isAccessibilityServiceEnabled(context)
    }
}
