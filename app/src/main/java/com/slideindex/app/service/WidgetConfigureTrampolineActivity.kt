package com.slideindex.app.service

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.widget.WidgetPopupHost

class WidgetConfigureTrampolineActivity : ComponentActivity() {

  private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

  private val configureLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {
    finish()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }

    if (savedInstanceState == null) {
      val info = WidgetPopupHost.providerInfo(this, appWidgetId)
      val configure = info?.configure
      if (configure != null) {
        val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
          component = configure
          putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        runCatching { configureLauncher.launch(configIntent) }
          .onFailure {
            Log.e("WidgetConfigure", "Failed to launch configure activity", it)
            finish()
          }
      } else {
        finish()
      }
    }
  }

  companion object {
    fun createIntent(context: Context, appWidgetId: Int): Intent =
      Intent(context, WidgetConfigureTrampolineActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
  }
}
