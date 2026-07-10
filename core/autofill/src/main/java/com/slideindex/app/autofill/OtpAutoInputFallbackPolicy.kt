package com.slideindex.app.autofill

object OtpAutoInputFallbackPolicy {
    private val launcherPackages = setOf(
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "com.bbk.launcher2",
        "com.meizu.flyme.launcher",
    )

    fun shouldRetryAccessibility(reason: String): Boolean =
        reason in RETRY_REASONS

    private val RETRY_REASONS = setOf(
        "no_active_window",
        "no_editable_node",
        "timeout",
        "none",
    )

    fun shouldFallbackToKeyboard(reason: String, windowPackage: String): Boolean {
        if (reason != "no_editable_node") return false
        val pkg = windowPackage.lowercase()
        if (pkg.isBlank() || pkg == "<unknown>") return true
        if (launcherPackages.any { pkg.contains(it) }) return false
        if (pkg.contains("systemui")) return false
        if (pkg.contains("launcher")) return false
        return true
    }
}
