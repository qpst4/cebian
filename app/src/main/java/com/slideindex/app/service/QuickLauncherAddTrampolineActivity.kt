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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.ui.QuickLauncherAddOverlaySheet
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.util.AppShortcutLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QuickLauncherAddTrampolineActivity : ComponentActivity() {

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
        val panelSide = intent.getStringExtra(EXTRA_PANEL_SIDE)
            ?.let { runCatching { PanelSide.valueOf(it) }.getOrNull() }
            ?: PanelSide.LEFT
        val configuredAppPackages =
            intent.getStringArrayExtra(EXTRA_CONFIGURED_APPS)?.toSet().orEmpty()
        val configuredShortcutKeys =
            intent.getStringArrayExtra(EXTRA_CONFIGURED_SHORTCUT_KEYS)?.toSet().orEmpty()
        val configuredActionKeys =
            intent.getStringArrayExtra(EXTRA_CONFIGURED_ACTION_KEYS)?.toSet().orEmpty()

        val app = application as SlideIndexApp
        setContent {
            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var themeSeedArgb by remember { mutableIntStateOf(AppSettings().themeColorArgb) }
            var dynamicColorEnabled by remember { mutableStateOf(false) }
            var dismissRequest by remember { mutableStateOf<(() -> Unit)?>(null) }
            LaunchedEffect(Unit) {
                launch { apps = app.appRepository.loadApps() }
                launch {
                    app.settingsRepository.settings.collect { settings ->
                        themeSeedArgb = settings.themeColorArgb
                        dynamicColorEnabled = settings.dynamicColorEnabled
                    }
                }
            }
            BackHandler(enabled = dismissRequest != null) {
                dismissRequest?.invoke()
            }
            SlideIndexTheme(
                seedColor = Color(themeSeedArgb),
                dynamicColor = dynamicColorEnabled,
            ) {
                QuickLauncherAddOverlaySheet(
                    panelSide = panelSide,
                    apps = apps,
                    configuredAppPackages = configuredAppPackages,
                    configuredShortcutKeys = configuredShortcutKeys,
                    configuredActionKeys = configuredActionKeys,
                    onDismiss = {},
                    onDismissComplete = { finishPicker() },
                    registerBackHandler = { dismissRequest = it },
                    onAdd = { QuickLauncherAddTrampoline.onItemAdd(it) },
                    onRemove = { QuickLauncherAddTrampoline.onItemRemove(it) },
                    launchCreateShortcut = { host, onResult ->
                        CreateShortcutTrampoline.launch(
                            context = this@QuickLauncherAddTrampolineActivity,
                            host = host,
                            onPrepare = {},
                            onResult = onResult,
                        )
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISMISSED, dismissed)
    }

    override fun onDestroy() {
        if (!dismissed) {
            QuickLauncherAddTrampoline.deliverDismiss()
        }
        super.onDestroy()
    }

    private fun finishPicker() {
        if (dismissed) return
        dismissed = true
        QuickLauncherAddTrampoline.deliverDismiss()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_PANEL_SIDE = "panel_side"
        private const val EXTRA_CONFIGURED_APPS = "configured_apps"
        private const val EXTRA_CONFIGURED_SHORTCUT_KEYS = "configured_shortcut_keys"
        private const val EXTRA_CONFIGURED_ACTION_KEYS = "configured_action_keys"
        private const val STATE_DISMISSED = "dismissed"

        fun createIntent(
            context: Context,
            panelSide: PanelSide,
            configuredAppPackages: Set<String>,
            configuredShortcutKeys: Set<String>,
            configuredActionKeys: Set<String>,
        ): Intent =
            Intent(context, QuickLauncherAddTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PANEL_SIDE, panelSide.name)
                putExtra(EXTRA_CONFIGURED_APPS, configuredAppPackages.toTypedArray())
                putExtra(EXTRA_CONFIGURED_SHORTCUT_KEYS, configuredShortcutKeys.toTypedArray())
                putExtra(EXTRA_CONFIGURED_ACTION_KEYS, configuredActionKeys.toTypedArray())
            }
    }
}
