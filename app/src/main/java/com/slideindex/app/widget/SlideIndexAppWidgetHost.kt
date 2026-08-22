package com.slideindex.app.widget

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class SlideIndexAppWidgetHost(private val appContext: Context) : AppWidgetHost(appContext, HOST_ID) {

  init {
    injectInteractionHandlerIfSupported()
  }

  override fun onCreateView(
    context: Context,
    appWidgetId: Int,
    appWidget: AppWidgetProviderInfo?,
  ): AppWidgetHostView {
    return RoundedAppWidgetHostView(context)
  }

  @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
  private fun injectInteractionHandlerIfSupported() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
      val handlerClass = Class.forName("android.widget.RemoteViews\$InteractionHandler")
      val proxy = Proxy.newProxyInstance(
        handlerClass.classLoader,
        arrayOf(handlerClass),
        object : InvocationHandler {
          override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            if (method.name == "onInteraction" && args != null && args.size >= 2) {
              val pendingIntent = args[1] as? PendingIntent
              if (pendingIntent != null) {
                runCatching {
                  val options = ActivityOptions.makeBasic()
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    options.setPendingIntentBackgroundActivityStartMode(
                      ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                  }
                  pendingIntent.send(appContext, 0, null, null, null, null, options.toBundle())
                  return true
                }.onFailure {
                  Log.e(TAG, "Failed to launch pendingIntent via proxy", it)
                }
              }
            }
            return false
          }
        }
      )
      val setMethod = AppWidgetHost::class.java.getDeclaredMethod(
        "setInteractionHandler",
        handlerClass
      )
      setMethod.isAccessible = true
      setMethod.invoke(this, proxy)
      Log.d(TAG, "API 31+ InteractionHandler injected successfully")
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to inject InteractionHandler: ${e.message}")
    }
  }

  companion object {
    private const val TAG = "SlideIndexAppWidgetHost"
    const val HOST_ID = 0x534944
  }
}
