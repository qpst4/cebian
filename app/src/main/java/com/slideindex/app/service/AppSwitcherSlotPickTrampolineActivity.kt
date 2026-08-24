package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.QuickLauncherAddOverlaySheet
import com.slideindex.app.overlay.appswitcher.AppSwitcherOverlayWindow
import com.slideindex.app.service.CreateShortcutTrampoline
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppSwitcherSlotPickTrampolineActivity : ComponentActivity() {

    @Inject lateinit var deps: AppDependencies

    private var dismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(STATE_DISMISSED, false) == true) {
            finish()
            return
        }

        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
        if (slotIndex < 0) {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        setContent {
            CompositionLocalProvider(LocalAppDependencies provides deps) {
                val scope = rememberCoroutineScope()
                var appSettings by remember { mutableStateOf(AppSettings()) }
                var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

                LaunchedEffect(Unit) {
                    appSettings = deps.settingsRepository.settings.first()
                    apps = deps.appRepository.loadApps(force = false)
                }

                BackHandler { finishPicker() }

                ModuleTheme(settings = appSettings) {
                    QuickLauncherAddOverlaySheet(
                        panelSide = PanelSide.LEFT,
                        apps = apps,
                        configuredAppPackages = emptySet(),
                        configuredShortcutKeys = emptySet(),
                        configuredActionKeys = emptySet(),
                        activityShortcuts = appSettings.activityShortcuts,
                        shellCommands = appSettings.shellCommands,
                        onDismiss = { finishPicker() },
                        launchCreateShortcut = { host, onResult ->
                            CreateShortcutTrampoline.launch(
                                context = this@AppSwitcherSlotPickTrampolineActivity,
                                host = host,
                                onPrepare = { finishPicker() },
                                onResult = { created ->
                                    created?.let { shortcut ->
                                        scope.launch {
                                            deps.settingsRepository.setFvAppSwitcherSlot(
                                                AppSwitcherOverlayWindow.currentAxis(),
                                                slotIndex,
                                                shortcut.toQuickLauncherItem(),
                                            )
                                            AppSwitcherOverlayWindow.refreshFromSettings()
                                        }
                                    }
                                    onResult(created)
                                },
                            )
                        },
                        onAdd = { item ->
                            scope.launch {
                                deps.settingsRepository.setFvAppSwitcherSlot(
                                    AppSwitcherOverlayWindow.currentAxis(),
                                    slotIndex,
                                    item,
                                )
                                AppSwitcherOverlayWindow.refreshFromSettings()
                                finishPicker()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun finishPicker() {
        if (dismissed) return
        dismissed = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISMISSED, dismissed)
    }

    companion object {
        const val SIDE_LEFT = "left"
        const val SIDE_RIGHT = "right"

        private const val EXTRA_SIDE = "side"
        private const val EXTRA_SLOT_INDEX = "slot_index"
        private const val STATE_DISMISSED = "dismissed"

        fun createIntent(context: Context, side: String, slotIndex: Int): Intent =
            Intent(context, AppSwitcherSlotPickTrampolineActivity::class.java).apply {
                putExtra(EXTRA_SIDE, side)
                putExtra(EXTRA_SLOT_INDEX, slotIndex)
            }
    }
}
