package com.slideindex.app.otp

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import com.slideindex.app.util.TaskManagerUtil

object OtpAccessibilitySettingsHelper {
    private const val LEGACY_OTP_SERVICE =
        "com.slideindex.app/com.slideindex.app.service.OtpAutoInputAccessibilityService"

    fun isAccessibilityReady(context: Context): Boolean =
        PermissionHelper.isAccessibilityServiceEnabled(context)

    fun ensureAccessibilityEnabled(context: Context): Boolean {
        migrateLegacyDedicatedServiceIfNeeded(context)
        if (isAccessibilityReady(context)) return true
        if (SecureSettingsHelper.ensureAccessibilityEnabled(context)) return true
        return toggleMainAccessibilityViaRoot(context, enable = true)
    }

    fun migrateLegacyDedicatedServiceIfNeeded(context: Context) {
        val resolver = context.contentResolver
        val enabledServices = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        if (enabledServices.isBlank()) return
        val services = enabledServices.split(':').filter { it.isNotBlank() }.toMutableList()
        val hadLegacy = services.removeAll {
            it.equals(LEGACY_OTP_SERVICE, ignoreCase = true) ||
                it.endsWith(".OtpAutoInputAccessibilityService", ignoreCase = true)
        }
        if (!hadLegacy) return
        val mainComponent = ComponentName(context, SlideIndexAccessibilityService::class.java)
        val mainId = mainComponent.flattenToString()
        val mainShortId = mainComponent.flattenToShortString()
        val hasMain = services.any {
            it.equals(mainId, ignoreCase = true) || it.equals(mainShortId, ignoreCase = true)
        }
        if (!hasMain) {
            services.add(mainId)
        }
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            TextUtils.join(":", services),
        )
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }

    private fun toggleMainAccessibilityViaRoot(context: Context, enable: Boolean): Boolean {
        if (!TaskManagerUtil.probeRootAvailable()) return false
        val component = ComponentName(context, SlideIndexAccessibilityService::class.java)
        val serviceId = component.flattenToString()
        return try {
            val command = if (enable) {
                buildEnableCommand(context, serviceId)
            } else {
                buildDisableCommand(context, serviceId)
            }
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()
            exitCode == 0 && isAccessibilityReady(context) == enable
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildEnableCommand(context: Context, serviceId: String): String {
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val services = current.split(':').filter { it.isNotBlank() }.toMutableSet()
        services.add(serviceId)
        val joined = TextUtils.join(":", services)
        return "settings put secure accessibility_enabled 1 && settings put secure enabled_accessibility_services '$joined'"
    }

    private fun buildDisableCommand(context: Context, serviceId: String): String {
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val services = current.split(':')
            .filter { it.isNotBlank() && !it.equals(serviceId, ignoreCase = true) }
        val joined = TextUtils.join(":", services)
        return if (services.isEmpty()) {
            "settings put secure accessibility_enabled 0 && settings put secure enabled_accessibility_services ''"
        } else {
            "settings put secure enabled_accessibility_services '$joined'"
        }
    }
}
