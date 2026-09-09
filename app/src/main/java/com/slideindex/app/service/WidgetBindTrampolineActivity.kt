package com.slideindex.app.service

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.widget.WidgetPopupHost

/**
 * A transparent activity that acts as a trampoline to start the system
 * ACTION_APPWIDGET_BIND and ACTION_APPWIDGET_CONFIGURE dialogs/activities.
 */
class WidgetBindTrampolineActivity : ComponentActivity() {

  private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
  private var selectedProvider: ComponentName? = null
  private var bindingInProgress = false

  private val bindLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    bindingInProgress = false
    if (result.resultCode != Activity.RESULT_OK) {
      finishCanceled()
      return@registerForActivityResult
    }
    maybeLaunchConfigure()
  }

  private val configureLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      finishSuccess()
    } else if (WidgetPopupHost.providerInfo(this, appWidgetId) != null) {
      finishSuccess()
    } else {
      finishCanceled()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    active = true
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      finishCanceled()
      return
    }
    WidgetPopupHost.startListening(this)

    appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    selectedProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(EXTRA_PROVIDER, ComponentName::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(EXTRA_PROVIDER)
    }

    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || selectedProvider == null) {
      finishCanceled()
      return
    }

    val manager = WidgetPopupHost.appWidgetManager(this)
    Log.d(TAG, "onCreate: calling bindAppWidgetIdIfAllowed for $appWidgetId")
    if (manager.bindAppWidgetIdIfAllowed(appWidgetId, selectedProvider!!)) {
      Log.d(TAG, "onCreate: bind allowed immediately")
      maybeLaunchConfigure()
    } else {
      Log.d(TAG, "onCreate: bind requires system dialog")
      bindingInProgress = true
      val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, selectedProvider)
      }
      runCatching { bindLauncher.launch(bindIntent) }
        .onFailure {
          Log.e(TAG, "bind launch failed", it)
          bindingInProgress = false
          finishCanceled()
        }
    }
  }

  override fun onDestroy() {
    active = false
    super.onDestroy()
  }

  private fun maybeLaunchConfigure() {
    Log.d(TAG, "maybeLaunchConfigure called")
    val info = WidgetPopupHost.providerInfo(this, appWidgetId)
    if (info == null) {
      Log.w(TAG, "maybeLaunchConfigure: providerInfo is null!")
      finishCanceled()
      return
    }
    val configure = info.configure
    if (configure != null) {
      val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
        component = configure
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      }
      runCatching { configureLauncher.launch(configIntent) }
        .onFailure {
          Log.e(TAG, "configure launch failed", it)
          finishSuccess()
        }
    } else {
      finishSuccess()
    }
  }

  private fun finishSuccess() {
    Log.d(TAG, "finishSuccess called")
    if (WidgetPopupHost.providerInfo(this, appWidgetId) == null) {
      Log.w(TAG, "finishSuccess: providerInfo is null!")
      finishCanceled()
      return
    }
    WidgetPickerTrampoline.deliverSuccess(appWidgetId)
    finish()
  }

  private fun finishCanceled() {
    Log.d(TAG, "finishCanceled called")
    WidgetPopupHost.deleteAppWidgetId(this, appWidgetId)
    WidgetPickerTrampoline.deliverCancel()
    finish()
  }

  companion object {
    private const val TAG = "WidgetBindTrampoline"

    @Volatile
    private var active = false

    fun isActive(): Boolean = active

    const val EXTRA_WIDGET_ID = "extra_app_widget_id"
    const val EXTRA_PROVIDER = "extra_provider"

    fun createIntent(context: Context, appWidgetId: Int, provider: ComponentName): Intent {
      return Intent(context, WidgetBindTrampolineActivity::class.java).apply {
        putExtra(EXTRA_WIDGET_ID, appWidgetId)
        putExtra(EXTRA_PROVIDER, provider)
      }
    }
  }
}
