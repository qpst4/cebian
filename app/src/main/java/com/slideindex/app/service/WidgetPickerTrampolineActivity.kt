package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.WidgetPickerScreen
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.widget.WidgetProviderEntry

/**
 * Fallback picker when [com.slideindex.app.overlay.WidgetPickerOverlayWindow] cannot attach
 * (e.g. settings screen while accessibility host is unavailable).
 */
@dagger.hilt.android.AndroidEntryPoint
class WidgetPickerTrampolineActivity : ComponentActivity() {

  @javax.inject.Inject lateinit var deps: AppDependencies

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    )
    super.onCreate(savedInstanceState)

    var appSettings by mutableStateOf(deps.settingsRepository.readSnapshot())

    setContent {
      // WidgetPickerScreen 内部通过 rememberSettingsRepository/rememberAppRepository 取依赖，
      // 缺这层 provider 会在组合期直接抛 IllegalStateException（选器 Activity 一进就闪退）。
      CompositionLocalProvider(LocalAppDependencies provides deps) {
        ModuleTheme(settings = appSettings) {
          WidgetPickerScreen(
            onBack = {
              WidgetPickerTrampoline.deliverCancel()
              finish()
            },
            onWidgetSelected = { entry -> onWidgetPicked(entry) },
            onAppSelected = { app ->
              WidgetPickerTrampoline.deliverAppSuccess(app.packageName, app.className, app.appLabel)
              finish()
            },
            onShortcutSelected = { sc ->
              WidgetPickerTrampoline.deliverShortcutSuccess(sc.packageName, sc.shortcutId, sc.label, sc.intentUri)
              finish()
            },
            enableBackHandler = true
          )
        }
      }
    }
  }

  private fun onWidgetPicked(entry: WidgetProviderEntry) {
    WidgetPickerTrampoline.startBindFlow(this, entry.provider.provider)
    finish()
  }

  companion object {
    fun createIntent(context: android.content.Context) =
      android.content.Intent(context, WidgetPickerTrampolineActivity::class.java)
  }
}
