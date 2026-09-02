package com.slideindex.app.freezer

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.di.AppGraphEntryPoint
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.overlayBottomPanelHeightCap
import com.slideindex.app.overlay.overlayBottomPanelWidth
import com.slideindex.app.overlay.ScreenOffDismissReceiver
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper
import dagger.hilt.android.EntryPointAccessors

private const val FREEZER_OVERLAY_SCRIM_ANIM_MS = 220
private const val FREEZER_OVERLAY_DISMISS_MS = 380L
private val panelSlideSpringSpec = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 300f)

object FreezerOverlayWindow {
    private const val TAG = "FreezerOverlayWindow"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var panelVisibilityState: MutableTransitionState<Boolean>? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
    private var appContext: Context? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var dismissToken = 0

    val isShowing: Boolean
        get() = composeView != null && panelVisibilityState?.targetState == true

    fun show(context: Context): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(context)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        if (isShowing) {
            return true
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: run {
            Log.w(TAG, "show: accessibility service not connected")
            return false
        }
        ensureWindow(hostContext)
        ++dismissToken
        panelVisibilityState?.targetState = true
        screenOffDismissReceiver.register(hostContext)
        OverlaySceneController.onContentPanelShown()
        composeView?.requestFocus()
        return true
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        if (composeView == null) return
        val token = ++dismissToken
        panelVisibilityState?.targetState = false
        screenOffDismissReceiver.unregister()
        OverlaySceneController.onContentPanelHidden()
        mainHandler.postDelayed({
            if (token != dismissToken) return@postDelayed
            if (panelVisibilityState?.targetState == true) return@postDelayed
            cleanup()
        }, FREEZER_OVERLAY_DISMISS_MS)
    }

    fun handleBack(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    private fun ensureWindow(context: Context) {
        if (composeView != null) return
        appContext = context.applicationContext
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val visibilityState = MutableTransitionState(false)
        panelVisibilityState = visibilityState
        val overlayContext = OverlayCompose.themedContext(context)
        val composeOwner = OverlayComposeOwner()
        owner = composeOwner
        val appDeps = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AppGraphEntryPoint::class.java,
            ).dependencies()
        }.getOrNull()
        val view = OverlayCompose.createComposeView(overlayContext, composeOwner).apply {
            setContent {
                CompositionLocalProvider(
                    *(listOfNotNull(
                        appDeps?.let { LocalAppDependencies provides it },
                    ).toTypedArray()),
                ) {
                    OverlayAwareModuleTheme {
                        FreezerOverlayRoot(
                            visibilityState = visibilityState,
                            onDismiss = ::dismiss,
                        )
                    }
                }
            }
        }
        composeView = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = TAG
        }
        backHandler = OverlayViewBackHandler(view) { handleBack() }.also { it.attach() }
        wm.addView(view, params)
    }

    private fun cleanup() {
        if (panelVisibilityState?.targetState == true) return
        val view = composeView
        val wm = windowManager
        backHandler?.detach()
        backHandler = null
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        val dialogOwner = owner
        owner = null
        composeView = null
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
        windowManager = null
        panelVisibilityState = null
    }
}

@Composable
private fun FreezerOverlayRoot(
    visibilityState: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appDeps = remember(context) {
        runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AppGraphEntryPoint::class.java,
            ).dependencies()
        }.getOrNull()
    } ?: return
    val scrimAnimSpec = tween<Float>(FREEZER_OVERLAY_SCRIM_ANIM_MS)
    val dismissInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(scrimAnimSpec),
            exit = fadeOut(scrimAnimSpec),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(scrimAnimSpec) + slideInVertically(panelSlideSpringSpec) { it },
            exit = fadeOut(scrimAnimSpec) + slideOutVertically(panelSlideSpringSpec) { it },
            modifier = Modifier
                .overlayBottomPanelWidth()
                .fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .overlayBottomPanelHeightCap()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    FreezerPanelContent(
                        settingsRepository = appDeps.settingsRepository,
                        title = stringResource(R.string.gesture_action_freezer_panel),
                        onBack = onDismiss,
                        onManageApps = {
                            onDismiss()
                            context.startActivity(FreezerPanelIntents.manageApps(context))
                        },
                        onAppLaunched = onDismiss,
                        overlayMode = true,
                    )
                }
            }
        }
    }
}
