package com.slideindex.app.xposed

import android.util.Log
import com.slideindex.app.xposed.hook.PermissionGranterHook
import com.slideindex.app.xposed.hook.SmsHandlerHook
import com.slideindex.app.xposed.hook.SmsProviderHook
import com.slideindex.app.xposed.hook.SystemInputInjectorHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class SlideIndexLibXposedModule : XposedModule() {
  override fun onModuleLoaded(param: ModuleLoadedParam) {
    log(
      Log.INFO,
      TAG,
      "onModuleLoaded process=${param.processName} systemServer=${param.isSystemServer} " +
        "framework=$frameworkName($frameworkVersionCode) api=$apiVersion",
    )
  }

  override fun onSystemServerStarting(param: SystemServerStartingParam) {
    log(Log.INFO, TAG, "onSystemServerStarting")
    systemInputInjectorHook.install(param.classLoader)
    permissionGranterHook.install(param.classLoader)
  }

  override fun onPackageLoaded(param: PackageLoadedParam) {
    if (param.packageName == PHONE_PACKAGE) {
      log(Log.INFO, TAG, "onPackageLoaded phone first=${param.isFirstPackage}")
    }
  }

  override fun onPackageReady(param: PackageReadyParam) {
    when (param.packageName) {
      PHONE_PACKAGE -> {
        log(Log.INFO, TAG, "onPackageReady phone")
        smsHandlerHook.install(param.classLoader)
      }
      TELEPHONY_PROVIDER_PACKAGE -> {
        log(Log.INFO, TAG, "onPackageReady telephony provider")
        smsProviderHook.install(param.classLoader)
      }
    }
  }

  companion object {
    private const val TAG = "SlideIndexXposed"
    private const val PHONE_PACKAGE = "com.android.phone"
    private const val TELEPHONY_PROVIDER_PACKAGE = "com.android.providers.telephony"
    private val smsHandlerHook = SmsHandlerHook()
    private val smsProviderHook = SmsProviderHook()
    private val systemInputInjectorHook = SystemInputInjectorHook()
    private val permissionGranterHook = PermissionGranterHook()
  }
}
