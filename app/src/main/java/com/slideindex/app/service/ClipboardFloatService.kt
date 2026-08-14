package com.slideindex.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboardfloat.ClipboardFloatDisplayMode
import com.slideindex.app.clipboardfloat.ClipboardFloatListController
import com.slideindex.app.clipboardfloat.ClipboardFloatRoot
import com.slideindex.app.clipboardfloat.ClipboardPasteHelper
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.ClipboardFloatEntryClickAction
import com.slideindex.app.settings.ClipboardFloatWindowMetrics
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ClipboardFloatService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject lateinit var deps: AppDependencies

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var composeView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private lateinit var listController: ClipboardFloatListController

    private var hostContext: Context? = null
    private var viewAdded = false

    private var displayMode by mutableStateOf(ClipboardFloatDisplayMode.Chip)
    private var panelPinned by mutableStateOf(false)
    private var showChipPref by mutableStateOf(true)
    private var clickAction by mutableStateOf(ClipboardFloatEntryClickAction.PASTE)
    private var chipFollowIme by mutableStateOf(true)
    private var panelFollowIme = true
    private var imeTop = 0
    private var chipPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var chipPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelWidthDp by mutableIntStateOf(ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP)
    private var panelHeightDp by mutableIntStateOf(ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP)

    private var idleGeometryPersistRunnable: Runnable? = null
    private var isDraggingWindow = false
    private var chipRetainedAfterManualCollapse = false

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
        listController = ClipboardFloatListController(
            repository = ClipboardAccess.repository,
            scope = lifecycleScope,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        when (intent?.action) {
            ACTION_HIDE -> {
                if (!shouldStayVisibleWithoutIme()) {
                    hideWindow()
                    return START_NOT_STICKY
                }
                persistGeometryOnClose()
                return START_STICKY
            }
            ACTION_UPDATE_IME -> {
                imeTop = intent.getIntExtra(EXTRA_IME_TOP, imeTop)
                if (viewAdded && shouldFollowIme() && !isDraggingWindow) {
                    applyWindowGeometry(
                        forceDefaultPosition = false,
                        anchorPanelToIme = displayMode == ClipboardFloatDisplayMode.Expanded,
                    )
                }
                return START_STICKY
            }
        }

        val resolvedHost = MessageOverlayHost.resolveHostContext(this) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        hostContext = resolvedHost
        windowManager = resolvedHost.getSystemService(WINDOW_SERVICE) as WindowManager

        loadSettingsSnapshot()
        imeTop = intent?.getIntExtra(EXTRA_IME_TOP, imeTop) ?: imeTop
        showChipPref = intent?.getBooleanExtra(EXTRA_SHOW_CHIP, showChipPref) ?: showChipPref

        if (!viewAdded) {
            chipRetainedAfterManualCollapse = false
            displayMode = if (showChipPref) ClipboardFloatDisplayMode.Chip else ClipboardFloatDisplayMode.Expanded
            createAndAttachWindow(resolvedHost)
        } else if (shouldFollowIme() && !isDraggingWindow) {
            applyWindowGeometry(
                forceDefaultPosition = forceDefaultPositionForMode(),
                anchorPanelToIme = displayMode == ClipboardFloatDisplayMode.Expanded,
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        persistGeometryOnClose()
        if (viewAdded) {
            composeView?.let { runCatching { windowManager.removeView(it) } }
            viewAdded = false
        }
        OverlayCompose.disposeComposeView(composeView)
        composeOwner?.destroy()
        composeOwner = null
        composeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun loadSettingsSnapshot() {
        val snapshot = deps.settingsRepository.readSnapshot()
        panelPinned = snapshot.clipboardFloatPanelPinPosition
        showChipPref = snapshot.clipboardFloatShowChip
        clickAction = snapshot.clipboardFloatEntryClickAction
        chipFollowIme = snapshot.clipboardFloatChipFollowIme
        chipPosX = snapshot.clipboardFloatChipX
        chipPosY = snapshot.clipboardFloatChipY
        panelWidthDp = snapshot.clipboardFloatPanelWidthDp
        panelHeightDp = snapshot.clipboardFloatPanelHeightDp
        panelPosX = snapshot.clipboardFloatPanelX
        panelPosY = snapshot.clipboardFloatPanelY
        panelFollowIme = when {
            snapshot.clipboardFloatPanelPinPosition -> false
            !hasSavedPanelPosition() -> true
            else -> false
        }
    }

    private fun hasSavedChipPosition(): Boolean =
        chipPosX != ClipboardFloatWindowMetrics.UNSET_POSITION &&
            chipPosY != ClipboardFloatWindowMetrics.UNSET_POSITION

    private fun hasSavedPanelPosition(): Boolean =
        panelPosX != ClipboardFloatWindowMetrics.UNSET_POSITION &&
            panelPosY != ClipboardFloatWindowMetrics.UNSET_POSITION

    private fun shouldUseDefaultChipPosition(forceDefaultPosition: Boolean): Boolean =
        forceDefaultPosition || chipFollowIme || !hasSavedChipPosition()

    private fun shouldUseDefaultPanelPosition(
        forceDefaultPosition: Boolean,
        anchorPanelToIme: Boolean,
    ): Boolean {
        if (!hasSavedPanelPosition()) return true
        if (panelPinned) return false
        if (!panelFollowIme) return false
        if (forceDefaultPosition) return true
        return anchorPanelToIme
    }

    private fun shouldStayVisibleWithoutIme(): Boolean =
        (displayMode == ClipboardFloatDisplayMode.Expanded && panelPinned) ||
            (displayMode == ClipboardFloatDisplayMode.Chip && chipRetainedAfterManualCollapse)

    private fun shouldFollowIme(): Boolean = when (displayMode) {
        ClipboardFloatDisplayMode.Chip -> chipFollowIme && !chipRetainedAfterManualCollapse
        ClipboardFloatDisplayMode.Expanded -> panelFollowIme && !panelPinned
    }

    private fun forceDefaultPositionForMode(): Boolean = when (displayMode) {
        ClipboardFloatDisplayMode.Chip -> shouldUseDefaultChipPosition(forceDefaultPosition = false)
        ClipboardFloatDisplayMode.Expanded -> shouldUseDefaultPanelPosition(
            forceDefaultPosition = false,
            anchorPanelToIme = false,
        )
    }

    private fun createAndAttachWindow(context: Context) {
        val overlayContext = OverlayCompose.themedContext(context)
        params = WindowManager.LayoutParams().apply {
            type = OverlayWindowTypes.overlayWindowType(context)
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            gravity = Gravity.TOP or Gravity.START
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        val owner = OverlayComposeOwner()
        composeOwner = owner
        val contentView = OverlayCompose.createComposeView(overlayContext, owner).apply {
            setContent {
                ClipboardFloatRoot(
                    mode = displayMode,
                    pinned = panelPinned,
                    listController = listController,
                    windowWidthDp = panelWidthDp,
                    onOpenExpanded = ::expandWindow,
                    onTogglePin = ::togglePin,
                    onResetLayout = ::resetLayout,
                    onCollapse = ::collapseWindow,
                    onClose = ::closeWindow,
                    onDragWindow = ::onDragWindow,
                    onDragWindowStart = ::onDragWindowStart,
                    onDragWindowEnd = ::onDragWindowEnd,
                    onResizeWindow = ::onResizeWindow,
                    onEntryClick = ::onEntryClick,
                    onEntryLongClick = ::onEntryLongClick,
                )
            }
        }
        composeView = contentView
        applyWindowGeometry(
            forceDefaultPosition = forceDefaultPositionForMode(),
            anchorPanelToIme = false,
        )
        windowManager.addView(composeView, params)
        viewAdded = true
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun applyWindowGeometry(
        forceDefaultPosition: Boolean,
        anchorPanelToIme: Boolean = false,
    ) {
        if (isDraggingWindow) return
        if (!viewAdded && !::params.isInitialized) return
        val density = resources.displayMetrics.density
        val marginPx = (12f * density).roundToInt()
        val screenWidth = resources.displayMetrics.widthPixels

        if (displayMode == ClipboardFloatDisplayMode.Chip) {
            params.width = (44f * density).roundToInt()
            params.height = (36f * density).roundToInt()
            if (shouldUseDefaultChipPosition(forceDefaultPosition)) {
                applyImeAnchoredPosition(screenWidth, marginPx)
            } else {
                params.x = chipPosX
                params.y = chipPosY.coerceAtLeast(marginPx)
            }
        } else {
            params.width = (panelWidthDp * density).roundToInt()
            params.height = (panelHeightDp * density).roundToInt()
            if (shouldUseDefaultPanelPosition(forceDefaultPosition, anchorPanelToIme)) {
                applyImeAnchoredPosition(screenWidth, marginPx)
            } else {
                params.x = panelPosX
                params.y = panelPosY.coerceAtLeast(marginPx)
            }
        }

        if (viewAdded) {
            composeView?.let { windowManager.updateViewLayout(it, params) }
        }
    }

    private fun applyImeAnchoredPosition(screenWidth: Int, marginPx: Int) {
        params.x = (screenWidth - params.width - marginPx).coerceAtLeast(marginPx)
        val y = imeTop - params.height - marginPx
        params.y = y.coerceAtLeast(marginPx)
    }

    private fun onDragWindowStart() {
        isDraggingWindow = true
        cancelIdleGeometryPersist()
    }

    private fun onDragWindow(dx: Float, dy: Float) {
        if (!viewAdded) return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        when (displayMode) {
            ClipboardFloatDisplayMode.Chip -> {
                chipPosX = params.x
                chipPosY = params.y
                chipFollowIme = false
            }
            ClipboardFloatDisplayMode.Expanded -> {
                panelPosX = params.x
                panelPosY = params.y
                panelFollowIme = false
            }
        }
        composeView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun onDragWindowEnd() {
        isDraggingWindow = false
        scheduleIdleGeometryPersist()
    }

    private fun onResizeWindow(dw: Float, dh: Float) {
        if (!viewAdded || displayMode != ClipboardFloatDisplayMode.Expanded) return
        cancelIdleGeometryPersist()
        val density = resources.displayMetrics.density
        val nextWidthDp = ClipboardFloatWindowMetrics.coerceWidth(
            (params.width + dw.roundToInt()).let { (it / density).roundToInt() },
        )
        val nextHeightDp = ClipboardFloatWindowMetrics.coerceHeight(
            (params.height + dh.roundToInt()).let { (it / density).roundToInt() },
        )
        panelWidthDp = nextWidthDp
        panelHeightDp = nextHeightDp
        params.width = (nextWidthDp * density).roundToInt()
        params.height = (nextHeightDp * density).roundToInt()
        composeView?.let { windowManager.updateViewLayout(it, params) }
        scheduleIdleGeometryPersist()
    }

    private fun scheduleIdleGeometryPersist() {
        idleGeometryPersistRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable { persistGeometryOnClose() }
        idleGeometryPersistRunnable = runnable
        mainHandler.postDelayed(runnable, IDLE_GEOMETRY_PERSIST_MS)
    }

    private fun cancelIdleGeometryPersist() {
        idleGeometryPersistRunnable?.let(mainHandler::removeCallbacks)
        idleGeometryPersistRunnable = null
    }

    private fun persistGeometryOnClose() {
        cancelIdleGeometryPersist()
        val chipX = chipPosX
        val chipY = chipPosY
        val chipFollow = chipFollowIme
        val panelX = panelPosX
        val panelY = panelPosY
        val widthDp = panelWidthDp
        val heightDp = panelHeightDp

        lifecycleScope.launch(Dispatchers.IO) {
            deps.settingsRepository.setClipboardFloatChipGeometry(
                x = chipX,
                y = chipY,
                followIme = chipFollow,
            )
            deps.settingsRepository.setClipboardFloatGeometry(
                x = panelX,
                y = panelY,
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }
    }

    private fun expandWindow() {
        chipRetainedAfterManualCollapse = false
        displayMode = ClipboardFloatDisplayMode.Expanded
        panelFollowIme = when {
            panelPinned -> false
            !hasSavedPanelPosition() -> true
            else -> panelFollowIme
        }
        applyWindowGeometry(
            forceDefaultPosition = !hasSavedPanelPosition(),
            anchorPanelToIme = false,
        )
    }

    private fun collapseWindow() {
        if (!showChipPref) {
            closeWindow()
            return
        }
        chipRetainedAfterManualCollapse = true
        chipPosX = params.x
        chipPosY = params.y
        chipFollowIme = false
        displayMode = ClipboardFloatDisplayMode.Chip
        applyWindowGeometry(forceDefaultPosition = false)
    }

    private fun closeWindow() {
        hideWindow()
    }

    private fun hideWindow() {
        chipRetainedAfterManualCollapse = false
        persistGeometryOnClose()
        if (viewAdded) {
            composeView?.let { runCatching { windowManager.removeView(it) } }
            viewAdded = false
        }
        stopSelf()
    }

    private fun togglePin() {
        panelPinned = !panelPinned
        panelPosX = params.x
        panelPosY = params.y
        panelFollowIme = when {
            panelPinned -> false
            !hasSavedPanelPosition() -> true
            else -> false
        }
        lifecycleScope.launch(Dispatchers.IO) {
            deps.settingsRepository.setClipboardFloatPinPosition(panelPinned)
            deps.settingsRepository.setClipboardFloatGeometry(
                x = panelPosX,
                y = panelPosY,
                widthDp = panelWidthDp,
                heightDp = panelHeightDp,
            )
        }
    }

    private fun resetLayout() {
        lifecycleScope.launch {
            deps.settingsRepository.resetClipboardFloatGeometry()
            loadSettingsSnapshot()
            panelFollowIme = !panelPinned
            applyWindowGeometry(forceDefaultPosition = true, anchorPanelToIme = true)
        }
    }

    private fun onEntryClick(entry: com.slideindex.app.clipboard.ClipboardEntry) {
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (service == null) {
            Toast.makeText(this, R.string.search_engine_accessibility_required, Toast.LENGTH_SHORT).show()
            return
        }
        val ok = ClipboardPasteHelper.performEntryAction(
            service = service,
            context = this,
            entry = entry,
            action = clickAction,
        )
        if (!ok) {
            Toast.makeText(this, R.string.clipboard_float_paste_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onEntryLongClick(entry: com.slideindex.app.clipboard.ClipboardEntry) {
        ClipboardWriter.write(this, entry)
        Toast.makeText(this, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_SHOW_IME = "com.slideindex.app.clipboard_float.SHOW_IME"
        const val ACTION_HIDE = "com.slideindex.app.clipboard_float.HIDE"
        const val ACTION_UPDATE_IME = "com.slideindex.app.clipboard_float.UPDATE_IME"
        const val EXTRA_IME_TOP = "ime_top"
        const val EXTRA_SHOW_CHIP = "show_chip"

        private const val IDLE_GEOMETRY_PERSIST_MS = 1500L

        fun updateImeTop(context: Context, imeTop: Int) {
            context.applicationContext.startService(
                Intent(context.applicationContext, ClipboardFloatService::class.java).apply {
                    action = ACTION_UPDATE_IME
                    putExtra(EXTRA_IME_TOP, imeTop)
                },
            )
        }
    }
}
