package com.slideindex.app.service

import com.slideindex.app.di.AppDependencies
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
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
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.ShellCommandPanelOverlaySheet
import com.slideindex.app.ui.theme.SlideIndexTheme
import kotlinx.coroutines.flow.first

@dagger.hilt.android.AndroidEntryPoint
class ShellCommandPanelTrampolineActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var deps: AppDependencies

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

        ShellCommandPanelTrampoline.registerActivityFinisher { finishPicker() }
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        setContent {
            var commands by remember { mutableStateOf<List<ShellCommand>>(emptyList()) }
            var shizukuGranted by remember { mutableStateOf(false) }
            var themeSeedArgb by remember { mutableIntStateOf(AppSettings().themeColorArgb) }
            var dynamicColorEnabled by remember { mutableStateOf(false) }
            var themePaletteStyleId by remember { mutableIntStateOf(AppSettings().themePaletteStyleId) }
            var dismissRequest by remember { mutableStateOf<(() -> Unit)?>(null) }

            LaunchedEffect(Unit) {
                val settings = deps.settingsRepository.settings.first()
                commands = settings.shellCommands
                themeSeedArgb = settings.themeColorArgb
                dynamicColorEnabled = settings.dynamicColorEnabled
                themePaletteStyleId = settings.themePaletteStyleId
                shizukuGranted = com.slideindex.app.util.TaskManagerUtil.hasPermission()
            }

            BackHandler(enabled = dismissRequest != null) {
                dismissRequest?.invoke()
            }

            SlideIndexTheme(
                seedColor = Color(themeSeedArgb),
                dynamicColor = dynamicColorEnabled,
                paletteStyle = ThemePaletteStyle.fromId(themePaletteStyleId),
            ) {
                ShellCommandPanelOverlaySheet(
                    initialCommands = commands,
                    shizukuGranted = shizukuGranted,
                    onDismissComplete = { finishPicker() },
                    onPersistCommands = { updated ->
                        commands = updated
                        ShellCommandPanelTrampoline.onCommandsPersist(updated)
                    },
                    onWindowReady = { ShellCommandPanelTrampoline.runPrepareIfNeeded() },
                    registerBackHandler = { dismissRequest = it },
                    registerContinuousDismissHandler = { handler ->
                        ShellCommandPanelTrampoline.registerContinuousDismissRequest(handler)
                    },
                    onCopyOutput = { output -> copyOutput(output) },
                )
            }
        }
    }

    override fun onDestroy() {
        ShellCommandPanelTrampoline.unregisterActivityFinisher()
        ShellCommandPanelTrampoline.unregisterContinuousDismissRequest()
        ShellCommandPanelTrampoline.deliverDismiss()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISMISSED, dismissed)
    }

    private fun copyOutput(output: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("shell_output", output))
        Toast.makeText(this, R.string.shell_panel_copied, Toast.LENGTH_SHORT).show()
    }

    private fun finishPicker() {
        if (dismissed) return
        dismissed = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val STATE_DISMISSED = "dismissed"

        fun createIntent(context: Context): Intent =
            Intent(context, ShellCommandPanelTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
