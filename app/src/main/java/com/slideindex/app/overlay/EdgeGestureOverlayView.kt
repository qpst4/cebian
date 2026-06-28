package com.slideindex.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.IndexSessionHost
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TaskSwitcherLockStore
import com.slideindex.app.util.TaskSwitcherMenuActions
import com.slideindex.app.util.coerceSafe
import kotlin.math.ceil

/**
 * 边缘手势 Overlay：识别层（GestureSession）+ 索引 UI（Canvas 绘制）。
 */
class EdgeGestureOverlayView(
    context: Context,
    private val side: PanelSide,
    private val appRepository: AppRepository,
    private val onSessionStartCallback: () -> Unit,
    private val onSessionEndCallback: () -> Unit,
    private val onClickPassthroughCallback: (Float, Float, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
) : View(context), IndexSessionHost, GestureSession.Callbacks {

    private var settings = AppSettings()
    private var apps: List<AppInfo> = emptyList()
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY

    private val zoneLayout = GestureZoneLayout(side)
    private val indexSession = SlideAlongRailSession(side, zoneLayout, this)
    private val panelGridSession = PanelGridSession()
    private val actionExecutor = ActionExecutor(
        context = context,
        appRepository = appRepository,
        clickPassthroughHandler = onClickPassthroughCallback,
    )
    private val pathRecognizer = SwipePathRecognizer(side, resources.displayMetrics.density)
    private val gestureSession = GestureSession(
        side = side,
        zoneLayout = zoneLayout,
        indexSession = indexSession,
        pathRecognizer = pathRecognizer,
        actionExecutor = actionExecutor,
        callbacks = this,
    )

    private var recentApps = mutableListOf<RecentAppEntry>()
    private var panelContentRect = RectF()
    private var taskSwitcherLayout: TaskSwitcherPanelLayout? = null
    private var taskSwitcherRowHighlight = -1
    private var taskSwitcherCloseHighlight = -1
    private var taskSwitcherFreeWindowHighlight = -1
    private var taskSwitcherCloseAllHighlight = false
    private var taskSwitcherClosePressIndex = -1
    private var taskSwitcherClosePressDownTime = 0L
    private var taskSwitcherCloseLongPressTriggered = false
    private var taskSwitcherCloseLongPressRunnable: Runnable? = null
    private var taskSwitcherRowPressIndex = -1
    private var taskSwitcherRowPressDownTime = 0L
    private var taskSwitcherRowLongPressTriggered = false
    private var taskSwitcherRowLongPressRunnable: Runnable? = null
    private var taskSwitcherContextMenu: TaskSwitcherContextMenuLayout? = null
    private var taskSwitcherMenuHighlight = -1
    private var taskSwitcherShortcutLoadSeq = 0
    private var taskSwitcherLoadGeneration = 0
    private var taskSwitcherAnchorRawY: Float? = null
    private var taskSwitcherFrozenAnchorLocalY: Float? = null
    private var panelEnterProgress = 1f
    private var panelEnterAnimator: ValueAnimator? = null
    private var interceptTouchActive = false

    private val railLetters: List<Char> = ('A'..'Z').toList() + '#'
    private val iconCache = mutableMapOf<String, Bitmap>()

    private val railBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val bubbleLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }
    private val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
        color = Color.argb(230, 255, 255, 255)
    }
    private val cellHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellLongPressHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triggerPreviewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triggerPreviewStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val elevatedCardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val elevatedShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val tmpRect = RectF()

    private val cellHeight get() = dp(72f)
    private val cellWidth get() = dp(68f)
    private val gridIconSize get() = dp(44f)
    private val gridPadding get() = dp(10f)
    private val gridIconTopInset get() = dp(6f)
    private val gridIconLabelGap get() = dp(3f)
    private val gridCellInset get() = dp(4f)
    private val bubbleRadius get() = dp(24f)
    private val bubblePanelGap get() = dp(10f)
    private val railCorner get() = dp(14f)
    private val panelCorner get() = dp(18f)

    private data class GridLayoutInfo(
        val appsPerRow: Int,
        val panelColumns: Int,
        val rows: Int,
        val panelWidth: Float,
    )

    fun applySettings(newSettings: AppSettings, screenWidth: Int) {
        settings = newSettings
        gestureSession.applySettings(newSettings)
        cellHighlightPaint.color = Color.argb(70, 255, 255, 255)
        cellLongPressHighlightPaint.color = Color.argb(110, 66, 133, 244)
        syncZoneLayout()
        invalidate()
    }

    fun isSessionActive(): Boolean = gestureSession.isActive()

    fun isPreviewMode(): Boolean = previewMode

    fun setPreviewMode(enabled: Boolean, content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY) {
        val changed = previewMode != enabled || previewContent != content
        if (!changed) return
        previewMode = enabled
        previewContent = content
        syncZoneLayout()
        invalidate()
    }

    fun setApps(newApps: List<AppInfo>) {
        apps = newApps
        indexSession.setApps(newApps)
        iconCache.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        syncZoneLayout()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (previewMode) return false
        val (localX, localY) = rawToLocal(event.rawX, event.rawY)
        when (gestureSession.panelMode()) {
            OverlayPanelMode.QUICK_LAUNCHER ->
                return handleQuickLauncherTouch(event, localX, localY)
            OverlayPanelMode.TASK_SWITCHER ->
                return handleTaskSwitcherTouch(event, localX, localY)
            OverlayPanelMode.INDEX -> return handleIndexTouch(event, localX, localY)
            OverlayPanelMode.NONE -> Unit
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (gestureSession.isActive()) return true
                if (gestureSession.onTouchDown(event.rawX, event.rawY, localX, localY)) return true
                if (settings.interceptSystemBackGesture &&
                    zoneLayout.containsInterceptZone(localX, localY)
                ) {
                    interceptTouchActive = true
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (interceptTouchActive) return true
                if (!gestureSession.isActive()) return false
                gestureSession.onTouchMove(event.rawX, event.rawY, localX, localY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (interceptTouchActive) {
                    interceptTouchActive = false
                    return true
                }
                if (!gestureSession.isActive()) return false
                gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                return true
            }
        }
        return false
    }

    private fun handleIndexTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val touchX = panelEnterAdjustedX(localX, indexPanelContentRect())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInsideIndexInteractiveArea(touchX, localY)) {
                    gestureSession.endSession()
                    return false
                }
                indexSession.updateSelection(touchX, localY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                indexSession.updateSelection(touchX, localY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureSession.onTouchUp(event.rawX, event.rawY, localX, localY)
                return true
            }
        }
        return false
    }

    private fun isInsideIndexInteractiveArea(localX: Float, localY: Float): Boolean {
        if (zoneLayout.isInRailZone(localX)) return true
        if (indexSession.selectedLetter != null) {
            indexSession.gridCellBounds.forEach { (_, rect) ->
                if (rect.contains(localX, localY)) return true
            }
            val grid = gridPopupRect()
            if (grid.contains(localX, localY)) return true
        }
        return false
    }

    private fun handleQuickLauncherTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val panelRect = quickLauncherPanelRect()
        val touchX = panelEnterAdjustedX(localX, panelRect)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panelGridSession.updateHighlight(touchX, localY)
                if (panelGridSession.highlightedIndex < 0 && !panelContentRect.contains(touchX, localY)) {
                    gestureSession.endSession()
                } else if (panelGridSession.highlightedIndex >= 0) {
                    HapticHelper.appTick(this, settings)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val prev = panelGridSession.highlightedIndex
                panelGridSession.updateHighlight(touchX, localY)
                if (panelGridSession.highlightedIndex != prev && panelGridSession.highlightedIndex >= 0) {
                    HapticHelper.appTick(this, settings)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                panelGridSession.highlightedQuickItem()?.let { item ->
                    HapticHelper.confirmLaunch(this, settings)
                    actionExecutor.launchQuickItem(item, settings)
                }
                gestureSession.endSession()
                return true
            }
        }
        return false
    }

    private fun handleTaskSwitcherTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (taskSwitcherContextMenu != null) {
            return handleTaskSwitcherContextMenuTouch(event, localX, localY)
        }
        val layout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
        val touchX = panelEnterAdjustedX(localX, layout.panelRect)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                taskSwitcherRowHighlight = -1
                taskSwitcherCloseHighlight = -1
                taskSwitcherFreeWindowHighlight = -1
                taskSwitcherCloseAllHighlight = false
                if (!layout.panelRect.contains(touchX, localY)) {
                    gestureSession.endSession()
                    return true
                }
                layout.rows.forEachIndexed { index, row ->
                    if (row.closeRect.contains(touchX, localY)) {
                        taskSwitcherCloseHighlight = index
                        taskSwitcherClosePressIndex = index
                        taskSwitcherClosePressDownTime = event.eventTime
                        taskSwitcherCloseLongPressTriggered = false
                        scheduleTaskSwitcherCloseLongPress(index, row.entry.app.packageName)
                        HapticHelper.appTick(this, settings)
                        invalidate()
                        return true
                    }
                    if (row.freeWindowRect.contains(touchX, localY)) {
                        taskSwitcherFreeWindowHighlight = index
                        HapticHelper.appTick(this, settings)
                        invalidate()
                        return true
                    }
                    if (row.rowRect.contains(touchX, localY)) {
                        taskSwitcherRowHighlight = index
                        taskSwitcherRowPressIndex = index
                        taskSwitcherRowPressDownTime = event.eventTime
                        taskSwitcherRowLongPressTriggered = false
                        scheduleTaskSwitcherRowLongPress(index)
                        HapticHelper.appTick(this, settings)
                        invalidate()
                        return true
                    }
                }
                if (layout.closeAllRect.contains(touchX, localY)) {
                    taskSwitcherCloseAllHighlight = true
                    HapticHelper.appTick(this, settings)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (taskSwitcherClosePressIndex >= 0) {
                    val pressRow = layout.rows.getOrNull(taskSwitcherClosePressIndex)
                    if (pressRow != null && !pressRow.closeRect.contains(touchX, localY)) {
                        cancelTaskSwitcherCloseLongPress()
                        taskSwitcherClosePressIndex = -1
                    }
                }
                if (taskSwitcherRowPressIndex >= 0) {
                    val pressRow = layout.rows.getOrNull(taskSwitcherRowPressIndex)
                    if (pressRow != null && !pressRow.rowRect.contains(touchX, localY)) {
                        cancelTaskSwitcherRowLongPress()
                        taskSwitcherRowPressIndex = -1
                    }
                }
                val prevRow = taskSwitcherRowHighlight
                val prevClose = taskSwitcherCloseHighlight
                val prevFreeWindow = taskSwitcherFreeWindowHighlight
                val prevCloseAll = taskSwitcherCloseAllHighlight
                taskSwitcherRowHighlight = -1
                taskSwitcherCloseHighlight = -1
                taskSwitcherFreeWindowHighlight = -1
                taskSwitcherCloseAllHighlight = false
                layout.rows.forEachIndexed { index, row ->
                    when {
                        row.closeRect.contains(touchX, localY) -> taskSwitcherCloseHighlight = index
                        row.freeWindowRect.contains(touchX, localY) -> taskSwitcherFreeWindowHighlight = index
                        row.rowRect.contains(touchX, localY) -> taskSwitcherRowHighlight = index
                    }
                }
                if (layout.closeAllRect.contains(touchX, localY)) {
                    taskSwitcherCloseAllHighlight = true
                }
                if (taskSwitcherRowHighlight != prevRow ||
                    taskSwitcherCloseHighlight != prevClose ||
                    taskSwitcherFreeWindowHighlight != prevFreeWindow ||
                    taskSwitcherCloseAllHighlight != prevCloseAll
                ) {
                    if (taskSwitcherRowHighlight >= 0 ||
                        taskSwitcherCloseHighlight >= 0 ||
                        taskSwitcherFreeWindowHighlight >= 0 ||
                        taskSwitcherCloseAllHighlight
                    ) {
                        HapticHelper.appTick(this, settings)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when {
                    taskSwitcherCloseHighlight >= 0 -> {
                        val closeIndex = taskSwitcherCloseHighlight
                        val row = layout.rows.getOrNull(closeIndex)
                        val packageName = row?.entry?.app?.packageName
                        val isLocked = row?.entry?.isLocked == true
                        val longPress = taskSwitcherCloseLongPressTriggered ||
                            (taskSwitcherClosePressIndex == closeIndex &&
                                event.eventTime - taskSwitcherClosePressDownTime >= TASK_SWITCHER_LONG_PRESS_MS)
                        cancelTaskSwitcherCloseLongPress()
                        if (packageName != null) {
                            when {
                                taskSwitcherCloseLongPressTriggered -> Unit
                                longPress -> toggleTaskSwitcherLock(closeIndex, packageName, !isLocked)
                                !isLocked -> {
                                    val entry = row.entry
                                    if (entry.taskId > 0) {
                                        recentApps.removeAll { it.taskId == entry.taskId }
                                        RecentTasksLoader.removeTaskIds(listOf(entry.taskId))
                                    } else {
                                        recentApps.removeAll { it.app.packageName == packageName }
                                        TaskManagerUtil.removePackageFromCache(packageName)
                                        RecentTasksLoader.removePackages(listOf(packageName))
                                    }
                                    TaskSwitcherLockStore.setLocked(context, packageName, locked = false)
                                    taskSwitcherLayout = null
                                    if (recentApps.isEmpty()) {
                                        gestureSession.endSession()
                                    } else {
                                        invalidate()
                                    }
                                    dismissTaskCards(listOf(entry))
                                }
                            }
                        }
                    }
                    taskSwitcherCloseAllHighlight -> {
                        val entries = recentApps.filterNot { it.isLocked }
                        val packages = entries.map { it.app.packageName }
                        val dismissed = packages.toSet()
                        recentApps.removeAll { it.app.packageName in dismissed }
                        dismissed.forEach { TaskManagerUtil.removePackageFromCache(it) }
                        RecentTasksLoader.removePackages(packages)
                        RecentTasksLoader.removeTaskIds(entries.map { it.taskId })
                        taskSwitcherLayout = null
                        if (recentApps.isEmpty()) {
                            gestureSession.endSession()
                        } else {
                            invalidate()
                        }
                        dismissTaskCards(entries)
                    }
                    taskSwitcherFreeWindowHighlight >= 0 -> {
                        val app = layout.rows
                            .getOrNull(taskSwitcherFreeWindowHighlight)
                            ?.entry
                            ?.app
                        if (app != null) {
                            HapticHelper.confirmLaunch(this, settings)
                            TaskSwitcherMenuActions.openInFreeWindow(
                                context,
                                app.packageName,
                                settings,
                                appRepository,
                                app = app,
                                onSessionEnd = { gestureSession.endSession() },
                            )
                        } else {
                            gestureSession.endSession()
                        }
                    }
                    taskSwitcherRowHighlight >= 0 && !taskSwitcherRowLongPressTriggered -> {
                        layout.rows.getOrNull(taskSwitcherRowHighlight)?.entry?.let { entry ->
                            HapticHelper.confirmLaunch(this, settings)
                            actionExecutor.switchToRecentTask(
                                taskId = entry.taskId,
                                rawIdentifier = entry.rawIdentifier,
                                topComponent = entry.topComponent,
                                packageName = entry.app.packageName,
                                settings = settings,
                            )
                        }
                        gestureSession.endSession()
                    }
                }
                resetTaskSwitcherTouchHighlights()
                return true
            }
        }
        return false
    }

    private fun resetTaskSwitcherTouchHighlights() {
        cancelTaskSwitcherCloseLongPress()
        cancelTaskSwitcherRowLongPress()
        taskSwitcherRowHighlight = -1
        taskSwitcherCloseHighlight = -1
        taskSwitcherFreeWindowHighlight = -1
        taskSwitcherCloseAllHighlight = false
        taskSwitcherClosePressIndex = -1
        taskSwitcherClosePressDownTime = 0L
        taskSwitcherCloseLongPressTriggered = false
        taskSwitcherRowPressIndex = -1
        taskSwitcherRowPressDownTime = 0L
        taskSwitcherRowLongPressTriggered = false
        invalidate()
    }

    private fun dismissTaskSwitcherContextMenu() {
        taskSwitcherShortcutLoadSeq++
        taskSwitcherContextMenu = null
        taskSwitcherMenuHighlight = -1
        invalidate()
    }

    private fun scheduleTaskSwitcherRowLongPress(index: Int) {
        cancelTaskSwitcherRowLongPress()
        taskSwitcherRowLongPressRunnable = Runnable {
            if (taskSwitcherRowPressIndex != index) return@Runnable
            showTaskSwitcherContextMenu(index)
        }
        postDelayed(taskSwitcherRowLongPressRunnable!!, TASK_SWITCHER_LONG_PRESS_MS)
    }

    private fun cancelTaskSwitcherRowLongPress() {
        taskSwitcherRowLongPressRunnable?.let { removeCallbacks(it) }
        taskSwitcherRowLongPressRunnable = null
    }

    private fun showTaskSwitcherContextMenu(index: Int) {
        val layout = taskSwitcherLayout ?: return
        val row = layout.rows.getOrNull(index) ?: return
        taskSwitcherRowLongPressTriggered = true
        taskSwitcherRowHighlight = -1
        val packageName = row.entry.app.packageName
        HapticHelper.confirmLaunch(this, settings)
        val appCtx = context.applicationContext
        val fixedItems = TaskSwitcherMenuActions.buildFixedMenuItems(appCtx)
        val loadSeq = ++taskSwitcherShortcutLoadSeq
        val instantShortcuts = AppShortcutLoader.loadInstantShortcuts(packageName)
            .filter { AppShortcutLoader.isDisplayableShortcut(it) }
        publishTaskSwitcherContextMenu(
            index = index,
            packageName = packageName,
            taskId = row.entry.taskId,
            rawIdentifier = row.entry.rawIdentifier,
            shortcuts = instantShortcuts,
            fixedItems = fixedItems,
            loadSeq = loadSeq,
        )
        Thread {
            val shortcuts = AppShortcutLoader.loadMenuShortcuts(appCtx, packageName)
            publishTaskSwitcherContextMenu(
                index = index,
                packageName = packageName,
                taskId = row.entry.taskId,
                rawIdentifier = row.entry.rawIdentifier,
                shortcuts = shortcuts,
                fixedItems = fixedItems,
                loadSeq = loadSeq,
            )
        }.start()
    }

    private fun publishTaskSwitcherContextMenu(
        index: Int,
        packageName: String,
        taskId: Int,
        rawIdentifier: String,
        shortcuts: List<TaskSwitcherMenuItem>,
        fixedItems: List<TaskSwitcherMenuItem>,
        loadSeq: Int,
    ) {
        post {
            if (loadSeq != taskSwitcherShortcutLoadSeq) return@post
            if (gestureSession.panelMode() != OverlayPanelMode.TASK_SWITCHER) return@post
            val latestLayout = taskSwitcherLayout ?: computeTaskSwitcherLayout().also { taskSwitcherLayout = it }
            if (latestLayout.rows.getOrNull(index) == null) return@post
            val latestRow = latestLayout.rows[index]
            if (latestRow.entry.app.packageName != packageName ||
                latestRow.entry.taskId != taskId ||
                latestRow.entry.rawIdentifier != rawIdentifier
            ) {
                return@post
            }
            taskSwitcherContextMenu = TaskSwitcherContextMenuLayoutFactory.build(
                side = side,
                panelRect = latestLayout.panelRect,
                rowRect = latestRow.rowRect,
                rowIndex = index,
                packageName = packageName,
                items = TaskSwitcherMenuActions.mergeMenuItems(shortcuts, fixedItems),
                viewWidth = width,
                viewHeight = height,
                density = resources.displayMetrics.density,
            )
            taskSwitcherMenuHighlight = -1
            invalidate()
        }
    }

    private fun handleTaskSwitcherContextMenuTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val menu = taskSwitcherContextMenu ?: return false
        val panel = taskSwitcherLayout?.panelRect ?: computeTaskSwitcherLayout().panelRect
        val touchX = panelEnterAdjustedX(localX, panel)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hitIndex = menu.itemRects.indexOfFirst { it.contains(touchX, localY) }
                if (hitIndex >= 0) {
                    taskSwitcherMenuHighlight = hitIndex
                    HapticHelper.appTick(this, settings)
                } else if (!menu.menuRect.contains(touchX, localY)) {
                    dismissTaskSwitcherContextMenu()
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val prev = taskSwitcherMenuHighlight
                taskSwitcherMenuHighlight = menu.itemRects.indexOfFirst { it.contains(touchX, localY) }
                if (taskSwitcherMenuHighlight != prev && taskSwitcherMenuHighlight >= 0) {
                    HapticHelper.appTick(this, settings)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val selected = taskSwitcherMenuHighlight
                taskSwitcherMenuHighlight = -1
                if (selected >= 0) {
                    val item = menu.items.getOrNull(selected) ?: return true
                    HapticHelper.confirmLaunch(this, settings)
                    executeTaskSwitcherMenuItem(menu.packageName, item)
                } else if (!menu.menuRect.contains(touchX, localY)) {
                    dismissTaskSwitcherContextMenu()
                }
                invalidate()
                return true
            }
        }
        return false
    }

    private fun executeTaskSwitcherMenuItem(packageName: String, item: TaskSwitcherMenuItem) {
        dismissTaskSwitcherContextMenu()
        TaskSwitcherMenuActions.execute(
            context = context,
            item = item,
            packageName = packageName,
            settings = settings,
            appRepository = appRepository,
            onSessionEnd = { gestureSession.endSession() },
        )
        when (item.type) {
            TaskSwitcherMenuItemType.FORCE_STOP, TaskSwitcherMenuItemType.FREE_WINDOW -> Unit
            else -> gestureSession.endSession()
        }
    }

    private fun scheduleTaskSwitcherCloseLongPress(index: Int, packageName: String) {
        cancelTaskSwitcherCloseLongPress()
        taskSwitcherCloseLongPressRunnable = Runnable {
            if (gestureSession.panelMode() != OverlayPanelMode.TASK_SWITCHER) return@Runnable
            if (taskSwitcherClosePressIndex != index) return@Runnable
            taskSwitcherCloseLongPressTriggered = true
            taskSwitcherCloseHighlight = -1
            taskSwitcherRowHighlight = -1
            val locked = recentApps.getOrNull(index)?.isLocked != true
            toggleTaskSwitcherLock(index, packageName, locked)
        }
        postDelayed(taskSwitcherCloseLongPressRunnable!!, TASK_SWITCHER_LONG_PRESS_MS)
    }

    private fun cancelTaskSwitcherCloseLongPress() {
        taskSwitcherCloseLongPressRunnable?.let { removeCallbacks(it) }
        taskSwitcherCloseLongPressRunnable = null
    }

    private fun toggleTaskSwitcherLock(index: Int, packageName: String, locked: Boolean) {
        if (recentApps.getOrNull(index)?.app?.packageName != packageName) return
        TaskSwitcherLockStore.setLocked(context, packageName, locked)
        recentApps[index] = recentApps[index].copy(isLocked = locked)
        taskSwitcherLayout = null
        HapticHelper.confirmLaunch(this, settings)
        invalidate()
    }

    private fun dismissTaskCards(entries: List<RecentAppEntry>) {
        if (entries.isEmpty() || !TaskManagerUtil.hasPermission()) return
        Thread {
            TaskManagerUtil.runOnTaskWorker {
                entries.filterNot { it.isLocked }.forEach { entry ->
                    val removed = if (entry.taskId > 0) {
                        TaskManagerUtil.removeTaskById(entry.taskId)
                    } else {
                        TaskManagerUtil.removeTaskByPackage(entry.app.packageName)
                    }
                    if (!removed) {
                        Log.w(
                            "EdgeGestureOverlay",
                            "dismissTaskCards failed package=${entry.app.packageName} taskId=${entry.taskId}",
                        )
                    }
                }
                RecentTasksLoader.syncFromSystem(appRepository)
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        syncZoneLayout()
        if (previewMode) {
            when (previewContent) {
                LayoutPreviewContent.TRIGGER_ONLY -> drawTriggerZonePreview(canvas)
                LayoutPreviewContent.INDEX_ONLY -> drawLetterRail(canvas)
            }
            return
        }
        if (!gestureSession.isActive()) return
        when (gestureSession.panelMode()) {
            OverlayPanelMode.INDEX -> {
                drawWithPanelEnterAnimation(canvas, indexPanelContentRect()) {
                    drawLetterRail(canvas)
                    if (indexSession.selectedLetter != null) {
                        drawAppGrid(canvas)
                        drawLetterBubble(canvas)
                    }
                }
            }
            OverlayPanelMode.QUICK_LAUNCHER -> {
                val panelRect = quickLauncherPanelRect()
                drawWithPanelEnterAnimation(canvas, panelRect) {
                    drawQuickLauncherPanel(canvas)
                }
            }
            OverlayPanelMode.TASK_SWITCHER -> drawTaskSwitcherPanel(canvas)
            OverlayPanelMode.NONE -> Unit
        }
    }

    override fun hapticLetterTick() {
        HapticHelper.letterTick(this, settings)
    }

    override fun hapticAppTick() {
        HapticHelper.appTick(this, settings)
    }

    override fun hapticConfirmLaunch() {
        HapticHelper.confirmLaunch(this, settings)
    }

    override fun scheduleDelayed(runnable: Runnable, delayMs: Long) {
        postDelayed(runnable, delayMs)
    }

    override fun cancelDelayed(runnable: Runnable) {
        removeCallbacks(runnable)
    }

    override fun requestInvalidate() {
        invalidate()
    }

    override fun onSessionStart(mode: OverlayPanelMode) {
        syncZoneLayout()
        cancelPanelEnterAnimation()
        when (mode) {
            OverlayPanelMode.TASK_SWITCHER -> {
                panelEnterProgress = 0f
                taskSwitcherFrozenAnchorLocalY = null
                taskSwitcherAnchorRawY = pathRecognizer.gestureStartRawY()
                taskSwitcherLayout = null
                loadTaskSwitcherApps(deferInvalidate = true)
            }
            OverlayPanelMode.INDEX, OverlayPanelMode.QUICK_LAUNCHER -> {
                panelEnterProgress = 0f
            }
            OverlayPanelMode.NONE -> {
                panelEnterProgress = 1f
            }
        }
        panelGridSession.reset()
        onSessionStartCallback()
        if (mode != OverlayPanelMode.NONE) {
            post {
                if (gestureSession.panelMode() != mode) return@post
                syncZoneLayout()
                if (mode == OverlayPanelMode.TASK_SWITCHER) {
                    taskSwitcherLayout = null
                    taskSwitcherFrozenAnchorLocalY = resolveTaskSwitcherAnchorLocalY()
                }
                startPanelEnterAnimation()
            }
        }
    }

    private fun loadTaskSwitcherApps(deferInvalidate: Boolean = false) {
        taskSwitcherLayout = null
        taskSwitcherRowHighlight = -1
        taskSwitcherCloseHighlight = -1
        taskSwitcherCloseAllHighlight = false

        val placeholder = RecentTasksLoader.peekCached()
        recentApps = placeholder.toMutableList()
        if (!deferInvalidate) {
            invalidateTaskSwitcherPanel()
        }

        if (!TaskManagerUtil.hasPermission()) {
            recentApps = mutableListOf()
            if (!deferInvalidate) {
                invalidateTaskSwitcherPanel()
            }
            return
        }

        val generation = ++taskSwitcherLoadGeneration
        RecentTasksLoader.refreshAsync(appRepository) { fresh ->
            if (generation != taskSwitcherLoadGeneration) return@refreshAsync
            if (gestureSession.panelMode() != OverlayPanelMode.TASK_SWITCHER) return@refreshAsync
            if (taskSwitcherContextMenu != null) {
                dismissTaskSwitcherContextMenu()
            }
            recentApps = fresh.toMutableList()
            taskSwitcherLayout = null
            invalidateTaskSwitcherPanel()
        }
    }

    override fun onSessionEnd() {
        cancelPanelEnterAnimation()
        panelEnterProgress = 1f
        taskSwitcherLoadGeneration++
        syncZoneLayout()
        panelGridSession.reset()
        taskSwitcherLayout = null
        taskSwitcherRowHighlight = -1
        taskSwitcherCloseHighlight = -1
        taskSwitcherCloseAllHighlight = false
        taskSwitcherAnchorRawY = null
        taskSwitcherFrozenAnchorLocalY = null
        dismissTaskSwitcherContextMenu()
        cancelTaskSwitcherCloseLongPress()
        cancelTaskSwitcherRowLongPress()
        post { onSessionEndCallback() }
    }

    override fun onRequestInvalidate() {
        invalidate()
    }

    override fun hapticGestureStart() {
        HapticHelper.gestureStart(this, settings)
    }

    private fun syncZoneLayout() {
        zoneLayout.update(
            settings = settings,
            viewWidth = width,
            viewHeight = height,
            density = resources.displayMetrics.density,
            sessionActive = gestureSession.isActive(),
            previewMode = previewMode,
        )
    }

    private fun rawToLocal(rawX: Float, rawY: Float): Pair<Float, Float> {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return rawX - loc[0] to rawY - loc[1]
    }

    private fun appsPerRow(): Int = settings.appsPerRow.coerceIn(2, 5)

    private fun gridLayoutInfo(appCount: Int): GridLayoutInfo {
        val m = appsPerRow()
        val panelColumns = if (appCount in 1 until m) appCount else m
        val rows = if (appCount == 0) 1 else ceil(appCount / m.toFloat()).toInt()
        val panelWidth = panelColumns * cellWidth + gridPadding * 2
        return GridLayoutInfo(m, panelColumns, rows, panelWidth)
    }

    private fun visualColumn(index: Int, m: Int, appCount: Int): Int {
        val colInRow = index % m
        val row = index / m
        val appsInRow = minOf(m, appCount - row * m)
        return when (side) {
            PanelSide.RIGHT -> when {
                appCount < m -> appCount - 1 - colInRow
                appsInRow == m -> m - 1 - colInRow
                else -> m - appsInRow + colInRow
            }
            PanelSide.LEFT -> colInRow
        }
    }

    private fun drawTriggerZonePreview(canvas: Canvas) {
        val corner = dp(6f)
        if (settings.interceptSystemBackGesture) {
            val intercept = zoneLayout.interceptZoneRect()
            triggerPreviewFillPaint.color = Color.argb(36, 33, 150, 243)
            canvas.drawRoundRect(intercept, corner, corner, triggerPreviewFillPaint)
            triggerPreviewStrokePaint.color = Color.argb(120, 66, 165, 245)
            triggerPreviewStrokePaint.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(intercept, corner, corner, triggerPreviewStrokePaint)
        }
        val zone = zoneLayout.triggerZoneRect()
        triggerPreviewFillPaint.color = Color.argb(72, 255, 152, 0)
        canvas.drawRoundRect(zone, corner, corner, triggerPreviewFillPaint)
        triggerPreviewStrokePaint.color = Color.argb(210, 255, 167, 38)
        triggerPreviewStrokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(zone, corner, corner, triggerPreviewStrokePaint)
    }

    private fun drawLetterRail(canvas: Canvas) {
        val rail = zoneLayout.indexRailRect()
        val alphaScale = settings.panelOpacity.coerceIn(0.6f, 1f)
        railBgPaint.color = Color.argb((200 * alphaScale).toInt(), 38, 38, 42)
        canvas.drawRoundRect(rail, railCorner, railCorner, railBgPaint)

        val slotHeight = rail.height() / railLetters.size
        val letterSize = slotHeight.coerceAtMost(dp(11.5f))
        val selectedLetter = indexSession.selectedLetter

        railLetters.forEachIndexed { index, letter ->
            val centerY = rail.top + slotHeight * index + slotHeight * 0.65f
            val centerX = rail.centerX()
            val selected = letter == selectedLetter
            if (selected) {
                letterCirclePaint.color = Color.argb(90, 255, 255, 255)
                canvas.drawCircle(centerX, centerY - letterSize * 0.15f, letterSize * 0.85f, letterCirclePaint)
                letterPaint.color = Color.WHITE
                letterPaint.textSize = letterSize * 1.05f
                letterPaint.typeface = Typeface.DEFAULT_BOLD
            } else {
                letterPaint.color = Color.argb(200, 220, 220, 220)
                letterPaint.textSize = letterSize
                letterPaint.typeface = Typeface.DEFAULT
            }
            canvas.drawText(letter.toString(), centerX, centerY, letterPaint)
        }
    }

    private fun drawLetterBubble(canvas: Canvas) {
        val letter = indexSession.selectedLetter ?: return
        val center = bubbleCenter()
        bubblePaint.color = Color.argb((240 * settings.panelOpacity).toInt().coerceIn(150, 240), 52, 52, 56)
        canvas.drawCircle(center.x, center.y, bubbleRadius, bubblePaint)
        bubbleLetterPaint.textSize = sp(22f)
        canvas.drawText(
            letter.toString(),
            center.x,
            center.y + sp(22f) * 0.35f,
            bubbleLetterPaint,
        )
    }

    private fun drawAppGrid(canvas: Canvas) {
        val filteredApps = indexSession.filteredApps
        if (filteredApps.isEmpty()) return
        val appCount = filteredApps.size
        val layout = gridLayoutInfo(appCount)
        val m = layout.appsPerRow
        val grid = gridPopupRect()
        panelBgPaint.color = Color.argb((215 * settings.panelOpacity).toInt().coerceIn(140, 215), 48, 48, 52)
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)

        indexSession.gridCellBounds.clear()
        filteredApps.forEachIndexed { index, app ->
            val row = index / m
            val visualCol = visualColumn(index, m, appCount)
            val left = grid.left + gridPadding + visualCol * cellWidth
            val top = grid.top + gridPadding + row * cellHeight
            val cell = RectF(left, top, left + cellWidth, top + cellHeight)
            indexSession.gridCellBounds += app to cell

            if (app == indexSession.highlightedApp) {
                tmpRect.set(cell.left + dp(3f), cell.top + dp(2f), cell.right - dp(3f), cell.bottom - dp(2f))
                val paint = if (indexSession.longPressArmed) {
                    cellLongPressHighlightPaint
                } else {
                    cellHighlightPaint
                }
                canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), paint)
            }

            val icon = iconFor(app)
            val iconTop = cell.top + gridIconTopInset
            val label = ellipsize(app.label, cellWidth - gridCellInset * 2)
            val labelBaseline = iconTop + gridIconSize + gridIconLabelGap - appLabelPaint.fontMetrics.ascent
            val iconCenterX = cell.centerX()
            canvas.drawBitmap(icon, iconCenterX - gridIconSize / 2f, iconTop, null)
            canvas.drawText(label, iconCenterX, labelBaseline, appLabelPaint)
        }
    }

    private fun anchorColumnIndex(layout: GridLayoutInfo): Int {
        return when (side) {
            PanelSide.LEFT -> 0
            PanelSide.RIGHT -> layout.panelColumns - 1
        }
    }

    private fun columnCenterX(grid: RectF, columnIndex: Int): Float {
        return grid.left + gridPadding + columnIndex * cellWidth + cellWidth / 2f
    }

    private fun bubbleCenter(): PointF {
        val filteredApps = indexSession.filteredApps
        if (filteredApps.isNotEmpty()) {
            val grid = gridPopupRect()
            val layout = gridLayoutInfo(filteredApps.size)
            val cx = columnCenterX(grid, anchorColumnIndex(layout))
            val cy = grid.top - bubbleRadius - bubblePanelGap
            return PointF(cx, cy)
        }
        val rail = zoneLayout.indexRailRect()
        val cy = indexSession.selectedLetterCenterY() ?: rail.centerY()
        val cx = when (side) {
            PanelSide.LEFT -> rail.right + bubbleRadius + dp(4f)
            PanelSide.RIGHT -> rail.left - bubbleRadius - dp(4f)
        }
        return PointF(cx, cy)
    }

    private fun gridPopupRect(): RectF {
        val filteredApps = indexSession.filteredApps
        val layout = gridLayoutInfo(filteredApps.size)
        val gh = layout.rows * cellHeight + gridPadding * 2
        val gw = layout.panelWidth
        val rail = zoneLayout.indexRailRect()
        val letterY = indexSession.selectedLetterCenterY() ?: rail.centerY()
        val bubbleReserve = bubbleRadius * 2 + bubblePanelGap + dp(8f)
        var top = letterY - gh / 2f
        top = top.coerceSafe(bubbleReserve + dp(8f), height - gh - dp(16f))
        val gap = dp(8f)
        val left = when (side) {
            PanelSide.LEFT -> rail.right + gap
            PanelSide.RIGHT -> rail.left - gap - gw
        }
        return RectF(left, top, left + gw, top + gh)
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (appLabelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && appLabelPaint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }

    private fun drawScaledIcon(canvas: Canvas, app: AppInfo, left: Float, top: Float, size: Float) {
        tmpRect.set(left, top, left + size, top + size)
        canvas.drawBitmap(iconFor(app), null, tmpRect, iconBitmapPaint)
    }

    private fun iconFor(app: AppInfo): Bitmap {
        return iconCache.getOrPut(app.packageName) {
            val size = gridIconSize.toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val drawable = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon.mutate()
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        }
    }

    private fun quickLauncherItems(): List<QuickLauncherItem> {
        val configured = when (side) {
            PanelSide.LEFT -> settings.quickLauncherLeft
            PanelSide.RIGHT -> settings.quickLauncherRight
        }
        if (configured.isNotEmpty()) return configured
        return apps.take(9).map { QuickLauncherItem.app(it.packageName, it.label) }
    }

    private fun drawQuickLauncherPanel(canvas: Canvas) {
        drawUtilityGrid(
            canvas = canvas,
            title = "快速启动器",
            entries = quickLauncherItems(),
        ) { item, cell, index ->
            panelGridSession.cellBounds.add(item to cell)
            val label = when (item.type) {
                QuickLauncherItemType.APP -> apps.find { it.packageName == item.payload }?.label ?: item.label
                QuickLauncherItemType.SHORTCUT -> item.label.ifBlank { "快捷方式" }
                QuickLauncherItemType.WIDGET -> item.label.ifBlank { "小组件" }
            }
            drawGridCell(canvas, cell, index, label) {
                when (item.type) {
                    QuickLauncherItemType.APP -> apps.find { it.packageName == item.payload }?.let { iconFor(it) }
                    else -> null
                }
            }
        }
    }

    private fun drawTaskSwitcherPanel(canvas: Canvas) {
        val layout = computeTaskSwitcherLayout()
        taskSwitcherLayout = layout
        panelContentRect.set(layout.panelRect)
        drawWithPanelEnterAnimation(canvas, layout.panelRect) {
            drawTaskSwitcherPanelContent(canvas, layout)
        }
    }

    private fun drawTaskSwitcherPanelContent(canvas: Canvas, layout: TaskSwitcherPanelLayout) {
        val rowHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 0, 0, 0) }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 0, 0, 0) }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 30, 30, 30)
            textSize = sp(13.5f)
        }
        val closeAllPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 0, 122, 255)
            textSize = sp(13f)
            textAlign = Paint.Align.CENTER
        }
        val closeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 60, 60, 60)
            strokeWidth = dp(1.55f)
            strokeCap = Paint.Cap.ROUND
        }
        val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 120, 120, 120) }

        val panel = layout.panelRect
        val panelCorner = dp(13f)
        drawElevatedRoundRect(canvas, panel, panelCorner, Color.WHITE)

        if (layout.rows.isEmpty()) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 80, 80, 80)
                textSize = sp(13f)
                textAlign = Paint.Align.CENTER
            }
            val hint = if (TaskManagerUtil.hasPermission()) {
                context.getString(R.string.task_switcher_empty)
            } else {
                context.getString(R.string.task_switcher_no_shizuku)
            }
            canvas.drawText(
                hint,
                panel.centerX(),
                panel.top + (panel.height() - layout.closeAllRect.height()) / 2f -
                    (hintPaint.descent() + hintPaint.ascent()) / 2f,
                hintPaint,
            )
        }

        layout.rows.forEachIndexed { index, row ->
            if (index == taskSwitcherRowHighlight ||
                index == taskSwitcherContextMenu?.rowIndex
            ) {
                canvas.drawRect(row.rowRect, rowHighlightPaint)
            }
            if (index == taskSwitcherCloseHighlight) {
                canvas.drawRect(row.closeRect, rowHighlightPaint)
            }
            if (index == taskSwitcherFreeWindowHighlight) {
                canvas.drawRect(row.freeWindowRect, rowHighlightPaint)
            }
            val iconSize = dp(30f)
            val iconLeft = taskSwitcherIconLeft(row)
            val iconTop = row.rowRect.centerY() - iconSize / 2f
            drawScaledIcon(canvas, row.entry.app, iconLeft, iconTop, iconSize)
            val labelX = iconLeft + iconSize + dp(9f)
            val labelMaxWidth = taskSwitcherLabelMaxWidth(row, labelX)
            val label = ellipsize(row.entry.app.label, labelMaxWidth, labelPaint)
            val labelBaseline = row.rowRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(label, labelX, labelBaseline, labelPaint)
            val gripX = taskSwitcherGripX(row.freeWindowRect)
            drawGripDots(canvas, gripX, row.rowRect.centerY(), gripPaint)
            drawCloseOrLockIcon(canvas, row.closeRect, row.entry.isLocked, closeIconPaint)
            if (index < layout.rows.lastIndex) {
                canvas.drawLine(
                    row.rowRect.left + dp(10f),
                    row.rowRect.bottom,
                    row.rowRect.right - dp(10f),
                    row.rowRect.bottom,
                    dividerPaint,
                )
            }
        }

        if (taskSwitcherCloseAllHighlight) {
            canvas.drawRect(layout.closeAllRect, rowHighlightPaint)
        }
        val closeAllText = context.getString(R.string.task_switcher_close_all)
        canvas.drawText(
            closeAllText,
            layout.closeAllRect.centerX(),
            layout.closeAllRect.centerY() - (closeAllPaint.descent() + closeAllPaint.ascent()) / 2f,
            closeAllPaint,
        )
        drawTaskSwitcherContextMenu(canvas)
    }

    private fun drawTaskSwitcherContextMenu(canvas: Canvas) {
        val menu = taskSwitcherContextMenu ?: return
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 0, 0, 0) }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 30, 30, 30)
            textSize = sp(14f)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 0, 0, 0) }
        val corner = dp(10f)
        drawElevatedRoundRect(canvas, menu.menuRect, corner, Color.WHITE)
        val fixedStart = menu.items.indexOfFirst { it.type != TaskSwitcherMenuItemType.SHORTCUT }
        menu.items.forEachIndexed { index, item ->
            val rect = menu.itemRects[index]
            if (index == taskSwitcherMenuHighlight) {
                canvas.drawRect(rect, highlightPaint)
            }
            if (fixedStart > 0 && index == fixedStart) {
                canvas.drawLine(rect.left + dp(12f), rect.top, rect.right - dp(12f), rect.top, dividerPaint)
            }
            val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            val label = ellipsize(item.label, rect.width() - dp(24f), textPaint)
            canvas.drawText(label, rect.left + dp(16f), baseline, textPaint)
        }
    }

    private fun computeTaskSwitcherLayout(): TaskSwitcherPanelLayout {
        val rowHeight = dp(42f)
        val footerHeight = dp(36f)
        val panelWidth = dp(226f)
        val rowCount = recentApps.size.coerceAtLeast(if (recentApps.isEmpty()) 0 else 1)
        val panelHeight = (if (rowCount == 0) dp(52f) else rowCount * rowHeight) + footerHeight
        val trigger = zoneLayout.triggerZoneRect()
        val anchorY = taskSwitcherAnchorLocalY().coerceIn(trigger.top, trigger.bottom)
        var top = anchorY - rowHeight / 2f
        top = top.coerceSafe(dp(16f), height - panelHeight - dp(16f))
        val gap = dp(10f)
        val left = when (side) {
            PanelSide.LEFT -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - panelWidth
        }
        val panelRect = RectF(left, top, left + panelWidth, top + panelHeight)
        val closeAllRect = RectF(
            panelRect.left,
            panelRect.bottom - footerHeight,
            panelRect.right,
            panelRect.bottom,
        )
        val rows = recentApps.mapIndexed { index, entry ->
            val rowTop = panelRect.top + index * rowHeight
            val rowRect = RectF(panelRect.left, rowTop, panelRect.right, rowTop + rowHeight)
            val closeSize = dp(30f)
            val closeRect = when (side) {
                PanelSide.LEFT -> RectF(
                    rowRect.left + dp(5.5f),
                    rowRect.centerY() - closeSize / 2f,
                    rowRect.left + closeSize + dp(5.5f),
                    rowRect.centerY() + closeSize / 2f,
                )
                PanelSide.RIGHT -> RectF(
                    rowRect.right - closeSize - dp(5.5f),
                    rowRect.centerY() - closeSize / 2f,
                    rowRect.right - dp(5.5f),
                    rowRect.centerY() + closeSize / 2f,
                )
            }
            val freeWindowRect = when (side) {
                PanelSide.LEFT -> RectF(
                    rowRect.right - closeSize - dp(5.5f),
                    rowRect.centerY() - closeSize / 2f,
                    rowRect.right - dp(5.5f),
                    rowRect.centerY() + closeSize / 2f,
                )
                PanelSide.RIGHT -> RectF(
                    rowRect.left + dp(5.5f),
                    rowRect.centerY() - closeSize / 2f,
                    rowRect.left + closeSize + dp(5.5f),
                    rowRect.centerY() + closeSize / 2f,
                )
            }
            TaskSwitcherRowLayout(entry, rowRect, closeRect, freeWindowRect)
        }
        return TaskSwitcherPanelLayout(panelRect, rows, closeAllRect)
    }

    private fun taskSwitcherActionIconInset(): Float = dp(9.5f)

    private fun taskSwitcherGripDotRadius(): Float = dp(1.65f)

    private fun taskSwitcherGripGapX(): Float = dp(3f)

    private fun taskSwitcherGripGapY(): Float = dp(3.6f)

    private fun taskSwitcherGripX(freeWindowRect: RectF): Float {
        val inset = taskSwitcherActionIconInset()
        val radius = taskSwitcherGripDotRadius()
        val gapX = taskSwitcherGripGapX()
        return when (side) {
            PanelSide.LEFT -> freeWindowRect.right - inset - gapX - radius
            PanelSide.RIGHT -> freeWindowRect.left + inset + radius
        }
    }

    private fun taskSwitcherIconLeft(row: TaskSwitcherRowLayout): Float {
        return when (side) {
            PanelSide.LEFT -> row.closeRect.right + dp(4f)
            PanelSide.RIGHT -> row.freeWindowRect.right + dp(4f)
        }
    }

    private fun taskSwitcherLabelMaxWidth(row: TaskSwitcherRowLayout, labelX: Float): Float {
        return when (side) {
            PanelSide.LEFT -> row.freeWindowRect.left - labelX - dp(8f)
            PanelSide.RIGHT -> row.closeRect.left - labelX - dp(6f)
        }.coerceAtLeast(dp(24f))
    }

    private fun drawGripDots(canvas: Canvas, x: Float, centerY: Float, paint: Paint) {
        val radius = taskSwitcherGripDotRadius()
        val gapY = taskSwitcherGripGapY()
        val gapX = taskSwitcherGripGapX()
        for (col in 0..1) {
            for (row in -1..1) {
                canvas.drawCircle(
                    x + col * gapX,
                    centerY + row * gapY,
                    radius,
                    paint,
                )
            }
        }
    }

    private fun drawCloseOrLockIcon(canvas: Canvas, rect: RectF, locked: Boolean, paint: Paint) {
        if (locked) drawLockIcon(canvas, rect, paint) else drawCloseIcon(canvas, rect, paint)
    }

    private fun drawCloseIcon(canvas: Canvas, rect: RectF, paint: Paint) {
        val inset = taskSwitcherActionIconInset()
        canvas.drawLine(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, paint)
        canvas.drawLine(rect.right - inset, rect.top + inset, rect.left + inset, rect.bottom - inset, paint)
    }

    private fun drawLockIcon(canvas: Canvas, rect: RectF, paint: Paint) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val bodyHalfWidth = dp(5f)
        val bodyHeight = dp(6.5f)
        val bodyTop = cy + dp(0.5f)
        val bodyBottom = bodyTop + bodyHeight
        val shackleTop = cy - dp(5.5f)
        val shackleBottom = bodyTop + dp(1f)
        paint.style = Paint.Style.STROKE
        canvas.drawArc(
            cx - bodyHalfWidth,
            shackleTop,
            cx + bodyHalfWidth,
            shackleBottom,
            180f,
            180f,
            false,
            paint,
        )
        canvas.drawRoundRect(
            cx - bodyHalfWidth,
            bodyTop,
            cx + bodyHalfWidth,
            bodyBottom,
            dp(1.2f),
            dp(1.2f),
            paint,
        )
    }

    private fun drawElevatedRoundRect(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        fillColor: Int,
    ) {
        val shadowBlur = dp(5.5f)
        val shadowLayers = 4
        val shadowAlpha = 30
        for (layer in shadowLayers downTo 1) {
            val fraction = layer / shadowLayers.toFloat()
            val spread = shadowBlur * fraction
            val alpha = (shadowAlpha * fraction * fraction / shadowLayers).toInt().coerceIn(1, 255)
            elevatedShadowPaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawRoundRect(
                rect.left - spread,
                rect.top - spread,
                rect.right + spread,
                rect.bottom + spread,
                cornerRadius + spread * 0.35f,
                cornerRadius + spread * 0.35f,
                elevatedShadowPaint,
            )
        }
        elevatedCardPaint.color = fillColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, elevatedCardPaint)
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }

    private fun <T> drawUtilityGrid(
        canvas: Canvas,
        title: String,
        entries: List<T>,
        drawCell: (T, RectF, Int) -> Unit,
    ) {
        if (entries.isEmpty()) return
        val m = appsPerRow()
        val appCount = entries.size
        val layout = gridLayoutInfo(appCount)
        val grid = utilityPanelRect(layout.panelWidth, layout.rows)
        panelContentRect.set(grid)
        panelGridSession.cellBounds.clear()
        panelBgPaint.color = Color.argb((225 * settings.panelOpacity).toInt().coerceIn(150, 225), 48, 48, 52)
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)
        letterPaint.textAlign = Paint.Align.LEFT
        letterPaint.color = Color.WHITE
        letterPaint.textSize = sp(14f)
        letterPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(title, grid.left + gridPadding, grid.top + dp(18f), letterPaint)
        entries.forEachIndexed { index, entry ->
            val row = index / m
            val visualCol = visualColumn(index, m, appCount)
            val left = grid.left + gridPadding + visualCol * cellWidth
            val top = grid.top + dp(28f) + gridPadding + row * cellHeight
            val cell = RectF(left, top, left + cellWidth, top + cellHeight)
            drawCell(entry, cell, index)
        }
        letterPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawGridCell(
        canvas: Canvas,
        cell: RectF,
        index: Int,
        label: String,
        iconProvider: () -> Bitmap?,
    ) {
        if (index == panelGridSession.highlightedIndex) {
            tmpRect.set(cell.left + dp(3f), cell.top + dp(2f), cell.right - dp(3f), cell.bottom - dp(2f))
            canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), cellHighlightPaint)
        }
        val icon = iconProvider()
        val iconTop = cell.top + gridIconTopInset
        val displayLabel = ellipsize(label, cellWidth - gridCellInset * 2)
        val labelBaseline = iconTop + gridIconSize + gridIconLabelGap - appLabelPaint.fontMetrics.ascent
        val iconCenterX = cell.centerX()
        icon?.let { canvas.drawBitmap(it, iconCenterX - gridIconSize / 2f, iconTop, null) }
        canvas.drawText(displayLabel, iconCenterX, labelBaseline, appLabelPaint)
    }

    private fun utilityPanelRect(panelWidth: Float, rows: Int): RectF {
        val gh = rows * cellHeight + gridPadding * 2 + dp(28f)
        val gw = panelWidth
        val rail = zoneLayout.indexRailRect()
        var top = rail.centerY() - gh / 2f
        top = top.coerceSafe(dp(16f), height - gh - dp(16f))
        val gap = dp(8f)
        val left = when (side) {
            PanelSide.LEFT -> rail.right + gap
            PanelSide.RIGHT -> rail.left - gap - gw
        }
        return RectF(left, top, left + gw, top + gh)
    }

    private fun resolveTaskSwitcherAnchorLocalY(): Float {
        val rawY = taskSwitcherAnchorRawY ?: pathRecognizer.gestureStartRawY()
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val anchorY = rawY - loc[1]
        val trigger = zoneLayout.triggerZoneRect()
        return anchorY.coerceIn(trigger.top, trigger.bottom)
    }

    private fun taskSwitcherAnchorLocalY(): Float =
        taskSwitcherFrozenAnchorLocalY ?: resolveTaskSwitcherAnchorLocalY()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun invalidateTaskSwitcherPanel() {
        if (gestureSession.panelMode() == OverlayPanelMode.TASK_SWITCHER) {
            invalidate()
        }
    }

    private fun indexPanelContentRect(): RectF {
        val rail = zoneLayout.indexRailRect()
        if (indexSession.selectedLetter == null) {
            return when (side) {
                PanelSide.LEFT -> RectF(rail.left, rail.top, rail.right + dp(240f), rail.bottom)
                PanelSide.RIGHT -> RectF(rail.left - dp(240f), rail.top, rail.right, rail.bottom)
            }
        }
        val grid = gridPopupRect()
        val bubble = bubbleCenter()
        return RectF(
            minOf(rail.left, grid.left, bubble.x - bubbleRadius),
            minOf(rail.top, grid.top, bubble.y - bubbleRadius),
            maxOf(rail.right, grid.right, bubble.x + bubbleRadius),
            maxOf(rail.bottom, grid.bottom, bubble.y + bubbleRadius),
        )
    }

    private fun quickLauncherPanelRect(): RectF {
        val items = quickLauncherItems()
        if (items.isEmpty()) return RectF()
        val layout = gridLayoutInfo(items.size)
        return utilityPanelRect(layout.panelWidth, layout.rows)
    }

    private fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit) {
        if (panelEnterProgress >= 1f || contentRect.isEmpty) {
            drawContent()
            return
        }
        val offsetX = panelEnterOffsetX(contentRect)
        val alpha = (255 * panelEnterProgress).toInt().coerceIn(0, 255)
        val layer = canvas.saveLayerAlpha(null, alpha)
        canvas.translate(offsetX, 0f)
        drawContent()
        canvas.restoreToCount(layer)
    }

    private fun panelEnterOffsetX(panel: RectF): Float {
        val delta = 1f - panelEnterProgress
        val slide = panel.width() + dp(PANEL_ENTER_OFFSCREEN_MARGIN_DP)
        return when (side) {
            PanelSide.LEFT -> -slide * delta
            PanelSide.RIGHT -> slide * delta
        }
    }

    private fun panelEnterAdjustedX(localX: Float, panel: RectF): Float =
        if (panelEnterProgress >= 1f || panel.isEmpty) localX else localX - panelEnterOffsetX(panel)

    private fun cancelPanelEnterAnimation() {
        panelEnterAnimator?.cancel()
        panelEnterAnimator = null
    }

    private fun startPanelEnterAnimation() {
        cancelPanelEnterAnimation()
        panelEnterProgress = 0f
        panelEnterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PANEL_ENTER_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                panelEnterProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    companion object {
        private const val TASK_SWITCHER_LONG_PRESS_MS = 450L
        private const val PANEL_ENTER_DURATION_MS = 180L
        private const val PANEL_ENTER_OFFSCREEN_MARGIN_DP = 16f
    }
}
