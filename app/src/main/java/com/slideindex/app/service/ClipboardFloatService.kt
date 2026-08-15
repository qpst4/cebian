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
import android.view.View
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
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.StashPanelInitialTab
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
    private var rememberPosition by mutableStateOf(false)
    private var panelPinned by mutableStateOf(false)
    private var showChipPref by mutableStateOf(true)
    private var clickAction by mutableStateOf(ClipboardFloatEntryClickAction.PASTE)
    private var imeTop = 0
    private var chipPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var chipPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
    private var panelWidthDp by mutableIntStateOf(ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP)
    private var panelHeightDp by mutableIntStateOf(ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP)

    private var idleGeometryPersistRunnable: Runnable? = null
    private var isDraggingWindow = false
    private var searchActive by mutableStateOf(false)
    private var chipRetainedAfterManualCollapse = false
    /** 搜索收起后大窗在无键盘时仍保持展开，直到用户手动收起或关闭 */
    private var expandedRetainedWithoutIme = false
    /** 本次会话内用户拖动/收起调整过 chip 位置（记住位置关时仍生效，但不写盘） */
    private var chipPositionCustomizedThisSession = false
    /** 本次会话内用户拖动过大窗位置 */
    private var panelPositionCustomizedThisSession = false

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
                    applyWindowGeometry(forceDefaultPosition = false)
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

        if (!viewAdded) {
            loadSettingsSnapshot()
        }
        imeTop = intent?.getIntExtra(EXTRA_IME_TOP, imeTop) ?: imeTop
        showChipPref = intent?.getBooleanExtra(EXTRA_SHOW_CHIP, showChipPref) ?: showChipPref

        if (!viewAdded) {
            chipRetainedAfterManualCollapse = false
            chipPositionCustomizedThisSession = false
            panelPositionCustomizedThisSession = false
            displayMode = if (showChipPref) ClipboardFloatDisplayMode.Chip else ClipboardFloatDisplayMode.Expanded
            createAndAttachWindow(resolvedHost)
        } else if (shouldFollowIme() && !isDraggingWindow) {
            applyWindowGeometry(forceDefaultPosition = forceDefaultPositionForMode())
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
        rememberPosition = snapshot.clipboardFloatPanelPinPosition
        showChipPref = snapshot.clipboardFloatShowChip
        clickAction = snapshot.clipboardFloatEntryClickAction
        panelWidthDp = snapshot.clipboardFloatPanelWidthDp
        panelHeightDp = snapshot.clipboardFloatPanelHeightDp
        if (rememberPosition) {
            chipPosX = snapshot.clipboardFloatChipX
            chipPosY = snapshot.clipboardFloatChipY
            panelPosX = snapshot.clipboardFloatPanelX
            panelPosY = snapshot.clipboardFloatPanelY
        } else {
            chipPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
            chipPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
            panelPosX = ClipboardFloatWindowMetrics.UNSET_POSITION
            panelPosY = ClipboardFloatWindowMetrics.UNSET_POSITION
        }
    }

    private fun hasSavedChipPosition(): Boolean =
        chipPosX != ClipboardFloatWindowMetrics.UNSET_POSITION &&
            chipPosY != ClipboardFloatWindowMetrics.UNSET_POSITION

    private fun hasSavedPanelPosition(): Boolean =
        panelPosX != ClipboardFloatWindowMetrics.UNSET_POSITION &&
            panelPosY != ClipboardFloatWindowMetrics.UNSET_POSITION

    private fun shouldUseRememberedChipPosition(): Boolean =
        rememberPosition && hasSavedChipPosition()

    private fun shouldUseRememberedPanelPosition(): Boolean =
        rememberPosition && hasSavedPanelPosition()

    private fun usesCustomChipPosition(): Boolean =
        chipRetainedAfterManualCollapse ||
            chipPositionCustomizedThisSession ||
            shouldUseRememberedChipPosition()

    private fun usesCustomPanelPosition(): Boolean =
        panelPositionCustomizedThisSession || shouldUseRememberedPanelPosition()

    private fun shouldUseDefaultChipPosition(forceDefaultPosition: Boolean): Boolean =
        forceDefaultPosition || !usesCustomChipPosition()

    private fun shouldUseDefaultPanelPosition(forceDefaultPosition: Boolean): Boolean =
        forceDefaultPosition || !usesCustomPanelPosition()

    private fun shouldStayVisibleWithoutIme(): Boolean =
        (displayMode == ClipboardFloatDisplayMode.Expanded &&
            (panelPinned || searchActive || expandedRetainedWithoutIme)) ||
            (displayMode == ClipboardFloatDisplayMode.Chip && chipRetainedAfterManualCollapse)

    private fun shouldFollowIme(): Boolean {
        if (searchActive) return false
        return when (displayMode) {
            ClipboardFloatDisplayMode.Chip -> !usesCustomChipPosition()
            ClipboardFloatDisplayMode.Expanded -> !usesCustomPanelPosition()
        }
    }

    private fun forceDefaultPositionForMode(): Boolean = when (displayMode) {
        ClipboardFloatDisplayMode.Chip -> shouldUseDefaultChipPosition(forceDefaultPosition = false)
        ClipboardFloatDisplayMode.Expanded -> shouldUseDefaultPanelPosition(forceDefaultPosition = false)
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
                    onOpenStashPanel = ::openStashPanel,
                    onCollapse = ::collapseWindow,
                    onClose = ::closeWindow,
                    onDragWindow = ::onDragWindow,
                    onDragWindowStart = ::onDragWindowStart,
                    onDragWindowEnd = ::onDragWindowEnd,
                    onResizeWindow = ::onResizeWindow,
                    onSearchActiveChanged = ::onSearchActiveChanged,
                    onEntryClick = ::onEntryClick,
                    onEntryLongClick = ::onEntryLongClick,
                )
            }
        }
        composeView = contentView
        applyWindowGeometry(forceDefaultPosition = forceDefaultPositionForMode())
        windowManager.addView(composeView, params)
        viewAdded = true
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    private fun applyWindowGeometry(
        forceDefaultPosition: Boolean,
    ) {
        if (isDraggingWindow) return
        if (!viewAdded && !::params.isInitialized) return
        val density = resources.displayMetrics.density
        val marginPx = (ClipboardFloatWindowMetrics.EDGE_MARGIN_DP * density).roundToInt()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        if (displayMode == ClipboardFloatDisplayMode.Chip) {
            params.width = (44f * density).roundToInt()
            params.height = (36f * density).roundToInt()
            if (shouldUseDefaultChipPosition(forceDefaultPosition)) {
                applyChipDefaultPosition(screenWidth, marginPx, density)
            } else {
                params.x = chipPosX
                params.y = chipPosY.coerceAtLeast(marginPx)
            }
        } else {
            params.width = (panelWidthDp * density).roundToInt()
            params.height = (panelHeightDp * density).roundToInt()
            if (shouldUseDefaultPanelPosition(forceDefaultPosition)) {
                applyPanelDefaultPosition(screenWidth, screenHeight, marginPx, density)
            } else {
                params.x = panelPosX
                params.y = panelPosY.coerceAtLeast(marginPx)
            }
        }

        if (viewAdded) {
            composeView?.let { windowManager.updateViewLayout(it, params) }
        }
    }

    private fun applyChipDefaultPosition(screenWidth: Int, marginPx: Int, density: Float) {
        val chipExtraPx = (ClipboardFloatWindowMetrics.CHIP_ABOVE_IME_EXTRA_DP * density).roundToInt()
        params.x = (screenWidth - params.width - marginPx).coerceAtLeast(marginPx)
        val y = imeTop - params.height - marginPx - chipExtraPx
        params.y = y.coerceAtLeast(marginPx)
    }

    private fun applyPanelDefaultPosition(
        screenWidth: Int,
        screenHeight: Int,
        marginPx: Int,
        density: Float,
    ) {
        val aboveCenterPx =
            (ClipboardFloatWindowMetrics.PANEL_DEFAULT_ABOVE_CENTER_DP * density).roundToInt()
        params.x = ((screenWidth - params.width) / 2).coerceAtLeast(marginPx)
        val y = (screenHeight - params.height) / 2 - aboveCenterPx
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
                chipPositionCustomizedThisSession = true
            }
            ClipboardFloatDisplayMode.Expanded -> {
                panelPosX = params.x
                panelPosY = params.y
                panelPositionCustomizedThisSession = true
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
        val chipX = if (rememberPosition) chipPosX else ClipboardFloatWindowMetrics.UNSET_POSITION
        val chipY = if (rememberPosition) chipPosY else ClipboardFloatWindowMetrics.UNSET_POSITION
        val chipFollow = !rememberPosition
        val panelX = if (rememberPosition) panelPosX else ClipboardFloatWindowMetrics.UNSET_POSITION
        val panelY = if (rememberPosition) panelPosY else ClipboardFloatWindowMetrics.UNSET_POSITION
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
        expandedRetainedWithoutIme = false
        displayMode = ClipboardFloatDisplayMode.Expanded
        applyWindowGeometry(forceDefaultPosition = !shouldUseRememberedPanelPosition())
    }

    private fun collapseWindow() {
        collapseToChip(
            retainWhenKeyboardHides = true,
            anchorChipToPanelPosition = true,
        )
    }

    private fun collapseAfterEntryAction() {
        collapseToChip(
            retainWhenKeyboardHides = false,
            anchorChipToPanelPosition = false,
        )
    }

    private fun collapseToChip(
        retainWhenKeyboardHides: Boolean,
        anchorChipToPanelPosition: Boolean,
    ) {
        if (!showChipPref) {
            closeWindow()
            return
        }
        expandedRetainedWithoutIme = false
        clearSearchState()
        chipRetainedAfterManualCollapse = retainWhenKeyboardHides
        if (anchorChipToPanelPosition) {
            chipPosX = params.x
            chipPosY = params.y
            chipPositionCustomizedThisSession = true
        }
        displayMode = ClipboardFloatDisplayMode.Chip
        applyWindowGeometry(
            forceDefaultPosition = !anchorChipToPanelPosition && !shouldUseRememberedChipPosition(),
        )
    }

    private fun clearSearchState() {
        searchActive = false
        listController.setSearchQuery("")
        if (viewAdded) {
            updateWindowFocusForSearch(false)
        }
    }

    private fun closeWindow() {
        hideWindow()
    }

    private fun hideWindow() {
        chipRetainedAfterManualCollapse = false
        expandedRetainedWithoutIme = false
        clearSearchState()
        persistGeometryOnClose()
        if (viewAdded) {
            composeView?.let { runCatching { windowManager.removeView(it) } }
            viewAdded = false
        }
        stopSelf()
    }

    private fun togglePin() {
        panelPinned = !panelPinned
    }

    private fun openStashPanel() {
        val host = hostContext ?: return
        val query = listController.searchQuery.value.trim().takeIf { searchActive && it.isNotEmpty() }
        FloatBallStashPanel.show(
            context = host,
            initialTab = StashPanelInitialTab.Clipboard,
            searchQuery = query,
        )
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
            return
        }
        if (!panelPinned) {
            collapseAfterEntryAction()
        }
    }

    private fun onEntryLongClick(entry: com.slideindex.app.clipboard.ClipboardEntry) {
        ClipboardWriter.write(this, entry)
        Toast.makeText(this, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
    }

    private fun onSearchActiveChanged(active: Boolean) {
        if (searchActive == active) return
        searchActive = active
        if (!active && displayMode == ClipboardFloatDisplayMode.Expanded) {
            expandedRetainedWithoutIme = true
        }
        if (!viewAdded) return
        updateWindowFocusForSearch(active)
    }

    private fun updateWindowFocusForSearch(active: Boolean) {
        params.flags = if (active) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        }
        composeView?.let { view ->
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            windowManager.updateViewLayout(view, params)
        }
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
