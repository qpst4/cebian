package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.overlay.layout.GridLayoutInfo
import com.slideindex.app.overlay.layout.OverlayPanelLayoutHost
import com.slideindex.app.overlay.layout.QuickLauncherPanelLayoutEngine
import com.slideindex.app.overlay.layout.visualColumn
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.service.CreateShortcutTrampoline
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedLaunchPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class QuickLauncherOverlayController(
    internal val host: Host,
) {
    private val motionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val quickLauncherPageSnapMotion = OverlayFloatSpringMotion(motionScope)
    interface Host : OverlayPanelLayoutHost {
        val context: Context
        fun settings(): AppSettings
        override fun side(): PanelSide
        fun apps(): List<AppInfo>
        fun gestureSession(): GestureSession
        fun zoneLayout(): GestureZoneLayout
        fun pathRecognizer(): SwipePathRecognizer
        fun actionExecutor(): ActionExecutor
        fun panelGridSession(): PanelGridSession
        fun panelEnterProgress(): Float
        fun panelEnterAdjustedX(localX: Float, panel: RectF): Float
        fun panelEnterOffsetX(panel: RectF): Float
        fun panelContentRect(): RectF
        fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit)
        override fun activeTriggerZoneRect(): RectF
        override fun viewWidth(): Int
        override fun viewHeight(): Int
        override fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun viewLocationOnScreen(): IntArray
        fun overlayView(): android.view.View? = null
        fun invalidate()
        fun invalidatePartial(left: Int, top: Int, right: Int, bottom: Int)
        fun post(action: () -> Unit)
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
        fun hapticTick()
        fun hapticLongThreshold()
        fun hapticConfirmLaunch()
        fun startPanelExitAnimation(onEnd: () -> Unit)
        fun notifyPresentationTouchRequirementChanged()
        fun onQuickLauncherPanelItemsPersist(panelId: String, items: List<QuickLauncherItem>)
        fun onOverlayWindowSuspend()
        fun onOverlayWindowResume()
        fun clearEdgeCaptureTouchActive()
        fun onInitiatingEdgeGestureReleased()
        fun setPanelPresentationFocus(needsFocus: Boolean)
    }

    private val renderer = QuickLauncherRenderer(this)
    private val touchHandler = QuickLauncherTouchHandler(this)

    internal val quickLauncherOverlayDialogHost = OverlayComposeDialogHost(
        context = host.context,
        themeSettings = { host.settings() },
    )
    internal val quickLauncherPanelController = QuickLauncherPanelController(
        object : QuickLauncherPanelController.Host {
            override val context: Context get() = host.context
            override fun settings(): AppSettings = host.settings()
            override fun activeQuickLauncherPanel(): QuickLauncherPanel = this@QuickLauncherOverlayController.activeQuickLauncherPanel()
            override fun side(): PanelSide = host.side()
            override fun apps(): List<AppInfo> = host.apps()
            override fun isPanelReady(): Boolean = host.panelEnterProgress() >= 1f
            override fun isAddDialogShowing(): Boolean =
                QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing
            override fun dp(value: Float): Float = host.dp(value)
            override fun sp(value: Float): Float = host.sp(value)
            override fun invalidate() = host.invalidate()
            override fun hapticTick() = host.hapticTick()
            override fun showAddDialog(
                configuredAppPackages: Set<String>,
                configuredShortcutKeys: Set<String>,
                configuredActionKeys: Set<String>,
                onAdd: (QuickLauncherItem) -> Unit,
                onRemove: (QuickLauncherItem) -> Unit,
            ) {
                val appDeps = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    host.context.applicationContext,
                    com.slideindex.app.di.AppGraphEntryPoint::class.java,
                ).dependencies()

                quickLauncherOverlayDialogHost.show(
                    onBackPressed = {
                        quickLauncherOverlayDialogHost.dismiss()
                        true
                    },
                    onDismiss = {
                        host.notifyPresentationTouchRequirementChanged()
                        host.invalidate()
                    },
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.slideindex.app.ui.compose.LocalAppDependencies provides appDeps,
                    ) {
                        com.slideindex.app.ui.QuickLauncherAddOverlaySheet(
                            panelSide = host.side(),
                            apps = host.apps(),
                            configuredAppPackages = configuredAppPackages,
                            configuredShortcutKeys = configuredShortcutKeys,
                            configuredActionKeys = configuredActionKeys,
                            activityShortcuts = host.settings().activityShortcuts,
                            shellCommands = host.settings().shellCommands,
                            onDismiss = {
                                quickLauncherOverlayDialogHost.dismiss()
                                host.notifyPresentationTouchRequirementChanged()
                            },
                            onAdd = onAdd,
                            onRemove = onRemove,
                            launchCreateShortcut = { createHost, onResult ->
                                CreateShortcutTrampoline.launch(
                                    context = host.context,
                                    host = createHost,
                                    onPrepare = {},
                                    onResult = onResult,
                                )
                            },
                        )
                    }
                }
                host.notifyPresentationTouchRequirementChanged()
            }
            override fun onPersist(items: List<QuickLauncherItem>) {
                invalidateQuickLauncherDerivedCaches()
                val panelId = host.gestureSession().quickLauncherPanelId()
                    .ifBlank { activeQuickLauncherPanel().id }
                host.onQuickLauncherPanelItemsPersist(panelId, items)
            }
            override fun isQuickLauncherVisible(): Boolean =
                host.gestureSession().panelMode() == OverlayPanelMode.QUICK_LAUNCHER
            override fun quickLauncherPageSize(): Int =
                this@QuickLauncherOverlayController.quickLauncherPageSize()
            override fun onEditDragMove(touchX: Float, localY: Float, panelRect: RectF) {
                touchHandler.applyEditDragAutoPage(touchX, panelRect)
            }
            override fun onEditDragBegan() {
                quickLauncherEdgeAutoPageSeeded = false
                quickLauncherEdgePageZone = 0
            }
            override fun resolveEditDragTargetGlobal(
                touchX: Float,
                localY: Float,
                panelRect: RectF,
            ): Int = quickLauncherGlobalIndexAt(touchX, localY, panelRect)
            override fun postDelayed(runnable: Runnable, delayMs: Long) =
                host.postDelayed(runnable, delayMs)
            override fun removeCallbacks(runnable: Runnable) =
                host.removeCallbacks(runnable)
            override fun switchToNextPanel() =
                this@QuickLauncherOverlayController.switchToNextPanel()
            override fun hasMultiplePanels(): Boolean =
                this@QuickLauncherOverlayController.hasMultiplePanels()
            override fun currentPanelName(): String =
                this@QuickLauncherOverlayController.currentPanelName()
        },
    )

    internal var quickLauncherAnchorRawY: Float? = null
    internal var quickLauncherFrozenAnchorLocalY: Float? = null
    internal var quickLauncherContinuousHapticIndex = -1
    internal var quickLauncherPressIndex = -1
    internal var quickLauncherPressDownTime = 0L
    internal var quickLauncherLongPressArmed = false
    internal var quickLauncherLongPressIndex = -1
    internal var quickLauncherLongPressRunnable: Runnable? = null
    internal var quickLauncherPageIndex = 0
    internal var quickLauncherPageCount = 1
    internal var quickLauncherPageSwipeStartX = 0f
    internal var quickLauncherPageSwipeStartY = 0f
    internal var quickLauncherPageSwipeTracking = false
    internal var quickLauncherPageSwipeLocked = false
    internal var quickLauncherPageChangedThisGesture = false
    internal var quickLauncherPageDragOffset = 0f
    internal var quickLauncherLaunchEndDeferMs = 0L
    internal var quickLauncherExiting = false
    internal var quickLauncherOpeningGestureActive = false
    internal var quickLauncherToolbarTouchActive = false
    /** -1 = outer edge, 0 = middle, 1 = inner edge; used for continuous edge auto-page. */
    internal var quickLauncherEdgePageZone = 0
    internal var quickLauncherEdgeAutoPageSeeded = false
    internal var quickLauncherAppsByPackage: Map<String, AppInfo> = emptyMap()
    internal val quickLauncherIconCache = mutableMapOf<String, Bitmap>()
    internal val quickLauncherLabelCache = mutableMapOf<String, String>()
    internal var quickLauncherCachedPages: List<List<QuickLauncherItem>>? = null
    internal var quickLauncherCachedPagesKey: Int = 0
    internal var quickLauncherLayoutPanelWidth: Float = 0f

    // Folder Sub-Panel State
    internal var folderOpen: Boolean = false
    internal var folderGestureActive: Boolean = false
    internal var folderGlobalIndex: Int = -1
    internal var folderItem: QuickLauncherItem? = null
    internal var folderSubPanelItems: List<QuickLauncherItem> = emptyList()
    internal var folderHighlightLocalIndex: Int = -1
    internal val folderRect = RectF()
    internal val folderCellBounds = mutableListOf<Pair<QuickLauncherItem, RectF>>()
    internal val folderCloseButtonBounds = RectF()
    internal val folderAddButtonBounds = RectF()
    internal var folderHoverRunnable: Runnable? = null
    internal var folderPressLocalIndex: Int = -1
    internal var folderPressDownTime: Long = 0L
    internal var folderLongPressArmed: Boolean = false
    internal var folderLongPressRunnable: Runnable? = null

    fun openFolder(globalIndex: Int) {
        val items = quickLauncherRootItems()
        val item = items.getOrNull(globalIndex) ?: return
        if (item.type != QuickLauncherItemType.FOLDER) return
        folderGlobalIndex = globalIndex
        folderItem = item
        folderSubPanelItems = item.folderItems()
        folderOpen = true
        folderHighlightLocalIndex = -1
        cancelFolderHover()
        cancelFolderLongPress()
        host.hapticTick()
        host.invalidate()
    }

    fun closeFolder() {
        if (!folderOpen) return
        folderOpen = false
        folderGlobalIndex = -1
        folderItem = null
        folderSubPanelItems = emptyList()
        folderHighlightLocalIndex = -1
        folderCellBounds.clear()
        folderCloseButtonBounds.setEmpty()
        folderAddButtonBounds.setEmpty()
        cancelFolderHover()
        cancelFolderLongPress()
        host.invalidate()
    }

    fun cancelFolderHover() {
        folderHoverRunnable?.let { host.removeCallbacks(it) }
        folderHoverRunnable = null
    }

    fun cancelFolderLongPress() {
        folderLongPressRunnable?.let { host.removeCallbacks(it) }
        folderLongPressRunnable = null
        folderLongPressArmed = false
        folderPressLocalIndex = -1
        folderPressDownTime = 0L
    }

    fun scheduleFolderLongPress(localIndex: Int, eventTime: Long) {
        cancelFolderLongPress()
        val item = folderSubPanelItems.getOrNull(localIndex) ?: return
        if (item.type == QuickLauncherItemType.ACTION || item.type == QuickLauncherItemType.FOLDER) return
        if (!host.settings().freeWindowEnabled || !host.settings().resolvedLaunchPolicy().usesLongPress()) return
        folderPressLocalIndex = localIndex
        folderPressDownTime = eventTime
        val runnable = Runnable {
            if (folderOpen && folderHighlightLocalIndex == localIndex) {
                folderLongPressArmed = true
                host.hapticLongThreshold()
                host.invalidate()
            }
        }
        folderLongPressRunnable = runnable
        host.postDelayed(runnable, host.settings().effectiveLongPressDurationMs().toLong())
    }

    fun isFolderLongPressTriggered(event: MotionEvent): Boolean {
        if (folderLongPressArmed) return true
        val item = folderSubPanelItems.getOrNull(folderHighlightLocalIndex) ?: return false
        if (item.type == QuickLauncherItemType.ACTION || item.type == QuickLauncherItemType.FOLDER) return false
        if (!host.settings().freeWindowEnabled || !host.settings().resolvedLaunchPolicy().usesLongPress()) return false
        if (folderPressLocalIndex < 0 || folderPressLocalIndex != folderHighlightLocalIndex) return false
        return event.eventTime - folderPressDownTime >= host.settings().effectiveLongPressDurationMs()
    }

    fun updateActiveFolderChildren(newChildren: List<QuickLauncherItem>) {
        val globalIdx = folderGlobalIndex
        if (globalIdx < 0) return
        quickLauncherPanelController.updateFolderItems(globalIdx, newChildren)
        folderItem = quickLauncherRootItems().getOrNull(globalIdx)
        folderSubPanelItems = newChildren
        invalidateQuickLauncherDerivedCaches()
        host.invalidate()
    }

    internal val quickLauncherCellHeight get() = host.dp(64f)
    internal val quickLauncherCellWidth get() = host.dp(56f)
    internal val quickLauncherGridPadding get() = host.dp(8f)
    internal val quickLauncherHeaderHeight get() = 0f

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean =
        touchHandler.handleTouch(event, localX, localY)

    fun draw(canvas: Canvas, drawToolbar: Boolean = true) =
        renderer.draw(canvas, drawToolbar)

    fun panelRect(): RectF = quickLauncherPanelRect()

    fun enterContentRect(): RectF {
        val panelRect = quickLauncherPanelRect()
        return quickLauncherPanelController.combinedContentRect(panelRect)
    }

    fun isExiting(): Boolean = quickLauncherExiting

    fun isOverlayDialogShowing(): Boolean =
        QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing

    fun isComposeOverlayDialogShowing(): Boolean = quickLauncherOverlayDialogHost.isShowing

    fun syncOverlayDialogZOrder() {
        if (quickLauncherOverlayDialogHost.isShowing) {
            quickLauncherOverlayDialogHost.bringToFront()
        }
    }

    fun syncSettings(settings: AppSettings) {
        quickLauncherPanelController.syncSettings(settings)
        renderer.syncSettings(settings)
        invalidateQuickLauncherDerivedCaches()
    }

    fun setApps(apps: List<AppInfo>) {
        rebuildQuickLauncherAppsByPackage(apps)
        invalidateQuickLauncherDerivedCaches()
    }

    fun invalidateDerivedCaches() {
        invalidateQuickLauncherDerivedCaches()
    }

    fun onSizeChanged() {
        quickLauncherLayoutPanelWidth = 0f
    }

    fun setAnchorRawY(rawY: Float?) {
        quickLauncherAnchorRawY = rawY
    }

    fun onSessionStart() {
        quickLauncherPageIndex = 0
        quickLauncherPageCount = 1
        quickLauncherPageSwipeTracking = false
        quickLauncherPageSwipeLocked = false
        quickLauncherPageChangedThisGesture = false
        quickLauncherPageSnapMotion.cancel()
        quickLauncherPageDragOffset = 0f
        quickLauncherExiting = false
        quickLauncherOpeningGestureActive = true
        quickLauncherEdgePageZone = 0
        quickLauncherEdgeAutoPageSeeded = false
        quickLauncherPanelController.ensureDefaultsPersisted(host.settings())
        renderer.warmCaches()
        quickLauncherAnchorRawY = quickLauncherAnchorRawY
            ?.takeIf { it > 0f }
            ?: host.pathRecognizer().lastRawY().takeIf { it > 0f }
            ?: host.pathRecognizer().gestureStartRawY().takeIf { it > 0f }
    }

    fun onLayoutReady() {
        if (!host.gestureSession().quickLauncherContinuousPickActive()) {
            quickLauncherFrozenAnchorLocalY = resolveQuickLauncherAnchorLocalY()
        }
    }

    fun onSessionEnd() {
        quickLauncherAnchorRawY = null
        quickLauncherFrozenAnchorLocalY = null
        quickLauncherContinuousHapticIndex = -1
        quickLauncherPressIndex = -1
        quickLauncherPressDownTime = 0L
        quickLauncherPageIndex = 0
        quickLauncherPageCount = 1
        quickLauncherPageSwipeTracking = false
        quickLauncherPageSwipeLocked = false
        quickLauncherPageChangedThisGesture = false
        quickLauncherPageSnapMotion.cancel()
        quickLauncherPageDragOffset = 0f
        quickLauncherLaunchEndDeferMs = 0L
        quickLauncherOpeningGestureActive = false
        quickLauncherEdgePageZone = 0
        quickLauncherEdgeAutoPageSeeded = false
        quickLauncherExiting = false
        quickLauncherLongPressRunnable?.let { host.removeCallbacks(it) }
        quickLauncherLongPressRunnable = null
        quickLauncherLongPressIndex = -1
        quickLauncherLongPressArmed = false
        closeFolder()
        quickLauncherPanelController.reset()
        quickLauncherOverlayDialogHost.dismiss()
        activePanelId = null
        host.setPanelPresentationFocus(false)
        invalidateQuickLauncherDerivedCaches()
    }

    fun onPanelEnterAnimationEnded() {
        host.notifyPresentationTouchRequirementChanged()
        if (!host.gestureSession().isMoveTimeActionLocked() ||
            host.gestureSession().quickLauncherContinuousPickActive()
        ) {
            quickLauncherOpeningGestureActive = false
        }
        // leave-open 面板需要可聚焦，否则 Back 会落到下层 Activity（尤其 cebian 自身）。
        if (!host.gestureSession().quickLauncherContinuousPickActive() &&
            !host.gestureSession().isMoveTimeActionLocked()
        ) {
            host.setPanelPresentationFocus(true)
        }
    }

    fun handleBackPress(): Boolean {
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) return false
        if (quickLauncherOverlayDialogHost.isShowing) {
            quickLauncherOverlayDialogHost.dismiss()
            return true
        }
        if (QuickLauncherAddTrampoline.isActive()) return false
        if (folderOpen) {
            closeFolder()
            return true
        }
        if (quickLauncherPanelController.editMode) {
            quickLauncherPanelController.setEditMode(false)
            host.invalidate()
            return true
        }
        dismissFromBack()
        return true
    }

    private fun dismissFromBack() {
        if (quickLauncherExiting) return
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.gestureSession().endSession()
            return
        }
        quickLauncherExiting = true
        host.setPanelPresentationFocus(false)
        host.notifyPresentationTouchRequirementChanged()
        host.startPanelExitAnimation {
            quickLauncherExiting = false
            host.gestureSession().endSession()
        }
    }

    private var activePanelId: String? = null

    internal fun activeQuickLauncherPanel(): QuickLauncherPanel {
        val panelId = host.gestureSession().quickLauncherPanelId()
        if (panelId != activePanelId) {
            activePanelId = panelId
            invalidateQuickLauncherDerivedCaches()
            quickLauncherPageIndex = 0
            quickLauncherPanelController.onActivePanelChanged()
        }
        return QuickLauncherPanelDefaults.resolvePanel(host.settings().quickLauncherPanels, panelId)
    }

    fun hasMultiplePanels(): Boolean =
        QuickLauncherPanelDefaults.effectivePanels(host.settings().quickLauncherPanels).size > 1

    fun currentPanelName(): String =
        activeQuickLauncherPanel().name.ifBlank {
            val panels = QuickLauncherPanelDefaults.effectivePanels(host.settings().quickLauncherPanels)
            val idx = panels.indexOfFirst { it.id == activeQuickLauncherPanel().id }
            if (idx >= 0) "面板 ${idx + 1}" else "快速启动器"
        }

    fun switchToNextPanel() {
        val panels = QuickLauncherPanelDefaults.effectivePanels(host.settings().quickLauncherPanels)
        if (panels.size <= 1) return
        val currentId = activeQuickLauncherPanel().id
        val currentIndex = panels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val nextPanel = panels[(currentIndex + 1) % panels.size]
        host.gestureSession().setQuickLauncherPanelId(nextPanel.id)
        activePanelId = nextPanel.id
        closeFolder()
        invalidateQuickLauncherDerivedCaches()
        quickLauncherPageIndex = 0
        quickLauncherPanelController.onActivePanelChanged()
        host.hapticTick()
        host.invalidate()
    }

    internal fun quickLauncherColumnsPerPage(): Int =
        activeQuickLauncherPanel().columnsPerPage.coerceIn(2, 5)

    internal fun quickLauncherRowsPerPage(): Int =
        activeQuickLauncherPanel().rowsPerPage.coerceIn(2, QuickLauncherPanelLayoutEngine.MAX_ROWS)

    internal fun quickLauncherPageSize(): Int =
        quickLauncherColumnsPerPage() * quickLauncherRowsPerPage()

    internal fun quickLauncherRootItems(): List<QuickLauncherItem> =
        quickLauncherPanelController.displayItems(host.settings(), activeQuickLauncherPanel())

    internal fun quickLauncherItemCacheKey(item: QuickLauncherItem): String =
        "${item.type.id}\u0000${item.payload}"

    internal fun rebuildQuickLauncherAppsByPackage(apps: List<AppInfo> = host.apps()) {
        quickLauncherAppsByPackage = apps.associateBy { it.packageName }
    }

    internal fun invalidateQuickLauncherDerivedCaches() {
        quickLauncherIconCache.clear()
        quickLauncherLabelCache.clear()
        quickLauncherCachedPages = null
        quickLauncherCachedPagesKey = 0
        quickLauncherLayoutPanelWidth = 0f
    }

    internal fun quickLauncherPages(): List<List<QuickLauncherItem>> {
        val panel = activeQuickLauncherPanel()
        val root = quickLauncherRootItems()
        val pageSize = quickLauncherPageSize()
        val columns = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val key = QuickLauncherGridLogic.pagesCacheKey(panel.id, root.size, root.hashCode(), pageSize, columns, rows)
        quickLauncherCachedPages?.let { cached ->
            if (key == quickLauncherCachedPagesKey) return cached
        }
        val pages = if (root.isEmpty()) {
            listOf(emptyList())
        } else {
            root.chunked(pageSize)
        }
        quickLauncherPageCount = pages.size
        quickLauncherPageIndex = quickLauncherPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        quickLauncherCachedPages = pages
        quickLauncherCachedPagesKey = key
        return pages
    }

    internal fun quickLauncherPagination(): Triple<Int, Int, Int> {
        val pages = quickLauncherPages()
        val pageSize = quickLauncherPageSize()
        val pageCount = pages.size
        val pageStart = quickLauncherPageIndex * pageSize
        return Triple(pageStart, pageSize, pageCount)
    }

    internal fun quickLauncherItemsForPage(pageIndex: Int): List<QuickLauncherItem> {
        val pages = quickLauncherPages()
        val clampedPage = pageIndex.coerceIn(0, pages.size - 1)
        val pageStart = clampedPage * quickLauncherPageSize()
        if (clampedPage == quickLauncherPageIndex) {
            quickLauncherPanelController.setItemPageOffset(pageStart)
        }
        return pages.getOrElse(clampedPage) { emptyList() }
    }

    internal fun quickLauncherPanelWidthForPaging(): Float {
        if (quickLauncherLayoutPanelWidth > 0f) return quickLauncherLayoutPanelWidth
        return quickLauncherPanelRect().width().coerceAtLeast(1f).also {
            quickLauncherLayoutPanelWidth = it
        }
    }

    internal fun invalidateQuickLauncherPanel() {
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.invalidate()
            return
        }
        val panelRect = quickLauncherPanelRect()
        if (panelRect.isEmpty) {
            host.invalidate()
            return
        }
        val dirty = quickLauncherPanelController.combinedContentRect(panelRect)
        val offsetX = host.panelEnterOffsetX(dirty)
        val pad = host.dp(2f).toInt()
        host.invalidatePartial(
            (dirty.left + offsetX).toInt() - pad,
            dirty.top.toInt() - pad,
            (dirty.right + offsetX).toInt() + pad,
            dirty.bottom.toInt() + pad,
        )
    }

    internal fun quickLauncherPagingActiveForHitTest(): Boolean =
        quickLauncherPageSwipeLocked ||
            quickLauncherPageSnapMotion.isRunning ||
            kotlin.math.abs(quickLauncherPageDragOffset) > host.dp(0.5f)

    internal fun quickLauncherGlobalIndexAt(touchX: Float, localY: Float, panelRect: RectF): Int {
        val pageSize = quickLauncherPageSize().coerceAtLeast(1)
        val panelWidth = panelRect.width().coerceAtLeast(1f)
        val offset = quickLauncherPageDragOffset
        val pagingActive = quickLauncherPagingActiveForHitTest()

        val pageIdx: Int
        val xInPage: Float
        if (pagingActive && quickLauncherPageCount > 1) {
            val relativeX = touchX - panelRect.left - offset
            pageIdx = (relativeX / panelWidth).toInt().coerceIn(0, quickLauncherPageCount - 1)
            xInPage = panelRect.left + relativeX - pageIdx * panelWidth
        } else {
            pageIdx = quickLauncherPageIndex.coerceIn(0, quickLauncherPageCount - 1)
            xInPage = touchX
        }

        val pageStart = pageIdx * pageSize
        val localIndex = quickLauncherLocalCellIndexAt(
            xInPage = xInPage,
            localY = localY,
            panelRect = panelRect,
            maxSlotIndex = pageSize - 1,
        )
        return QuickLauncherGridLogic.dragSlotGlobal(pageStart, localIndex, pageSize)
    }

    internal fun quickLauncherLocalCellIndexAt(
        xInPage: Float,
        localY: Float,
        panelRect: RectF,
        maxSlotIndex: Int,
    ): Int {
        if (maxSlotIndex < 0) return 0
        val columns = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val visualCol = ((xInPage - panelRect.left - quickLauncherGridPadding) / quickLauncherCellWidth)
            .toInt()
            .coerceIn(0, columns - 1)
        val row = ((localY - panelRect.top - quickLauncherHeaderHeight - quickLauncherGridPadding) /
            quickLauncherCellHeight)
            .toInt()
            .coerceIn(0, rows - 1)
        val colInRow = when (host.side()) {
            com.slideindex.app.overlay.PanelSide.RIGHT -> columns - 1 - visualCol
            else -> visualCol
        }
        return (row * columns + colInRow).coerceIn(0, maxSlotIndex)
    }

    internal fun quickLauncherPanelRect(): RectF {
        quickLauncherPagination()
        return QuickLauncherPanelLayoutEngine.panelRect(
            host = host,
            columnsPerPage = quickLauncherColumnsPerPage(),
            rowsPerPage = quickLauncherRowsPerPage(),
            cellWidth = quickLauncherCellWidth,
            cellHeight = quickLauncherCellHeight,
            gridPadding = quickLauncherGridPadding,
            headerHeight = quickLauncherHeaderHeight,
            anchorLocalY = quickLauncherAnchorLocalY(),
            toolbarReserveWidth = quickLauncherPanelController.contentReserveWidth(host.settings()),
        )
    }

    private fun resolveQuickLauncherAnchorLocalY(): Float {
        val rawY = quickLauncherAnchorRawY ?: host.pathRecognizer().gestureStartRawY()
        val loc = host.viewLocationOnScreen()
        val anchorY = rawY - loc[1]
        val trigger = host.activeTriggerZoneRect()
        return anchorY.coerceIn(trigger.top, trigger.bottom)
    }

    private fun quickLauncherAnchorLocalY(): Float =
        quickLauncherFrozenAnchorLocalY ?: resolveQuickLauncherAnchorLocalY()

    private fun quickLauncherGridLayoutInfo(): GridLayoutInfo =
        QuickLauncherPanelLayoutEngine.gridLayoutInfo(
            columnsPerPage = quickLauncherColumnsPerPage(),
            rowsPerPage = quickLauncherRowsPerPage(),
            cellWidth = quickLauncherCellWidth,
            gridPadding = quickLauncherGridPadding,
        )

    private fun quickLauncherPanelContentHeight(rows: Int): Float =
        QuickLauncherPanelLayoutEngine.contentHeight(
            rows,
            quickLauncherCellHeight,
            quickLauncherGridPadding,
            quickLauncherHeaderHeight,
        )

    private fun anchoredQuickLauncherPanelRect(panelWidth: Float, rows: Int): RectF =
        QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = panelWidth,
            contentHeight = quickLauncherPanelContentHeight(rows),
            anchorLocalY = quickLauncherAnchorLocalY(),
        )

    private fun offsetQuickLauncherPanelForToolbar(panelRect: RectF): RectF =
        QuickLauncherPanelLayoutEngine.offsetForToolbar(
            host = host,
            panelRect = panelRect,
            reserveWidth = quickLauncherPanelController.contentReserveWidth(host.settings()),
        )

    internal fun collectToolbarAccessibilityNodes(
        context: android.content.Context,
        panelRect: RectF,
    ): List<OverlayVirtualNode> =
        quickLauncherPanelController.collectAccessibilityNodes(context, panelRect)
}
