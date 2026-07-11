package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherDefaults
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings

internal class QuickLauncherPanelController(
    private val host: Host,
) {
    interface Host {
        val context: Context
        fun settings(): AppSettings
        fun side(): PanelSide
        fun apps(): List<AppInfo>
        fun isPanelReady(): Boolean
        fun isAddDialogShowing(): Boolean
        fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun invalidate()
        fun hapticTick()
        fun showAddDialog(
            configuredAppPackages: Set<String>,
            configuredShortcutKeys: Set<String>,
            configuredActionKeys: Set<String>,
            onAdd: (QuickLauncherItem) -> Unit,
            onRemove: (QuickLauncherItem) -> Unit,
        )
        fun onPersist(items: List<QuickLauncherItem>)
        fun quickLauncherPageSize(): Int
        fun onEditDragMove(touchX: Float, localY: Float, panelRect: RectF)
        fun onEditDragBegan()
        fun resolveEditDragTargetGlobal(touchX: Float, localY: Float, panelRect: RectF): Int
    }

    typealias ToolbarAction = QuickLauncherPanelToolbar.ToolbarAction
    typealias ToolbarLayoutMetrics = QuickLauncherPanelToolbar.ToolbarLayoutMetrics

    private val toolbar = QuickLauncherPanelToolbar(this, host)
    private val management = QuickLauncherPanelManagementHandler(this, host, toolbar)

    var editMode: Boolean = false
        private set

    var itemPageOffset: Int = 0
        internal set

    private var localItems: List<QuickLauncherItem> = emptyList()
    private var defaultsPersisted = false

    fun reset() {
        editMode = false
        toolbar.reset()
        management.reset()
    }

    fun setEditMode(enabled: Boolean) {
        if (editMode == enabled) return
        editMode = enabled
        if (!enabled) {
            management.onEditModeDisabled()
            toolbar.onEditModeDisabled()
            if (localItems.isNotEmpty()) {
                persistLocalItems()
            }
        }
        host.invalidate()
    }

    fun setItemPageOffset(offset: Int) {
        itemPageOffset = offset.coerceAtLeast(0)
    }

    fun syncSettings(settings: AppSettings) {
        if (!editMode && !management.isDragging()) {
            val configured = configuredItems(settings)
            if (localItems.isEmpty() || configured == localItems) {
                localItems = configured
            }
        }
    }

    fun displayItems(settings: AppSettings): List<QuickLauncherItem> {
        if (localItems.isNotEmpty()) return localItems
        return QuickLauncherDefaults.effectiveItems(configuredItems(settings), host.apps())
    }

    internal fun displayItems(): List<QuickLauncherItem> = displayItems(host.settings())

    fun ensureDefaultsPersisted(settings: AppSettings) {
        if (defaultsPersisted || configuredItems(settings).isNotEmpty()) return
        val defaults = QuickLauncherDefaults.fromApps(host.apps())
        if (defaults.isEmpty()) return
        defaultsPersisted = true
        localItems = defaults
        host.onPersist(defaults)
    }

    fun shouldShowToolbar(settings: AppSettings): Boolean = toolbar.shouldShowToolbar()
    fun toolbarLayoutMetrics(): ToolbarLayoutMetrics = toolbar.toolbarLayoutMetrics()
    fun contentReserveWidth(settings: AppSettings): Float = toolbar.contentReserveWidth()
    fun toolbarBounds(): RectF = toolbar.toolbarBounds()
    fun toolbarContains(localX: Float, localY: Float): Boolean = toolbar.toolbarContains(localX, localY)
    fun combinedContentRect(panelRect: RectF): RectF = toolbar.combinedContentRect(panelRect)
    fun layoutToolbar(panelRect: RectF) = toolbar.layoutToolbar(panelRect)
    fun drawToolbar(canvas: Canvas, panelRect: RectF) = toolbar.drawToolbar(canvas, panelRect)
    fun layoutDeleteBadges(cells: List<RectF>) =
        toolbar.layoutDeleteBadges(cells, management.dragSourceIndex())
    fun drawDeleteBadges(canvas: Canvas) = toolbar.drawDeleteBadges(canvas)

    fun resolveToolbarAction(localX: Float, localY: Float, panelRect: RectF): ToolbarAction? =
        toolbar.resolveToolbarAction(localX, localY, panelRect)

    fun commitToolbarAtRelease(
        localX: Float,
        localY: Float,
        panelRect: RectF,
        tapGesture: Boolean,
        toolbarCommitAllowed: Boolean,
        allowSlideRelease: Boolean = false,
    ): Boolean {
        val handled = toolbar.commitToolbarAtRelease(
            localX = localX,
            localY = localY,
            panelRect = panelRect,
            tapGesture = tapGesture,
            toolbarCommitAllowed = toolbarCommitAllowed,
            allowSlideRelease = allowSlideRelease,
        )
        if (handled) {
            management.reset()
            host.invalidate()
        }
        return handled
    }

    fun handleManagementTouch(
        event: MotionEvent,
        localX: Float,
        localY: Float,
        panelRect: RectF,
        cellBounds: List<Pair<Any, RectF>>,
        tapGesture: Boolean = false,
        toolbarCommitAllowed: Boolean = true,
    ): Boolean = management.handleManagementTouch(
        event = event,
        localX = localX,
        localY = localY,
        panelRect = panelRect,
        cellBounds = cellBounds,
        tapGesture = tapGesture,
        toolbarCommitAllowed = toolbarCommitAllowed,
    )

    fun isDragging(): Boolean = management.isDragging()
    fun dragSourceIndex(): Int = management.dragSourceIndex()
    fun dragDestinationIndex(): Int = management.dragDestinationIndex()
    fun dragSourceGlobal(): Int = management.dragSourceGlobal()
    fun dragDestinationGlobal(): Int = management.dragDestinationGlobal()
    fun dragSourceOnPage(pageStart: Int, pageSize: Int): Boolean =
        management.dragSourceOnPage(pageStart, pageSize)
    fun dragSourceOnCurrentPage(): Boolean = management.dragSourceOnCurrentPage()
    fun dragPointerX(): Float = management.dragPointerX()
    fun dragPointerY(): Float = management.dragPointerY()
    fun dragVisualOffsetForPage(pageStart: Int, pageSize: Int): Pair<Float, Float> =
        management.dragVisualOffsetForPage(pageStart, pageSize)
    fun dragVisualOffset(index: Int): Pair<Float, Float> = management.dragVisualOffset(index)
    fun syncPageLocalDragTarget() = management.syncPageLocalDragTarget()

    internal fun openAddDialog() {
        if (host.isAddDialogShowing()) return
        val items = workingItems()
        val configuredAppPackages = items
            .filter { it.type == QuickLauncherItemType.APP }
            .map { it.payload }
            .toSet()
        val configuredShortcutKeys = items
            .filter { it.type == QuickLauncherItemType.SHORTCUT }
            .mapNotNull { item -> QuickLauncherItemCodec.shortcutItemKey(item) }
            .toSet()
        val configuredActionKeys = items
            .filter { it.type == QuickLauncherItemType.ACTION }
            .mapNotNull { QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) }
            .toSet()
        host.showAddDialog(
            configuredAppPackages,
            configuredShortcutKeys,
            configuredActionKeys,
            onAdd = { added ->
                localItems = workingItems() + added
                persistLocalItems()
                host.invalidate()
            },
            onRemove = { removed ->
                val current = workingItems()
                val removeIndex = current.indexOfFirst { item ->
                    item.type == removed.type && item.payload == removed.payload
                }
                if (removeIndex >= 0) {
                    localItems = current.filterIndexed { index, _ -> index != removeIndex }
                    persistLocalItems()
                    host.invalidate()
                }
            },
        )
    }

    internal fun workingItems(): List<QuickLauncherItem> {
        if (localItems.isNotEmpty()) return localItems
        return QuickLauncherDefaults.effectiveItems(configuredItems(host.settings()), host.apps())
    }

    internal fun removeItemAt(pageLocalIndex: Int) {
        val index = itemPageOffset + pageLocalIndex
        val current = workingItems()
        if (index !in current.indices) return
        localItems = current.filterIndexed { i, _ -> i != index }
        host.hapticTick()
        persistLocalItems()
        host.invalidate()
    }

    internal fun moveItemGlobal(from: Int, to: Int) {
        val current = workingItems().toMutableList()
        if (from !in current.indices || to !in 0..current.size) return
        if (from == to) return
        val item = current.removeAt(from)
        current.add(to.coerceIn(0, current.size), item)
        localItems = current
        persistLocalItems()
    }

    private fun persistLocalItems() {
        val items = localItems.ifEmpty { return }
        host.onPersist(items)
    }

    private fun configuredItems(settings: AppSettings): List<QuickLauncherItem> =
        settings.quickLauncher

    fun collectAccessibilityNodes(context: Context, panelRect: RectF): List<OverlayVirtualNode> =
        toolbar.collectAccessibilityNodes(context, panelRect)
}
