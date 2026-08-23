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
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface CornerSlotPickerPage {
    data object Main : CornerSlotPickerPage
    data object MyShortcuts : CornerSlotPickerPage
    data object PresetShortcuts : CornerSlotPickerPage
    data object PickApp : CornerSlotPickerPage
    data class PickActivity(val packageName: String) : CornerSlotPickerPage
    data class ShellCommand(val initialCommand: String) : CornerSlotPickerPage
}

@AndroidEntryPoint
class CornerGestureSlotPickTrampolineActivity : ComponentActivity() {

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

        val corner = intent.getStringExtra(EXTRA_CORNER).orEmpty()
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
        if (slotIndex !in 0 until 15) {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        setContent {
            val scope = rememberCoroutineScope()
            var appSettings by remember { mutableStateOf(AppSettings()) }
            var currentAction by remember { mutableStateOf<GestureAction>(GestureAction.None) }
            var page by remember { mutableStateOf<CornerSlotPickerPage>(CornerSlotPickerPage.Main) }

            LaunchedEffect(corner, slotIndex) {
                val overlay = deps.settingsRepository.overlaySettings.first()
                val cornerSettings = overlay.cornerGestureSettings
                currentAction = when (corner) {
                    CORNER_RIGHT -> {
                        if (cornerSettings.unifiedSlots) {
                            cornerSettings.leftSlots
                        } else {
                            cornerSettings.rightSlots
                        }.getOrElse(slotIndex) { GestureAction.None }
                    }
                    else -> cornerSettings.leftSlots.getOrElse(slotIndex) { GestureAction.None }
                }
                appSettings = deps.settingsRepository.settings.first()
            }

            val saveCornerAction: (GestureAction) -> Unit = { action ->
                scope.launch {
                    val overlay = deps.settingsRepository.overlaySettings.first()
                    val unified = overlay.cornerGestureSettings.unifiedSlots
                    when {
                        unified -> deps.settingsRepository.setCornerGestureLeftSlotAction(slotIndex, action)
                        corner == CORNER_RIGHT ->
                            deps.settingsRepository.setCornerGestureRightSlotAction(slotIndex, action)
                        else ->
                            deps.settingsRepository.setCornerGestureLeftSlotAction(slotIndex, action)
                    }
                    finishPicker()
                }
            }

            BackHandler {
                if (page == CornerSlotPickerPage.Main) {
                    finishPicker()
                } else {
                    page = CornerSlotPickerPage.Main
                }
            }

            CompositionLocalProvider(LocalAppDependencies provides deps) {
                ModuleTheme(settings = appSettings) {
                    when (val screen = page) {
                        CornerSlotPickerPage.Main -> {
                            GestureActionPickerScreen(
                                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                                current = currentAction,
                                onDismiss = { finishPicker() },
                                onSelect = { action ->
                                    if (action is GestureAction.FloatingPointer) {
                                        return@GestureActionPickerScreen
                                    }
                                    saveCornerAction(action)
                                },
                                onOpenMyShortcuts = { page = CornerSlotPickerPage.MyShortcuts },
                                onOpenPresetShortcuts = { page = CornerSlotPickerPage.PresetShortcuts },
                                onOpenPickApp = { page = CornerSlotPickerPage.PickApp },
                                onOpenExecuteShellCommand = { command ->
                                    page = CornerSlotPickerPage.ShellCommand(command)
                                },
                            )
                        }

                        CornerSlotPickerPage.MyShortcuts -> {
                            MyShortcutsFolderScreen(
                                activityShortcuts = appSettings.activityShortcuts,
                                onBack = { page = CornerSlotPickerPage.Main },
                                onBrowseNewShortcut = { page = CornerSlotPickerPage.PickApp },
                                currentAction = currentAction,
                                onSelectRadio = saveCornerAction,
                            )
                        }

                        CornerSlotPickerPage.PresetShortcuts -> {
                            PresetShortcutsFolderScreen(
                                onBack = { page = CornerSlotPickerPage.Main },
                                currentAction = currentAction,
                                onSelectRadio = saveCornerAction,
                            )
                        }

                        CornerSlotPickerPage.PickApp -> {
                            ActivityShortcutPickAppScreen(
                                onBack = { page = CornerSlotPickerPage.Main },
                                onSelectApp = { app ->
                                    page = CornerSlotPickerPage.PickActivity(app.packageName)
                                },
                            )
                        }

                        is CornerSlotPickerPage.PickActivity -> {
                            ActivityShortcutPickActivityScreen(
                                packageName = screen.packageName,
                                onBack = { page = CornerSlotPickerPage.PickApp },
                                onSelectActivity = { activity ->
                                    saveCornerAction(
                                        GestureAction.LaunchShortcut.component(
                                            "${activity.packageName}/${activity.className}",
                                            activity.label,
                                        ),
                                    )
                                },
                            )
                        }

                        is CornerSlotPickerPage.ShellCommand -> {
                            GestureExecuteShellCommandScreen(
                                initialCommand = screen.initialCommand,
                                shellCommands = appSettings.shellCommands,
                                onBack = { page = CornerSlotPickerPage.Main },
                                onConfirm = { command ->
                                    saveCornerAction(GestureAction.ExecuteShellCommand(command))
                                },
                            )
                        }
                    }
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
        const val CORNER_LEFT = "left"
        const val CORNER_RIGHT = "right"

        private const val EXTRA_CORNER = "corner"
        private const val EXTRA_SLOT_INDEX = "slot_index"
        private const val STATE_DISMISSED = "dismissed"

        fun createIntent(context: Context, corner: String, slotIndex: Int): Intent =
            Intent(context, CornerGestureSlotPickTrampolineActivity::class.java).apply {
                putExtra(EXTRA_CORNER, corner)
                putExtra(EXTRA_SLOT_INDEX, slotIndex)
            }
    }
}
