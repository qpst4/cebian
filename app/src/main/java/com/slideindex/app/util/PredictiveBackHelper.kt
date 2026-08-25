package com.slideindex.app.util

import android.content.pm.ApplicationInfo
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

object PredictiveBackHelper {
    private const val SET_ENABLE_ON_BACK_INVOKED_CALLBACK =
        "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"

    fun applyEnabled(appInfo: ApplicationInfo, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(SET_ENABLE_ON_BACK_INVOKED_CALLBACK)
            val method = ApplicationInfo::class.java.getDeclaredMethod(
                "setEnableOnBackInvokedCallback",
                Boolean::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(appInfo, enabled)
        }
    }
}
