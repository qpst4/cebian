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
import android.util.Pair
import android.view.View
import android.widget.RemoteViews
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
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
              val view = args[0] as? View
              val pendingIntent = args[1] as? PendingIntent ?: return false
              val response = if (args.size > 2) args[2] else null

              if (pendingIntent.isActivity) {
                WidgetPopupOverlayWindow.dismiss()
              }

              try {
                val getLaunchOptionsMethod = response?.javaClass?.getMethod("getLaunchOptions", View::class.java)
                val launchOptions = getLaunchOptionsMethod?.invoke(response, view)
                val startPendingIntentMethod = RemoteViews::class.java.getMethod(
                  "startPendingIntent",
                  View::class.java,
                  PendingIntent::class.java,
                  Pair::class.java
                )
                val result = startPendingIntentMethod.invoke(null, view, pendingIntent, launchOptions)
                val success = (result as? Boolean) ?: true
                Log.d(TAG, "API 31+ RemoteViews.startPendingIntent success=$success")
                return success
              } catch (e: Throwable) {
                Log.w(TAG, "API 31+ fallback to pendingIntent.send: ${e.message}")
                return runCatching {
                  val options = ActivityOptions.makeBasic()
                  @Suppress("DEPRECATION")
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    options.setPendingIntentBackgroundActivityStartMode(
                      ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                  }
                  pendingIntent.send(appContext, 0, null, null, null, null, options.toBundle())
                  true
                }.getOrDefault(false)
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
