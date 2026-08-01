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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.theme.SlideIndexTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
            var themeSeedArgb by remember { mutableIntStateOf(AppSettings().themeColorArgb) }
            var dynamicColorEnabled by remember { mutableStateOf(false) }
            var themePaletteStyleId by remember { mutableIntStateOf(AppSettings().themePaletteStyleId) }
            var currentAction by remember { mutableStateOf<GestureAction>(GestureAction.None) }

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
                val settings = deps.settingsRepository.settings.first()
                themeSeedArgb = settings.themeColorArgb
                dynamicColorEnabled = settings.dynamicColorEnabled
                themePaletteStyleId = settings.themePaletteStyleId
            }

            BackHandler { finishPicker() }

            CompositionLocalProvider(LocalAppDependencies provides deps) {
                SlideIndexTheme(
                    seedColor = Color(themeSeedArgb),
                    dynamicColor = dynamicColorEnabled,
                    paletteStyle = ThemePaletteStyle.fromId(themePaletteStyleId),
                ) {
                    GestureActionPickerScreen(
                        trigger = GestureTriggerType.SHORT_SWIPE_IN,
                        current = currentAction,
                        onDismiss = { finishPicker() },
                        onSelect = { action ->
                            if (action is GestureAction.FloatingPointer) return@GestureActionPickerScreen
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
