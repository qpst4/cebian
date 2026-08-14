package com.slideindex.app.service

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.slideindex.app.overlay.history.HistoryFloatContent
import com.slideindex.app.settings.HistoryFloatHandleWidth
import com.slideindex.app.stash.StashCoordinator

class HistoryFloatService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private lateinit var windowManager: WindowManager
    private lateinit var mainParams: LayoutParams
    private lateinit var composeView: ComposeView
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var handleVisible by mutableStateOf(true)
    private var handleWidth by mutableIntStateOf(HistoryFloatHandleWidth.DEFAULT_DP)
    private var lockLoc = true
    private var landscapeEnabled = false
    private var positionY = 0
    private var viewAdded = false
    private var hiddenForFullscreen = false
    private var hiddenForLandscape = false
    private val visibleDisplayFrame = Rect()
    private val fullscreenCheckHandler = Handler(Looper.getMainLooper())
    private val fullscreenCheckRunnable = object : Runnable {
        override fun run() {
            updateFullscreenVisibility()
            fullscreenCheckHandler.postDelayed(this, FULLSCREEN_CHECK_INTERVAL_MS)
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mainParams = LayoutParams()
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@HistoryFloatService)
            setViewTreeSavedStateRegistryOwner(this@HistoryFloatService)
            setOnApplyWindowInsetsListener { _, insets ->
                updateFullscreenVisibility()
                insets
            }
            setContent {
                HistoryFloatContent(
                    handleVisible = handleVisible,
                    handleWidth = handleWidth,
                    onOpenPanel = { openClipboardPanel() },
                    onMoveHandle = { moveHandle(it) },
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        when (intent?.action) {
            ACTION_LOCK_POSITION -> {
                lockLoc = intent.getBooleanExtra(EXTRA_LOCK_POSITION, lockLoc)
                return START_STICKY
            }
            ACTION_SET_HANDLE_WIDTH -> {
                handleWidth = intent.getIntExtra(
                    EXTRA_HANDLE_WIDTH_DP,
                    HistoryFloatHandleWidth.DEFAULT_DP,
                )
                return START_STICKY
            }
            ACTION_SET_LANDSCAPE_ENABLED -> {
                landscapeEnabled = intent.getBooleanExtra(EXTRA_LANDSCAPE_ENABLED, landscapeEnabled)
                updateLandscapeVisibility()
                return START_STICKY
            }
        }
        handleWidth = intent?.getIntExtra(EXTRA_HANDLE_WIDTH_DP, handleWidth) ?: handleWidth
        lockLoc = intent?.getBooleanExtra(EXTRA_LOCK_POSITION, lockLoc) ?: lockLoc
        landscapeEnabled = intent?.getBooleanExtra(EXTRA_LANDSCAPE_ENABLED, landscapeEnabled) ?: landscapeEnabled
        showFloatWindow()
        return START_STICKY
    }

    override fun onDestroy() {
        fullscreenCheckHandler.removeCallbacks(fullscreenCheckRunnable)
        if (viewAdded) {
            windowManager.removeView(composeView)
            viewAdded = false
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLandscapeVisibility()
        updateFullscreenVisibility()
    }

    private fun showFloatWindow() {
        if (!Settings.canDrawOverlays(this) || viewAdded) {
            return
        }

        mainParams.type = LayoutParams.TYPE_APPLICATION_OVERLAY
        mainParams.format = PixelFormat.RGBA_8888
        mainParams.width = LayoutParams.WRAP_CONTENT
        mainParams.height = LayoutParams.WRAP_CONTENT
        mainParams.flags = BASE_WINDOW_FLAGS
        mainParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPos1P3()
        windowManager.addView(composeView, mainParams)
        viewAdded = true
        updateLandscapeVisibility()
        updateFullscreenVisibility()
        fullscreenCheckHandler.removeCallbacks(fullscreenCheckRunnable)
        fullscreenCheckHandler.post(fullscreenCheckRunnable)
    }

    private fun setPos1P3() {
        val screenHeight = resources.displayMetrics.heightPixels
        mainParams.x = 0
        mainParams.y = -(screenHeight / 3)
        positionY = mainParams.y
    }

    private fun moveHandle(dy: Float) {
        if (lockLoc || !viewAdded) {
            return
        }
        positionY += dy.toInt()
        mainParams.x = 0
        mainParams.y = positionY
        windowManager.updateViewLayout(composeView, mainParams)
    }

    private fun openClipboardPanel() {
        StashCoordinator.openClipboardPanel(applicationContext)
    }

    private fun updateFullscreenVisibility() {
        if (!viewAdded) {
            return
        }
        val isFullscreen = isSystemFullscreen()
        if (hiddenForFullscreen == isFullscreen) {
            return
        }
        hiddenForFullscreen = isFullscreen
        applyFloatVisibility()
    }

    private fun updateLandscapeVisibility() {
        if (!viewAdded) {
            return
        }
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val shouldHide = isLandscape && !landscapeEnabled
        if (hiddenForLandscape == shouldHide) {
            return
        }
        hiddenForLandscape = shouldHide
        applyFloatVisibility()
    }

    private fun applyFloatVisibility() {
        val hidden = hiddenForFullscreen || hiddenForLandscape
        val expectedFlags = if (hidden) {
            BASE_WINDOW_FLAGS or LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            BASE_WINDOW_FLAGS
        }
        if (viewAdded && mainParams.flags != expectedFlags) {
            mainParams.flags = expectedFlags
            windowManager.updateViewLayout(composeView, mainParams)
        }
        composeView.alpha = if (hidden) 0f else 1f
        composeView.visibility = View.VISIBLE
    }

    private fun isSystemFullscreen(): Boolean {
        composeView.getWindowVisibleDisplayFrame(visibleDisplayFrame)
        val statusBarHeight = getStatusBarHeight()
        if (statusBarHeight <= 0) {
            return false
        }
        return visibleDisplayFrame.top <= statusBarHeight / 2
    }

    private fun getStatusBarHeight(): Int {
        if (!viewAdded) return 0
        return ViewCompat.getRootWindowInsets(composeView)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
    }

    companion object {
        const val ACTION_LOCK_POSITION = "com.slideindex.app.history_float.LOCK_POSITION"
        const val ACTION_SET_HANDLE_WIDTH = "com.slideindex.app.history_float.SET_HANDLE_WIDTH"
        const val ACTION_SET_LANDSCAPE_ENABLED = "com.slideindex.app.history_float.SET_LANDSCAPE_ENABLED"
        const val EXTRA_HANDLE_WIDTH_DP = "handle_width_dp"
        const val EXTRA_LOCK_POSITION = "lock_position"
        const val EXTRA_LANDSCAPE_ENABLED = "landscape_enabled"

        private const val BASE_WINDOW_FLAGS =
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL
        private const val FULLSCREEN_CHECK_INTERVAL_MS = 500L
    }
}
