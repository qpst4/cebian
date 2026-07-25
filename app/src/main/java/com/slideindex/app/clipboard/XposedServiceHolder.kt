package com.slideindex.app.clipboard

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

object XposedServiceHolder {
  @Volatile
  private var service: XposedService? = null

  private val listeners = CopyOnWriteArrayList<(XposedService?) -> Unit>()

  fun init(application: Application) {
    XposedServiceHelper.registerListener(
      object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(service: XposedService) {
          XposedServiceHolder.service = service
          listeners.forEach { it(service) }
        }

        override fun onServiceDied(service: XposedService) {
          XposedServiceHolder.service = null
          listeners.forEach { it(null) }
        }
      },
    )
  }

  fun currentService(): XposedService? = service

  fun addListener(listener: (XposedService?) -> Unit) {
    listeners += listener
    service?.let(listener)
  }

  fun removeListener(listener: (XposedService?) -> Unit) {
    listeners -= listener
  }
}
