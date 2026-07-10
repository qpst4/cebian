package com.slideindex.app.xposed.hook

import android.Manifest
import android.os.Build
import android.util.Log
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.hookMethod
import io.github.libxposed.api.XposedInterface

class PermissionGranterHook {
  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
    return runCatching { hookGrantPermissions(xposed, classLoader) }
      .getOrElse {
        XposedLog.e(TAG, "PermissionGranterHook failed", it)
        emptyList()
      }
  }

  private fun hookGrantPermissions(
    xposed: XposedInterface,
    classLoader: ClassLoader,
  ): List<XposedInterface.HookHandle> {
    val serviceClass = LibXposedReflect.findClass(PERMISSION_MANAGER_SERVICE, classLoader)
    val androidPackageClass = LibXposedReflect.findClass(ANDROID_PACKAGE_CLASS, classLoader)
    val callbackClass = LibXposedReflect.findClassIfExists(PERMISSION_CALLBACK_CLASS, classLoader)
    val method = LibXposedReflect.findMethodExactIfExists(
      serviceClass,
      "restorePermissionState",
      androidPackageClass,
      Boolean::class.javaPrimitiveType,
      String::class.java,
      callbackClass,
    ) ?: return emptyList()
    val handle = xposed.hookMethod(
      method,
      object : LibXposedMethodHook() {
        override fun afterHookedMethod(param: HookParam) {
          val pkg = param.args[0] ?: return
          val packageName = LibXposedReflect.callMethod(pkg, "getPackageName") as? String ?: return
          val permissions = PACKAGE_PERMISSIONS[packageName] ?: return
          val permissionManagerService = param.thisObject ?: return
          val packageManagerInt = LibXposedReflect.getObjectField(permissionManagerService, "mPackageManagerInt")
          val packageSetting = LibXposedReflect.callMethod(packageManagerInt, "getPackageSetting", packageName)
            ?: return
          val permissionsState = LibXposedReflect.callMethod(packageSetting, "getPermissionsState")
          val requestedPermissions = LibXposedReflect.callMethod(pkg, "getRequestedPermissions") as? List<*>
            ?: return
          val settings = LibXposedReflect.getObjectField(permissionManagerService, "mSettings") ?: return
          val allPermissions = LibXposedReflect.getObjectField(settings, "mPermissions")
          for (permission in permissions) {
            if (requestedPermissions.contains(permission)) continue
            val granted = LibXposedReflect.callMethod(
              permissionsState,
              "hasInstallPermission",
              permission,
            ) as Boolean
            if (!granted) {
              val basePermission = LibXposedReflect.callMethod(allPermissions, "get", permission) ?: continue
              LibXposedReflect.callMethod(permissionsState, "grantInstallPermission", basePermission)
              Log.i(TAG, "Granted $permission to $packageName")
            }
          }
        }
      },
      id = "permission_granter_restore",
    )
    Log.i(TAG, "PermissionGranterHook installed for API ${Build.VERSION.SDK_INT}")
    return listOf(handle)
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
