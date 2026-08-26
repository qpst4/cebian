package com.slideindex.app.xposed

import android.util.Log
import com.slideindex.app.xposed.hook.PermissionGranterHook
import com.slideindex.app.xposed.hook.SmsHandlerHook
import com.slideindex.app.xposed.hook.SmsProviderHook
import com.slideindex.app.xposed.hook.SystemInputInjectorHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class SlideIndexLibXposedModule : XposedModule() {
  private var systemServerClassLoader: ClassLoader? = null
  private val packageReadyParams = mutableMapOf<String, PackageReadyParam>()

  override fun onModuleLoaded(param: ModuleLoadedParam) {
    XposedLog.bind(this)
    log(
      Log.INFO,
      TAG,
      "onModuleLoaded process=${param.processName} systemServer=${param.isSystemServer} " +
        "framework=$frameworkName($frameworkVersionCode) api=$apiVersion",
    )
  }

  override fun onSystemServerStarting(param: SystemServerStartingParam) {
    systemServerClassLoader = param.classLoader
    log(Log.INFO, TAG, "onSystemServerStarting")
    installSystemServerHooks(param.classLoader)
  }

  override fun onPackageLoaded(param: PackageLoadedParam) {
    if (param.packageName == PHONE_PACKAGE) {
      log(Log.INFO, TAG, "onPackageLoaded phone first=${param.isFirstPackage}")
    }
  }

  override fun onPackageReady(param: PackageReadyParam) {
    packageReadyParams[param.packageName] = param
    when (param.packageName) {
      PHONE_PACKAGE -> {
        log(Log.INFO, TAG, "onPackageReady phone")
        installPhoneHooks(param.classLoader)
      }
      TELEPHONY_PROVIDER_PACKAGE -> {
        log(Log.INFO, TAG, "onPackageReady telephony provider")
        installTelephonyProviderHooks(param.classLoader)
      }
    }
  }

  override fun onHotReloading(param: HotReloadingParam): Boolean {
    log(Log.INFO, TAG, "onHotReloading")
    return true
  }

  override fun onHotReloaded(param: HotReloadedParam) {
    super.onHotReloaded(param)
    log(Log.INFO, TAG, "onHotReloaded process=${param.processName}")
    systemServerClassLoader?.let(::installSystemServerHooks)
    packageReadyParams[PHONE_PACKAGE]?.let { installPhoneHooks(it.classLoader) }
    packageReadyParams[TELEPHONY_PROVIDER_PACKAGE]?.let { installTelephonyProviderHooks(it.classLoader) }
  }

  private fun installSystemServerHooks(classLoader: ClassLoader) {
    systemInputInjectorHook.install(this, classLoader)
    permissionGranterHook.install(this, classLoader)
  }

  private fun installPhoneHooks(classLoader: ClassLoader) {
    smsHandlerHook.install(this, classLoader)
  }

  private fun installTelephonyProviderHooks(classLoader: ClassLoader) {
    smsProviderHook.install(this, classLoader)
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
