package com.slideindex.app.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

class SlideIndexAppWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
  override fun onCreateView(
    context: Context,
    appWidgetId: Int,
    appWidget: AppWidgetProviderInfo,
  ): AppWidgetHostView {
    return RoundedAppWidgetHostView(context)
  }

  companion object {
    const val HOST_ID = 0x534944
  }
}
