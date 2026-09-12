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

import com.slideindex.app.overlay.appswitcher.AppSwitcherOverlayWindow

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.settings.FvAppSwitcherAxis

import com.slideindex.app.settings.FvAppSwitcherSlotIconOverride

import com.slideindex.app.settings.fvAppSwitcherFor

import com.slideindex.app.ui.appswitcher.AppSwitcherSlotIconEditorHost

import com.slideindex.app.ui.compose.LocalAppDependencies

import com.slideindex.app.ui.miuix.theme.ModuleTheme

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.launch



@AndroidEntryPoint

class AppSwitcherSlotIconEditorTrampolineActivity : ComponentActivity() {



    @Inject lateinit var deps: AppDependencies



    private var dismissed = false

    private var slotIndex = -1



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



        val axis = intent.getStringExtra(EXTRA_AXIS)?.let(::axisFromName)

        slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)

        if (axis == null || slotIndex < 0) {

            finish()

            return

        }



        @Suppress("DEPRECATION")

        overridePendingTransition(0, 0)



        setContent {

            val scope = rememberCoroutineScope()

            var appSettings by remember { mutableStateOf(AppSettings()) }

            var slotLabel by remember { mutableStateOf("") }

            var slotIconOverride by remember { mutableStateOf<FvAppSwitcherSlotIconOverride?>(null) }



            LaunchedEffect(axis, slotIndex) {

                appSettings = deps.settingsRepository.settings.first()

                val fvSettings = appSettings.fvAppSwitcherFor(axis)

                val item = fvSettings.itemAt(slotIndex)

                slotIconOverride = fvSettings.iconOverrideAt(slotIndex)

                slotLabel = item?.label?.ifBlank { item.payload }.orEmpty()

            }



            BackHandler { finishPicker() }



            CompositionLocalProvider(LocalAppDependencies provides deps) {

                ModuleTheme(settings = appSettings) {

                    AppSwitcherSlotIconEditorHost(

                        slotLabel = slotLabel,

                        initialOverride = slotIconOverride,

                        onBack = { finishPicker() },

                        onSave = { override ->

                            scope.launch {

                                deps.settingsRepository.setFvAppSwitcherSlotIconOverride(

                                    axis = axis,

                                    index = slotIndex,

                                    override = override,

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

        AppSwitcherOverlayWindow.resumeAfterSlotIconEditor()

    }



    override fun onSaveInstanceState(outState: Bundle) {

        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_DISMISSED, dismissed)

    }



    companion object {

        private const val EXTRA_AXIS = "axis"

        private const val EXTRA_SLOT_INDEX = "slot_index"

        private const val STATE_DISMISSED = "dismissed"



        private const val AXIS_VERTICAL = "vertical"

        private const val AXIS_HORIZONTAL = "horizontal"



        fun createIntent(

            context: Context,

            axis: FvAppSwitcherAxis,

            slotIndex: Int,

        ): Intent =

            Intent(context, AppSwitcherSlotIconEditorTrampolineActivity::class.java).apply {

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                putExtra(EXTRA_AXIS, axisToName(axis))

                putExtra(EXTRA_SLOT_INDEX, slotIndex)

            }



        private fun axisToName(axis: FvAppSwitcherAxis): String = when (axis) {

            FvAppSwitcherAxis.VERTICAL -> AXIS_VERTICAL

            FvAppSwitcherAxis.HORIZONTAL -> AXIS_HORIZONTAL

        }



        private fun axisFromName(name: String): FvAppSwitcherAxis? = when (name) {

            AXIS_VERTICAL -> FvAppSwitcherAxis.VERTICAL

            AXIS_HORIZONTAL -> FvAppSwitcherAxis.HORIZONTAL

            else -> null

        }

    }

}


