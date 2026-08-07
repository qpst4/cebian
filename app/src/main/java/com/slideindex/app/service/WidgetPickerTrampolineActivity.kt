package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.ui.WidgetPickerScreen
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.widget.WidgetProviderEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )
    super.onCreate(savedInstanceState)

    val initialSettings = runBlocking { deps.settingsRepository.settings.first() }
    var themeSeedArgb by mutableIntStateOf(initialSettings.themeColorArgb)
    var dynamicColorEnabled by mutableStateOf(initialSettings.dynamicColorEnabled)
    var themePaletteStyleId by mutableIntStateOf(initialSettings.themePaletteStyleId)

    setContent {
      SlideIndexTheme(
        seedColor = Color(themeSeedArgb),
        dynamicColor = dynamicColorEnabled,
        paletteStyle = ThemePaletteStyle.fromId(themePaletteStyleId),
      ) {
        WidgetPickerScreen(
          onBack = {
            WidgetPickerTrampoline.deliverCancel()
            finish()
          },
          onWidgetSelected = { entry -> onWidgetPicked(entry) },
          enableBackHandler = true,
        )
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
