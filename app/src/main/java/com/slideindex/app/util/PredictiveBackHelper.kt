package com.slideindex.app.util

import android.content.pm.ApplicationInfo
import android.os.Build

object PredictiveBackHelper {
    fun applyEnabled(appInfo: ApplicationInfo, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            val method = ApplicationInfo::class.java.getDeclaredMethod(
                "setEnableOnBackInvokedCallback",
                Boolean::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(appInfo, enabled)
        }
    }
}
