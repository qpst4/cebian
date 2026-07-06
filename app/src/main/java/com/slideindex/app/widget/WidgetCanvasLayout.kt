package com.slideindex.app.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class WidgetCanvasLayout(context: Context) : ViewGroup(context) {

  var editMode: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      cancelPendingLongPress()
      cancelPendingDrag()
      cancelBrowseLongPress()
      if (!value) {
        resetTouchInteractionState()
      }
      setWillNotDraw(!value)
      refreshEditChrome()
      invalidate()
    }

  var onLongPressBlank: (() -> Unit)? = null
  var onTapBlank: (() -> Unit)? = null
  var onItemChanged: ((WidgetPanelItem) -> Unit)? = null
  var onPageCommitted: ((WidgetPanelPage) -> Unit)? = null
  var onItemRemoved: ((Int) -> Unit)? = null
  var onConfigureWidget: ((Int) -> Unit)? = null
  var onAddWidgetRequested: (() -> Unit)? = null
  var onInteractionActiveChange: ((Boolean) -> Unit)? = null

  var pageColumnCount: Int = 4
    private set
  var pageRowCount: Int = 26
    private set
  var currentGridStepPx: Int = 0
    private set
  /** One span width in pixels; distance between adjacent grid dots. */
  val gridStepPx: Int get() = currentGridStepPx

  private var boundPage: WidgetPanelPage? = null
  private var boundHostContext: Context? = null
  val currentPage: WidgetPanelPage? get() = boundPage

  private val gridDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x55FFFFFF
    style = Paint.Style.FILL
  }
  private val gridDotEditPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x99FFFFFF.toInt()
    style = Paint.Style.FILL
  }
  private val emptySlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x66FFFFFF
    style = Paint.Style.STROKE
    strokeWidth = 1.5f * resources.displayMetrics.density
  }
  private val dragPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x664CAF50
    style = Paint.Style.FILL
  }
  private val resizePreviewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x33FFFFFF
    style = Paint.Style.FILL
  }
  private val resizePreviewStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xCCFFFFFF.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 2f * resources.displayMetrics.density
    pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
  }

  private var draggingChild: WidgetCardContainer? = null
  private var draggingItem: WidgetPanelItem? = null
  private var dragTouchOffsetX = 0f
  private var dragTouchOffsetY = 0f
  private var hoverCellX = -1
  private var hoverCellY = -1
  private var interactionActive = false
  private var chromeTouchTarget: WidgetCardContainer? = null

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
  private var blankTouchTracking = false
  private var blankTouchDownX = 0f
  private var blankTouchDownY = 0f
  private var pendingLongPress: Runnable? = null

  private var pendingDragChild: WidgetCardContainer? = null
  private var pendingDragDownX = 0f
  private var pendingDragDownY = 0f
  private var pendingDragLongPress: Runnable? = null
  private var browseTouchChild: WidgetCardContainer? = null
  private var browseTouchDownX = 0f
  private var browseTouchDownY = 0f
  private var browseTouchTracking = false
  private var browseLongPressConsumed = false
  private var pendingBrowseLongPress: Runnable? = null

  private val nestedScrollChildHelper = NestedScrollingChildHelper(this)
  private var panelScrollLastY = 0f
  private var panelScrollDownX = 0f
  private var panelScrollDownY = 0f
  private var panelScrollActive = false

  init {
    clipChildren = false
    clipToPadding = false
    setWillNotDraw(true)
    isNestedScrollingEnabled = true
    nestedScrollChildHelper.isNestedScrollingEnabled = true
  }

  private var lastGridStepPx = 0

  fun bind(page: WidgetPanelPage, hostContext: Context) {
    boundHostContext = hostContext
    boundPage = page
    pageColumnCount = page.columnCount
    pageRowCount = page.rowCount
    lastBindKey = bindKeyFor(page)

    removeAllViews()
    for (item in page.items) {
      addWidgetCard(hostContext, item)
    }
    post { refreshAllWidgetLayouts() }
    requestLayout()
    invalidate()
  }

  fun removeWidgetCard(appWidgetId: Int) {
    val page = boundPage ?: return
    if (page.items.none { it.appWidgetId == appWidgetId }) return
    boundPage = WidgetPanelGridLogic.removeItem(page, appWidgetId)
    for (i in childCount - 1 downTo 0) {
      val child = getChildAt(i) as? WidgetCardContainer ?: continue
      if (child.item.appWidgetId == appWidgetId) {
        removeViewAt(i)
        break
      }
    }
    boundPage?.let { lastBindKey = bindKeyFor(it) }
    requestLayout()
    invalidate()
  }

  fun addWidgetCard(hostContext: Context, item: WidgetPanelItem): WidgetCardContainer {
    val hostView = WidgetPopupHost.obtainHostView(hostContext, item.appWidgetId)
    val container = WidgetCardContainer(context, item, hostView).apply {
      onDelete = {
        val widgetId = this.item.appWidgetId
        removeWidgetCard(widgetId)
        onItemRemoved?.invoke(widgetId)
      }
      onConfigure = { onConfigureWidget?.invoke(item.appWidgetId) }
      onResize = { spanX, spanY ->
        commitItemChange(this.item.copy(spanX = spanX, spanY = spanY))
      }
      onInteractionActiveChange = { active -> setInteractionActive(active) }
      setEditMode(editMode)
      layoutParams = LayoutParams(0, 0)
    }
    addView(container)
    container.post { container.updateScalableTargetFromContainer() }
    return container
  }

  fun refreshAllWidgetLayouts() {
    for (i in 0 until childCount) {
      (getChildAt(i) as? WidgetCardContainer)?.refreshWidgetLayout(force = true)
    }
  }

  private fun syncItemsFromPage(page: WidgetPanelPage) {
    for (i in 0 until childCount) {
      val child = getChildAt(i) as? WidgetCardContainer ?: continue
      val updated = page.items.find { it.appWidgetId == child.item.appWidgetId } ?: continue
      if (child.item == updated) continue
      child.syncItem(updated)
      if (!child.isPreviewingResize()) {
        child.post { child.refreshWidgetLayout(force = true) }
      }
    }
  }

  private fun applyPageGeometry(page: WidgetPanelPage) {
    boundPage = page
    pageColumnCount = page.columnCount
    pageRowCount = page.rowCount
    syncItemsFromPage(page)
    requestLayout()
    invalidate()
    post { refreshAllWidgetLayouts() }
  }

  private fun applyPageItems(page: WidgetPanelPage, hostContext: Context) {
    boundPage = page
    var structureChanged = false
    for (i in childCount - 1 downTo 0) {
      val child = getChildAt(i) as? WidgetCardContainer ?: continue
      if (page.items.none { it.appWidgetId == child.item.appWidgetId }) {
        removeViewAt(i)
        structureChanged = true
      }
    }
    syncItemsFromPage(page)
    for (item in page.items) {
      if (findChildByWidgetId(item.appWidgetId) == null) {
        addWidgetCard(hostContext, item)
        structureChanged = true
      }
    }
    requestLayout()
    invalidate()
    if (structureChanged) {
      post { refreshAllWidgetLayouts() }
    }
  }

  fun notifyResizePreviewChanged() {
    invalidate()
  }

  fun commitItemChange(item: WidgetPanelItem) {
    val page = boundPage ?: return
    if (!WidgetPanelGridLogic.isAreaFree(
        page,
        item.x,
        item.y,
        item.spanX,
        item.spanY,
        item.appWidgetId,
      )
    ) {
      requestLayout()
      return
    }
    val updatedPage = WidgetPanelGridLogic.upsertItem(page, item)
    boundPage = updatedPage
    lastBindKey = bindKeyFor(updatedPage)
    findChildByWidgetId(item.appWidgetId)?.let { child ->
      child.syncItem(item)
      child.refreshWidgetLayout(force = true)
    }
    requestLayout()
    onPageCommitted?.invoke(updatedPage)
    onItemChanged?.invoke(item)
  }

  private fun findChildByWidgetId(appWidgetId: Int): WidgetCardContainer? {
    for (i in 0 until childCount) {
      val child = getChildAt(i) as? WidgetCardContainer ?: continue
      if (child.item.appWidgetId == appWidgetId) return child
    }
    return null
  }

  private fun bindKeyFor(page: WidgetPanelPage): BindKey = BindKey(
    pageId = page.id,
    itemsSignature = itemsSignature(page),
    columnCount = page.columnCount,
    rowCount = page.rowCount,
  )

  private fun itemsSignature(page: WidgetPanelPage): String =
    page.items.joinToString("|") {
      "${it.appWidgetId}:${it.x},${it.y},${it.spanX},${it.spanY}"
    }

  private fun cancelPendingDrag() {
    pendingDragLongPress?.let { removeCallbacks(it) }
    pendingDragLongPress = null
    pendingDragChild = null
  }

  private fun cancelBrowseLongPress() {
    pendingBrowseLongPress?.let { removeCallbacks(it) }
    pendingBrowseLongPress = null
    browseTouchChild = null
    browseTouchTracking = false
    browseLongPressConsumed = false
  }

  private fun scheduleBrowseLongPress(child: WidgetCardContainer, x: Float, y: Float) {
    cancelBrowseLongPress()
    browseTouchChild = child
    browseTouchDownX = x
    browseTouchDownY = y
    browseTouchTracking = true
    pendingBrowseLongPress = Runnable {
      if (!browseTouchTracking || editMode) return@Runnable
      val target = browseTouchChild ?: return@Runnable
      if (findTouchTarget(browseTouchDownX, browseTouchDownY) !== target) return@Runnable
      browseLongPressConsumed = true
      browseTouchTracking = false
      val cancel = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
      deliverTouchToChild(target, cancel)
      cancel.recycle()
      onLongPressBlank?.invoke()
    }
    postDelayed(pendingBrowseLongPress!!, longPressTimeout)
  }

  private fun handleBrowseModeTouch(event: MotionEvent): Boolean {
    if (editMode) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val child = findTouchTarget(event.x, event.y)
        if (child != null) {
          scheduleBrowseLongPress(child, event.x, event.y)
        } else {
          cancelBrowseLongPress()
        }
        return false
      }
      MotionEvent.ACTION_MOVE -> {
        if (!browseTouchTracking) return false
        val dx = event.x - browseTouchDownX
        val dy = event.y - browseTouchDownY
        if (dx * dx + dy * dy > touchSlop * touchSlop) {
          cancelBrowseLongPress()
        }
        return false
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (browseLongPressConsumed) {
          browseLongPressConsumed = false
          cancelBrowseLongPress()
          return true
        }
        cancelBrowseLongPress()
        return false
      }
    }
    return false
  }

  private fun stopPanelScroll() {
    if (panelScrollActive || nestedScrollChildHelper.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)) {
      nestedScrollChildHelper.stopNestedScroll(ViewCompat.TYPE_TOUCH)
    }
    panelScrollActive = false
  }

  private fun dispatchPanelNestedScroll(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_MOVE -> {
        val dy = (panelScrollLastY - event.y).toInt()
        panelScrollLastY = event.y
        if (dy == 0) return panelScrollActive
        val consumed = IntArray(2)
        nestedScrollChildHelper.dispatchNestedPreScroll(
          0,
          dy,
          consumed,
          null,
          ViewCompat.TYPE_TOUCH,
        )
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        stopPanelScroll()
        return panelScrollActive
      }
    }
    return false
  }

  private fun maybeStartPanelScroll(event: MotionEvent): Boolean {
    val dx = event.x - panelScrollDownX
    val dy = event.y - panelScrollDownY
    if (dx * dx + dy * dy <= touchSlop * touchSlop) return false
    if (abs(dy) <= abs(dx)) return false
    cancelPendingDrag()
    cancelPendingLongPress()
    cancelBrowseLongPress()
    blankTouchTracking = false
    if (!panelScrollActive) {
      panelScrollActive = nestedScrollChildHelper.startNestedScroll(
        ViewCompat.SCROLL_AXIS_VERTICAL,
        ViewCompat.TYPE_TOUCH,
      )
    }
    return panelScrollActive
  }

  private fun handleEditModePanelTouch(event: MotionEvent): Boolean {
    if (draggingChild != null) {
      return onTouchEvent(event)
    }

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        panelScrollDownX = event.x
        panelScrollDownY = event.y
        panelScrollLastY = event.y
        panelScrollActive = false
        val child = findTouchTarget(event.x, event.y)
        if (child != null) {
          val localX = event.x - child.left - child.translationX
          val localY = event.y - child.top - child.translationY
          if (!child.isTouchOnChrome(localX, localY)) {
            scheduleWidgetDragLongPress(child, event.x, event.y)
          }
        }
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (maybeStartPanelScroll(event)) {
          return dispatchPanelNestedScroll(event)
        }
        if (pendingDragChild != null) {
          val dx = event.x - pendingDragDownX
          val dy = event.y - pendingDragDownY
          if (dx * dx + dy * dy > touchSlop * touchSlop) {
            cancelPendingDrag()
          }
        }
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        cancelPendingDrag()
        stopPanelScroll()
        return true
      }
    }
    return true
  }

  private fun scheduleWidgetDragLongPress(child: WidgetCardContainer, x: Float, y: Float) {
    cancelPendingDrag()
    pendingDragChild = child
    pendingDragDownX = x
    pendingDragDownY = y
    pendingDragLongPress = Runnable {
      val target = pendingDragChild
      if (target !== child || draggingChild != null || !editMode) {
        cancelPendingDrag()
        return@Runnable
      }
      pendingDragChild = null
      pendingDragLongPress = null
      startDrag(child, x, y)
    }
    postDelayed(pendingDragLongPress!!, longPressTimeout)
  }

  private fun cancelPendingLongPress() {
    pendingLongPress?.let { removeCallbacks(it) }
    pendingLongPress = null
  }

  private fun scheduleBlankLongPress() {
    cancelPendingLongPress()
    pendingLongPress = Runnable {
      if (!blankTouchTracking || editMode) return@Runnable
      if (findTouchTarget(blankTouchDownX, blankTouchDownY) == null) {
        onLongPressBlank?.invoke()
      }
      blankTouchTracking = false
    }
    postDelayed(pendingLongPress!!, longPressTimeout)
  }

  private fun handleBlankAreaTouch(event: MotionEvent): Boolean {
    if (editMode) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        if (findTouchTarget(event.x, event.y) != null) return false
        blankTouchTracking = true
        blankTouchDownX = event.x
        blankTouchDownY = event.y
        scheduleBlankLongPress()
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (!blankTouchTracking) return false
        val dx = event.x - blankTouchDownX
        val dy = event.y - blankTouchDownY
        if (dx * dx + dy * dy > touchSlop * touchSlop) {
          cancelPendingLongPress()
          blankTouchTracking = false
        }
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (!blankTouchTracking) return false
        cancelPendingLongPress()
        val tappedBlank = event.actionMasked == MotionEvent.ACTION_UP &&
          findTouchTarget(event.x, event.y) == null
        blankTouchTracking = false
        if (tappedBlank) {
          onTapBlank?.invoke()
        }
        return true
      }
    }
    return false
  }

  private fun refreshEditChrome() {
    for (i in 0 until childCount) {
      (getChildAt(i) as? WidgetCardContainer)?.setEditMode(editMode)
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val density = resources.displayMetrics.density
    val widthMode = MeasureSpec.getMode(widthMeasureSpec)
    val widthSize = MeasureSpec.getSize(widthMeasureSpec)
    val heightMode = MeasureSpec.getMode(heightMeasureSpec)
    val heightSize = MeasureSpec.getSize(heightMeasureSpec)
    val preferredCellPx = 1

    val innerWidthAvailable = if (widthMode != MeasureSpec.UNSPECIFIED && widthSize > 0) {
      widthSize - paddingLeft - paddingRight
    } else {
      0
    }

    currentGridStepPx = if (innerWidthAvailable > 0 && pageColumnCount > 0) {
      WidgetSizeHelper.computeGridStepPx(innerWidthAvailable, pageColumnCount)
    } else {
      preferredCellPx
    }

    val contentW = pageColumnCount * currentGridStepPx
    val contentH = pageRowCount * currentGridStepPx
    val desiredW = paddingLeft + paddingRight + contentW
    val desiredH = paddingTop + paddingBottom + contentH

    val measuredW = when (widthMode) {
      MeasureSpec.EXACTLY -> widthSize
      MeasureSpec.AT_MOST -> min(widthSize, desiredW)
      else -> desiredW
    }
    val measuredH = when (heightMode) {
      MeasureSpec.EXACTLY -> heightSize
      MeasureSpec.AT_MOST -> min(heightSize, desiredH)
      else -> desiredH
    }
    setMeasuredDimension(measuredW, measuredH)

    for (i in 0 until childCount) {
      val child = getChildAt(i) as? WidgetCardContainer ?: continue
      val childW = child.previewLayoutWidthPx(currentGridStepPx)
      val childH = child.previewLayoutHeightPx(currentGridStepPx)
      child.measure(
        MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY)
      )
    }
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    for (i in 0 until childCount) {
      val child = getChildAt(i) as? WidgetCardContainer ?: continue
      if (child == draggingChild) continue // Handled by translation
      val item = child.item
      val left = paddingLeft + item.x * currentGridStepPx
      val top = paddingTop + item.y * currentGridStepPx
      child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
    }
    if (currentGridStepPx != lastGridStepPx) {
      lastGridStepPx = currentGridStepPx
      if (!interactionActive && !browseTouchTracking) {
        post { refreshAllWidgetLayouts() }
      }
    }
  }

  private fun findTouchTarget(x: Float, y: Float): WidgetCardContainer? {
    for (i in childCount - 1 downTo 0) {
      val child = getChildAt(i) as? WidgetCardContainer ?: continue
      val localX = x - child.left - child.translationX
      val localY = y - child.top - child.translationY
      if (child.isTouchOnChrome(localX, localY)) return child
      val left = child.left + child.translationX
      val top = child.top + child.translationY
      if (x >= left && x <= left + child.width && y >= top && y <= top + child.height) {
        return child
      }
    }
    return null
  }

  private fun deliverTouchToChild(child: WidgetCardContainer, event: MotionEvent): Boolean {
    val transformed = MotionEvent.obtain(event)
    transformed.offsetLocation(
      -child.left - child.translationX,
      -child.top - child.translationY,
    )
    val handled = child.dispatchTouchEvent(transformed)
    transformed.recycle()
    return handled
  }

  private fun setInteractionActive(active: Boolean) {
    if (interactionActive == active) return
    interactionActive = active
    onInteractionActiveChange?.invoke(active)
    if (!active && !anyChildPreviewingResize()) {
      post { refreshAllWidgetLayouts() }
    }
  }

  private fun resetTouchInteractionState() {
    cancelPendingLongPress()
    cancelPendingDrag()
    cancelBrowseLongPress()
    blankTouchTracking = false
    chromeTouchTarget = null
    panelScrollActive = false
    stopPanelScroll()
    requestDisallowInterceptAllParents(false)
    if (draggingChild != null) {
      val child = draggingChild!!
      child.animate().cancel()
      child.scaleX = 1f
      child.scaleY = 1f
      child.alpha = 1f
      child.translationZ = 0f
      child.translationX = 0f
      child.translationY = 0f
      draggingChild = null
      draggingItem = null
      hoverCellX = -1
      hoverCellY = -1
    }
    if (interactionActive) {
      interactionActive = false
      onInteractionActiveChange?.invoke(false)
      post { refreshAllWidgetLayouts() }
    }
  }

  private fun anyChildPreviewingResize(): Boolean {
    for (i in 0 until childCount) {
      if ((getChildAt(i) as? WidgetCardContainer)?.isPreviewingResize() == true) return true
    }
    return false
  }

  private fun startDrag(child: WidgetCardContainer, x: Float, y: Float) {
    draggingChild = child
    draggingItem = child.item
    dragTouchOffsetX = x - (child.left + child.translationX)
    dragTouchOffsetY = y - (child.top + child.translationY)
    child.bringToFront()
    child.translationZ = 8f * resources.displayMetrics.density
    child.animate().cancel()
    child.scaleX = 1.05f
    child.scaleY = 1.05f
    child.alpha = 0.88f
    setInteractionActive(true)
    requestDisallowInterceptAllParents(true)
    updateHoverCell(x, y)
    invalidate()
  }

  private fun endDrag(child: WidgetCardContainer) {
    val item = draggingItem ?: return
    val page = boundPage
    child.animate().cancel()
    child.scaleX = 1f
    child.scaleY = 1f
    child.alpha = 1f
    child.translationZ = 0f
    setInteractionActive(false)
    requestDisallowInterceptAllParents(false)
    if (page != null &&
      (hoverCellX != item.x || hoverCellY != item.y) &&
      WidgetPanelGridLogic.isAreaFree(page, hoverCellX, hoverCellY, item.spanX, item.spanY, item.appWidgetId)
    ) {
      child.translationX = 0f
      child.translationY = 0f
      commitItemChange(item.copy(x = hoverCellX, y = hoverCellY))
    } else {
      child.translationX = 0f
      child.translationY = 0f
      requestLayout()
    }
    draggingChild = null
    draggingItem = null
    hoverCellX = -1
    hoverCellY = -1
    invalidate()
  }

  private fun requestDisallowInterceptAllParents(disallow: Boolean) {
    var current: android.view.ViewParent? = parent
    while (current != null) {
      current.requestDisallowInterceptTouchEvent(disallow)
      current = current.parent
    }
  }

  private fun updateHoverCell(x: Float, y: Float) {
    val step = currentGridStepPx
    val item = draggingItem ?: return
    val topLeftX = x - dragTouchOffsetX - paddingLeft
    val topLeftY = y - dragTouchOffsetY - paddingTop
    hoverCellX = (topLeftX / step).toInt().coerceIn(0, pageColumnCount - item.spanX)
    hoverCellY = (topLeftY / step).toInt().coerceIn(0, pageRowCount - item.spanY)
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    val chromeTarget = chromeTouchTarget
    if (chromeTarget != null) {
      val handled = deliverTouchToChild(chromeTarget, event)
      if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
        chromeTouchTarget = null
      }
      return handled
    }

    if (editMode && draggingChild == null && event.actionMasked == MotionEvent.ACTION_DOWN) {
      val child = findTouchTarget(event.x, event.y)
      if (child != null) {
        val localX = event.x - child.left - child.translationX
        val localY = event.y - child.top - child.translationY
        if (child.isTouchOnChrome(localX, localY)) {
          chromeTouchTarget = child
          return deliverTouchToChild(child, event)
        }
      }
    }
    if (editMode && draggingChild == null) {
      return handleEditModePanelTouch(event)
    }
    if (draggingChild != null) {
      return onTouchEvent(event)
    }
    if (!editMode) {
      if (handleBrowseModeTouch(event)) return true
    }
    return super.dispatchTouchEvent(event)
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (draggingChild != null) return true

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        panelScrollDownX = event.x
        panelScrollDownY = event.y
        panelScrollLastY = event.y
        panelScrollActive = false
        if (!editMode) return false
        val child = findTouchTarget(event.x, event.y)
        if (child != null) {
          val localX = event.x - child.left - child.translationX
          val localY = event.y - child.top - child.translationY
          if (!child.isTouchOnChrome(localX, localY)) {
            scheduleWidgetDragLongPress(child, event.x, event.y)
          }
        }
      }
      MotionEvent.ACTION_MOVE -> {
        if (editMode) {
          if (pendingDragChild != null && draggingChild == null) {
            val dx = event.x - pendingDragDownX
            val dy = event.y - pendingDragDownY
            if (dx * dx + dy * dy > touchSlop * touchSlop) {
              cancelPendingDrag()
            }
          }
          return draggingChild != null
        }
        if (maybeStartPanelScroll(event)) {
          return true
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        cancelPendingDrag()
        if (panelScrollActive) {
          stopPanelScroll()
          return true
        }
      }
    }
    return draggingChild != null || panelScrollActive
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (panelScrollActive && draggingChild == null) {
      when (event.actionMasked) {
        MotionEvent.ACTION_MOVE -> return dispatchPanelNestedScroll(event)
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          stopPanelScroll()
          return true
        }
      }
    }

    if (draggingChild != null) {
      val child = draggingChild!!
      when (event.actionMasked) {
        MotionEvent.ACTION_MOVE -> {
          val left = event.x - dragTouchOffsetX
          val top = event.y - dragTouchOffsetY
          child.translationX = left - child.left
          child.translationY = top - child.top
          updateHoverCell(event.x, event.y)
          invalidate()
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag(child)
      }
      return true
    }

    if (!editMode) {
      if (handleBlankAreaTouch(event)) return true
    }
    return false
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (currentGridStepPx <= 0) return
    val step = currentGridStepPx.toFloat()
    val radiusPx = (if (editMode) 2.5f else 2f) * resources.displayMetrics.density
    val dotPaint = if (editMode) gridDotEditPaint else gridDotPaint
    val cornerRadius = 16f * resources.displayMetrics.density
    val cols = pageColumnCount
    val rows = pageRowCount

    for (y in 0..rows) {
      for (x in 0..cols) {
        val cx = paddingLeft + x * step
        val cy = paddingTop + y * step
        canvas.drawCircle(cx, cy, radiusPx, dotPaint)
      }
    }

    if (!editMode) return

    for (i in 0 until childCount) {
      val child = getChildAt(i) as? WidgetCardContainer ?: continue
      if (!child.isPreviewingResize()) continue
      val item = child.item
      val spanX = child.layoutSpanX()
      val spanY = child.layoutSpanY()
      val page = boundPage
      val isFree = page == null ||
        WidgetPanelGridLogic.isAreaFree(page, item.x, item.y, spanX, spanY, item.appWidgetId)
      val left = paddingLeft + item.x * step
      val top = paddingTop + item.y * step
      val right = left + spanX * step
      val bottom = top + spanY * step
      val rect = RectF(left, top, right, bottom)
      resizePreviewFillPaint.color = if (isFree) 0x33FFFFFF else 0x33F44336
      resizePreviewStrokePaint.color = if (isFree) 0xCCFFFFFF.toInt() else 0xCCF44336.toInt()
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, resizePreviewFillPaint)
      canvas.drawRoundRect(rect, cornerRadius, cornerRadius, resizePreviewStrokePaint)
    }

    // Draw hover preview for drag
    if (draggingChild != null && hoverCellX >= 0 && hoverCellY >= 0) {
      val item = draggingItem!!
      val page = boundPage ?: return
      val isFree = WidgetPanelGridLogic.isAreaFree(page, hoverCellX, hoverCellY, item.spanX, item.spanY, item.appWidgetId)
      dragPreviewPaint.color = if (isFree) 0x664CAF50 else 0x66F44336
      val left = paddingLeft + hoverCellX * step
      val top = paddingTop + hoverCellY * step
      val right = left + item.spanX * step
      val bottom = top + item.spanY * step
      canvas.drawRoundRect(RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()), cornerRadius, cornerRadius, dragPreviewPaint)
    }
  }

  private var lastBindKey: BindKey? = null

  fun bindIfNeeded(page: WidgetPanelPage, hostContext: Context) {
    boundHostContext = hostContext
    if (interactionActive || draggingChild != null || anyChildPreviewingResize()) return
    val resolvedPage = resolvePageForBind(page)
    val key = bindKeyFor(resolvedPage)
    val previous = lastBindKey
    lastBindKey = key
    if (previous == null || previous.pageId != key.pageId) {
      bind(resolvedPage, hostContext)
      return
    }
    if (previous.itemsSignature != key.itemsSignature) {
      applyPageItems(resolvedPage, hostContext)
      return
    }
    if (previous != key) {
      applyPageGeometry(resolvedPage)
    }
  }

  /**
   * When [commitItemChange] updates [boundPage] before Compose state catches up, ignore the stale
   * snapshot from [AndroidView] update to avoid reverting drag/resize.
   */
  private fun resolvePageForBind(incoming: WidgetPanelPage): WidgetPanelPage {
    val local = boundPage ?: return incoming
    if (local.id != incoming.id) return incoming
    if (itemsSignature(local) == itemsSignature(incoming)) return incoming

    val incomingIds = incoming.items.map { it.appWidgetId }.toSet()
    val localIds = local.items.map { it.appWidgetId }.toSet()
    if (incomingIds != localIds) {
      // Compose added/removed widgets (e.g. picker or delete); incoming is authoritative.
      return incoming
    }

    // Same widgets; canvas may be ahead during in-flight drag/resize.
    return local
  }

  private data class BindKey(
    val pageId: Long,
    val itemsSignature: String,
    val columnCount: Int,
    val rowCount: Int,
  )

  companion object {
    const val CELL_GAP_DP = 0f
  }
}
