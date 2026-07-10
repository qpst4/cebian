package com.slideindex.app.xposed.hook

import android.Manifest
import android.os.Build
import android.util.Log
import com.slideindex.app.xposed.XposedLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class PermissionGranterHook {
  fun install(classLoader: ClassLoader) {
    runCatching { hookGrantPermissions(classLoader) }
      .onFailure { XposedLog.e(TAG, "PermissionGranterHook failed", it) }
  }

  private fun hookGrantPermissions(classLoader: ClassLoader) {
    val serviceClass = XposedHelpers.findClass(PERMISSION_MANAGER_SERVICE, classLoader)
    val androidPackageClass = XposedHelpers.findClass(ANDROID_PACKAGE_CLASS, classLoader)
    val callbackClass = XposedHelpers.findClassIfExists(PERMISSION_CALLBACK_CLASS, classLoader)
    val method = XposedHelpers.findMethodExactIfExists(
      serviceClass,
      "restorePermissionState",
      androidPackageClass,
      Boolean::class.javaPrimitiveType,
      String::class.java,
      callbackClass,
    ) ?: return
    XposedBridge.hookMethod(method, object : XC_MethodHook() {
      override fun afterHookedMethod(param: MethodHookParam) {
        val pkg = param.args[0] ?: return
        val packageName = XposedHelpers.callMethod(pkg, "getPackageName") as? String ?: return
        val permissions = PACKAGE_PERMISSIONS[packageName] ?: return
        val permissionManagerService = param.thisObject
        val packageManagerInt = XposedHelpers.getObjectField(permissionManagerService, "mPackageManagerInt")
        val packageSetting = XposedHelpers.callMethod(packageManagerInt, "getPackageSetting", packageName) ?: return
        val permissionsState = XposedHelpers.callMethod(packageSetting, "getPermissionsState")
        val requestedPermissions = XposedHelpers.callMethod(pkg, "getRequestedPermissions") as? List<*>
          ?: return
        val settings = XposedHelpers.getObjectField(permissionManagerService, "mSettings")
        val allPermissions = XposedHelpers.getObjectField(settings, "mPermissions")
        for (permission in permissions) {
          if (requestedPermissions.contains(permission)) continue
          val granted = XposedHelpers.callMethod(permissionsState, "hasInstallPermission", permission) as Boolean
          if (!granted) {
            val basePermission = XposedHelpers.callMethod(allPermissions, "get", permission) ?: continue
            XposedHelpers.callMethod(permissionsState, "grantInstallPermission", basePermission)
            Log.i(TAG, "Granted $permission to $packageName")
          }
        }
      }
    })
    Log.i(TAG, "PermissionGranterHook installed for API ${Build.VERSION.SDK_INT}")
  }

  companion object {
    private const val TAG = "PermissionGranter"
    private const val PERMISSION_MANAGER_SERVICE = "com.android.server.pm.permission.PermissionManagerService"
    private const val ANDROID_PACKAGE_CLASS = "com.android.server.pm.parsing.pkg.AndroidPackage"
    private const val PERMISSION_CALLBACK_CLASS =
      "com.android.server.pm.permission.PermissionManagerServiceInternal\$PermissionCallback"
    private const val PHONE_PACKAGE = "com.android.phone"

    private val PACKAGE_PERMISSIONS = mapOf(
      PHONE_PACKAGE to listOf(
        "android.permission.INJECT_EVENTS",
        Manifest.permission.KILL_BACKGROUND_PROCESSES,
        Manifest.permission.READ_SMS,
      ),
    )
  }
}
