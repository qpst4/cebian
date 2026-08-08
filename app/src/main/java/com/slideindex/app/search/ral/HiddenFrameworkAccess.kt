/**
 * Based on [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) (GPL-3.0).
 */
package com.slideindex.app.search.ral

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent
import androidx.core.os.bundleOf
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

internal object HiddenFrameworkAccess {
    private const val START_SUCCESS = 0
    private const val USER_SYSTEM = 0
    private const val INPUT_INJECTION_SYNC_WAIT = 2

    fun bindActivityManager(wrappedBinder: IBinder): Any {
        val stubClass = Class.forName("android.app.IActivityManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java)
            .invoke(null, wrappedBinder)
            ?: error("IActivityManager unavailable")
    }

    fun startActivity(activityManager: Any, callingPackage: String?, intent: Intent): Int {
        val launchIntent = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val applicationThreadClass = Class.forName("android.app.IApplicationThread")
            val profilerInfoClass = Class.forName("android.app.ProfilerInfo")
            activityManager.javaClass.getMethod(
                "startActivityWithFeature",
                applicationThreadClass,
                String::class.java,
                String::class.java,
                Intent::class.java,
                String::class.java,
                IBinder::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                profilerInfoClass,
                Bundle::class.java,
            ).invoke(
                activityManager,
                null,
                callingPackage,
                null,
                launchIntent,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
            ) as Int
        } else {
            @Suppress("DEPRECATION")
            val applicationThreadClass = Class.forName("android.app.IApplicationThread")
            activityManager.javaClass.getMethod(
                "startActivity",
                applicationThreadClass,
                String::class.java,
                Intent::class.java,
                String::class.java,
                IBinder::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Bundle::class.java,
                Bundle::class.java,
            ).invoke(
                activityManager,
                null,
                callingPackage,
                launchIntent,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
            ) as Int
        }
    }

    fun requireStartSuccess(result: Int) {
        if (result != START_SUCCESS) {
            throw Exception("Error starting Activity: $result")
        }
    }

    fun bindPackageManager(wrappedBinder: IBinder): Any {
        val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java)
            .invoke(null, wrappedBinder)
            ?: error("IPackageManager unavailable")
    }

    fun grantRuntimePermission(
        packageManager: Any,
        packageName: String,
        permission: String,
        userId: Int = USER_SYSTEM,
    ) {
        packageManager.javaClass.getMethod(
            "grantRuntimePermission",
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
        ).invoke(packageManager, packageName, permission, userId)
    }

    @SuppressLint("DiscouragedPrivateApi")
    fun launchAssist(searchManager: SearchManager, extras: Bundle) {
        val method = SearchManager::class.java.getMethod("launchAssist", Bundle::class.java)
        method.invoke(searchManager, extras)
    }

    fun injectAssistKeyEvents() {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val inputBinder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, Context.INPUT_SERVICE) as IBinder
        val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
        val inputManager = stubClass.getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(inputBinder))
            ?: error("IInputManager unavailable")
        val injectMethod = inputManager.javaClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
        val downTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ASSIST, 0)
        val upEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ASSIST, 0)
        injectMethod.invoke(inputManager, downEvent, INPUT_INJECTION_SYNC_WAIT)
        injectMethod.invoke(inputManager, upEvent, INPUT_INJECTION_SYNC_WAIT)
    }

    @Suppress("UNCHECKED_CAST")
    fun getAllIntentFilters(packageManager: PackageManager, packageName: String): List<IntentFilter> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        return runCatching {
            val method = PackageManager::class.java.getMethod(
                "getAllIntentFilters",
                String::class.java,
            )
            val result = method.invoke(packageManager, packageName)
            when (result) {
                null -> emptyList()
                is List<*> -> result.filterIsInstance<IntentFilter>()
                is Array<*> -> result.filterIsInstance<IntentFilter>()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    fun wrapActivityServiceBinder(binder: IBinder): IBinder = ShizukuBinderWrapper(binder)

    fun activityServiceBinder(): IBinder =
        SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)

    fun packageServiceBinder(): IBinder =
        SystemServiceHelper.getSystemService("package")

    fun emptyAssistExtras(): Bundle = bundleOf()
}
