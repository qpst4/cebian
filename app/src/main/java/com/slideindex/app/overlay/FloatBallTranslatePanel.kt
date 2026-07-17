package com.slideindex.app.overlay



import android.content.BroadcastReceiver

import android.content.Context

import android.content.Intent

import android.content.IntentFilter

import android.graphics.PixelFormat

import android.os.Handler

import android.os.Looper

import android.util.Log

import android.view.Gravity

import android.view.WindowManager

import android.widget.Toast

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.widthIn

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.MutableState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.platform.ComposeView

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp

import com.slideindex.app.R

import com.slideindex.app.di.OverlayDependencyAccess

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.overlay.pickresult.PickResultInteractiveTextSection

import com.slideindex.app.overlay.pickresult.PickResultPanelMaxWidth

import com.slideindex.app.overlay.pickresult.pickResultPanelCard
import com.slideindex.app.overlay.pickresult.pickResultWindowHeightDp

import com.slideindex.app.overlay.pickresult.PickResultSectionHeader

import com.slideindex.app.overlay.pickresult.PickResultTextMode

import com.slideindex.app.ui.theme.SlideIndexTheme



private val PANEL_HORIZONTAL_PADDING = 12.dp

private val PANEL_MAX_HEIGHT_FRACTION = 0.55f



enum class FloatBallTranslatePanelPhase {

    LOADING,

    SUCCESS,

    ERROR,

}



/**

 * Independent translation overlay above the pick-result panel; reuses pick-result text interaction UI.

 */

object FloatBallTranslatePanel {

    private const val TAG = "FloatBallTranslatePanel"

    private val mainHandler = Handler(Looper.getMainLooper())



    private var windowManager: WindowManager? = null

    private var composeView: ComposeView? = null

    private var owner: OverlayComposeOwner? = null

    private var screenOffReceiver: BroadcastReceiver? = null

    private var appContext: Context? = null

    private var layoutParams: WindowManager.LayoutParams? = null



    private var phaseState: MutableState<FloatBallTranslatePanelPhase>? = null

    private var translatedTextState: MutableState<String?>? = null

    private var errorMessageState: MutableState<String?>? = null

    private var textModeState: MutableState<PickResultTextMode>? = null

    internal val panelVisible = mutableStateOf(false)

    val isShowing: Boolean get() = composeView != null



    fun showLoading(context: Context) {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { showLoading(context) }

            return

        }

        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext

        ensureWindow(hostContext)

        translatedTextState?.value = null

        errorMessageState?.value = null

        textModeState?.value = PickResultTextMode.WORD_TAP

        phaseState?.value = FloatBallTranslatePanelPhase.LOADING
        updateWindowFocusable(focusable = false)

    }



    fun showResult(context: Context, translatedText: String) {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { showResult(context, translatedText) }

            return

        }

        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext

        ensureWindow(hostContext)

        translatedTextState?.value = translatedText

        errorMessageState?.value = null

        textModeState?.value = PickResultTextMode.WORD_TAP

        phaseState?.value = FloatBallTranslatePanelPhase.SUCCESS
        updateWindowFocusableForMode(PickResultTextMode.WORD_TAP)

    }



    fun showError(context: Context, message: String) {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { showError(context, message) }

            return

        }

        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext

        ensureWindow(hostContext)

        translatedTextState?.value = null

        errorMessageState?.value = message

        phaseState?.value = FloatBallTranslatePanelPhase.ERROR
        updateWindowFocusable(focusable = false)

    }



    fun dismiss() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { dismiss() }

            return

        }

        val view = composeView

        val wm = windowManager

        if (view != null && wm != null) {

            runCatching { wm.removeView(view) }

        }

        screenOffReceiver?.let { receiver ->

            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }

        }

        val currentOwner = owner

        if (currentOwner != null) {

            view?.post { currentOwner.destroy() } ?: currentOwner.destroy()

        }

        owner = null

        composeView = null

        layoutParams = null

        windowManager = null

        phaseState = null

        translatedTextState = null

        errorMessageState = null

        textModeState = null
        panelVisible.value = false
        screenOffReceiver = null

        appContext = null

    }



    private fun updateWindowFocusableForMode(mode: PickResultTextMode) {

        updateWindowFocusable(

            focusable = mode == PickResultTextMode.SELECT || mode == PickResultTextMode.EDIT,

        )

    }



    private fun updateWindowFocusable(focusable: Boolean) {

        val wm = windowManager ?: return

        val view = composeView ?: return

        val params = layoutParams ?: return

        params.flags = if (focusable) {

            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

        } else {

            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        }

        runCatching { wm.updateViewLayout(view, params) }

    }



    private fun ensureWindow(context: Context) {

        if (composeView != null) return



        val phaseHolder = mutableStateOf(FloatBallTranslatePanelPhase.LOADING)

        val translatedHolder = mutableStateOf<String?>(null)

        val errorHolder = mutableStateOf<String?>(null)

        val textModeHolder = mutableStateOf(PickResultTextMode.WORD_TAP)

        phaseState = phaseHolder

        translatedTextState = translatedHolder

        errorMessageState = errorHolder

        textModeState = textModeHolder



        val dialogOwner = OverlayComposeOwner()

        val overlayContext = OverlayCompose.themedContext(context)

        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {

            setContent {

                val phase by phaseHolder

                val translatedText by translatedHolder

                val errorMessage by errorHolder

                val textMode by textModeHolder

                val settingsHolder = remember { mutableStateOf(AppSettings()) }

                LaunchedEffect(overlayContext) {

                    val flow = OverlayDependencyAccess.overlayDependencies(overlayContext)

                        ?.settingsRepository

                        ?.settings

                        ?: return@LaunchedEffect

                    flow.collect { settingsHolder.value = it }

                }

                val settings by settingsHolder

                FloatBallTranslatePanelContent(

                    phase = phase,

                    translatedText = translatedText,

                    errorMessage = errorMessage,

                    textMode = textMode,

                    textSizeSp = settings.floatBallPickTextSizeSp,

                    onTextModeChange = { mode ->

                        textModeHolder.value = mode

                        updateWindowFocusableForMode(mode)

                    },

                    onDismiss = { dismiss() },

                    onTextChange = { translatedHolder.value = it },

                    onCopy = { text ->

                        FloatBallTextPick.copyText(context, text)

                        Toast.makeText(context, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()

                    },

                    onSearch = { FloatBallTextPick.searchText(context, it) },

                    onShare = { FloatBallTextPick.shareText(context, it) },

                    onPaste = {

                        val pasted = FloatBallTextPick.readClipboardText(context)

                        if (pasted == null) {

                            Toast.makeText(context, R.string.float_ball_paste_empty, Toast.LENGTH_SHORT).show()

                        } else {

                            translatedHolder.value = pasted

                        }

                    },

                    onRemoveSpaces = { value, removeAll ->

                        translatedHolder.value = if (removeAll) {

                            value.replace(Regex("\\s+"), "")

                        } else {

                            value.trim()

                        }

                    },

                )

            }

        }



        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

            ?: run {

                dialogOwner.destroy()

                return

            }

        val params = buildLayoutParams(context)

        val added = runCatching { wm.addView(compose, params) }.isSuccess

        if (!added) {

            dialogOwner.destroy()

            Log.e(TAG, "failed to add translate panel")

            return

        }



        windowManager = wm

        composeView = compose

        owner = dialogOwner

        layoutParams = params

        appContext = context

        panelVisible.value = true

        registerScreenOffReceiver(context)

    }



    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {

        return WindowManager.LayoutParams(

            WindowManager.LayoutParams.MATCH_PARENT,

            WindowManager.LayoutParams.MATCH_PARENT,

            OverlayWindowTypes.overlayWindowType(context),

            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or

                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or

                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,

            PixelFormat.TRANSLUCENT,

        ).apply {

            gravity = Gravity.CENTER

            layoutInDisplayCutoutMode =

                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        }

    }



    private fun registerScreenOffReceiver(context: Context) {

        val receiver = object : BroadcastReceiver() {

            override fun onReceive(receiverContext: Context?, intent: Intent?) {

                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()

            }

        }

        screenOffReceiver = receiver

        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }

    }

}



@Composable

private fun FloatBallTranslatePanelContent(

    phase: FloatBallTranslatePanelPhase,

    translatedText: String?,

    errorMessage: String?,

    textMode: PickResultTextMode,

    textSizeSp: Float,

    onTextModeChange: (PickResultTextMode) -> Unit,

    onDismiss: () -> Unit,

    onTextChange: (String) -> Unit,

    onCopy: (String) -> Unit,

    onSearch: (String) -> Unit,

    onShare: (String) -> Unit,

    onPaste: () -> Unit,

    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,

) {

    val maxPanelHeight = pickResultWindowHeightDp(PANEL_MAX_HEIGHT_FRACTION)
    val dismissInteraction = remember { MutableInteractionSource() }

    SlideIndexTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = PANEL_HORIZONTAL_PADDING)
                    .widthIn(max = PickResultPanelMaxWidth)
                    .heightIn(max = maxPanelHeight)
                    .pickResultPanelCard()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {

                PickResultSectionHeader(

                    title = stringResource(R.string.float_ball_translate_panel_title),

                    expanded = true,

                    onToggle = {},

                    collapsible = false,

                )

                when (phase) {

                    FloatBallTranslatePanelPhase.LOADING -> {

                        Row(

                            modifier = Modifier

                                .fillMaxWidth()

                                .padding(horizontal = 20.dp, vertical = 8.dp),

                            verticalAlignment = Alignment.CenterVertically,

                            horizontalArrangement = Arrangement.spacedBy(10.dp),

                        ) {

                            CircularProgressIndicator(

                                modifier = Modifier.size(20.dp),

                                strokeWidth = 2.dp,

                            )

                            Text(

                                text = stringResource(R.string.float_ball_translating),

                                style = MaterialTheme.typography.bodyMedium,

                            )

                        }

                    }

                    FloatBallTranslatePanelPhase.SUCCESS -> {

                        val result = translatedText.orEmpty()

                        if (result.isNotBlank()) {

                            PickResultInteractiveTextSection(

                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(horizontal = 12.dp),

                                text = result,

                                textMode = textMode,

                                textSizeSp = textSizeSp,

                                onTextModeChange = onTextModeChange,

                                onTextChange = onTextChange,

                                showSourceChips = false,

                                translateEnabled = false,

                                pinActionBarOutside = true,

                                showSearch = true,

                                onSearch = onSearch,

                                onShare = onShare,

                                onCopy = onCopy,

                                onPaste = onPaste,

                                onTranslate = {},

                                onRemoveSpaces = onRemoveSpaces,

                            )

                        }

                    }

                    FloatBallTranslatePanelPhase.ERROR -> {

                        Column(

                            modifier = Modifier

                                .fillMaxWidth()

                                .padding(horizontal = 20.dp, vertical = 8.dp),

                            verticalArrangement = Arrangement.spacedBy(6.dp),

                        ) {

                            Text(

                                text = stringResource(R.string.float_ball_translate_failed),

                                style = MaterialTheme.typography.bodyMedium,

                                color = MaterialTheme.colorScheme.error,

                            )

                            if (!errorMessage.isNullOrBlank()) {

                                Text(

                                    text = translateErrorLabel(errorMessage),

                                    style = MaterialTheme.typography.bodySmall,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                                )

                            }

                        }

                    }

                }

            }

        }

    }

}



@Composable

private fun translateErrorLabel(code: String): String = when (code) {

    "mlkit_model_not_installed" -> stringResource(R.string.float_ball_translate_error_model_missing)

    "wifi_required" -> stringResource(R.string.float_ball_translate_error_wifi_required)

    "unsupported_language" -> stringResource(R.string.float_ball_translate_error_unsupported_language)

    "translate_unavailable" -> stringResource(R.string.float_ball_translate_error_unavailable)

    "network_error", "http_403", "http_429", "http_500" -> stringResource(R.string.float_ball_translate_error_network)

    else -> code

}


