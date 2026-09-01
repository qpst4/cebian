package com.slideindex.app.privilege

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.settings.effectiveClipboardMonitoringMode

/**
 * Whether the process should keep Shizuku binder listeners / UserService warm.
 *
 * Root privilege mode skips Shizuku unless clipboard monitoring explicitly uses a Shizuku backend.
 */
object ShizukuRequirement {
    fun needsShizuku(settings: AppSettings): Boolean {
        if (settings.privilegeMode == PrivilegeMode.SHIZUKU) return true
        if (!settings.clipboardBackgroundMonitoring) return false
        return !settings.effectiveClipboardMonitoringMode().usesRoot
    }
}
