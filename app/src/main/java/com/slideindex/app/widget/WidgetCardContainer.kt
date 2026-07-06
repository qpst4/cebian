package com.slideindex.app.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Card shell wrapping [ScalableFrameLayout] with delete / resize chrome.
 */
class WidgetCardContainer(
  context: Context,
  initialItem: WidgetPanelItem,
  hostView: AppWidgetHostView?,
) : FrameLayout(context) {

  var item: WidgetPanelItem = initialItem
    private set
  var onDelete: (() -> Unit)? = null
  var onResize: ((spanX: Int, spanY: Int) -> Unit)? = null
  var onConfigure: (() -> Unit)? = null
  var onInteractionActiveChange: ((Boolean) -> Unit)? = null

  private val scalableFrame: ScalableFrameLayout
  private val loadingPlaceholder: WidgetLoadingPlaceholder
  private val deleteButton: View
  private val resizeHandle: ResizeHandleView
  private val configureButton: View
  private var editModeEnabled = false
  private var previewSpanX = item.spanX
  private var previewSpanY = item.spanY
  private var previewingResize = false

  private val density = resources.displayMetrics.density
  private val cardCornerRadiusPx = 16f * density
  private val editOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x66FFFFFF
    style = Paint.Style.STROKE
    strokeWidth = 1.2f * density
  }

  init {
    clipChildren = false
    clipToPadding = false
    clipToOutline = false

    loadingPlaceholder = WidgetLoadingPlaceholder(context)
    scalableFrame = ScalableFrameLayout(context)

    if (hostView != null) {
      scalableFrame.bindWidget(hostView, item.appWidgetId, item.spanX, item.spanY)
      loadingPlaceholder.visibility = GONE
    } else {
      loadingPlaceholder.visibility = VISIBLE
    }

    addView(loadingPlaceholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    addView(scalableFrame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

    if (hostView != null) {
      post { attachHostWhenReady(hostView) }
    }

    val chromeBtnSize = (18 * density).roundToInt()
    val chromeInset = (4 * density).roundToInt()
    deleteButton = View(context).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xFFE53935.toInt())
      }
      val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1.6f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
      }
      setWillNotDraw(false)
      tag = crossPaint
      setOnClickListener { onDelete?.invoke() }
    }
    addView(
      deleteButton,
      LayoutParams(chromeBtnSize, chromeBtnSize).apply {
        gravity = Gravity.TOP or Gravity.END
        topMargin = -chromeInset
        marginEnd = -chromeInset
      },
    )

    resizeHandle = ResizeHandleView(context).apply {
      setOnTouchListener(ResizeTouchListener())
    }
    val handleSize = chromeBtnSize
    addView(
      resizeHandle,
      LayoutParams(handleSize, handleSize).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        bottomMargin = (1 * density).roundToInt()
        marginEnd = (1 * density).roundToInt()
      },
    )

    configureButton = View(context).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0xCC333333.toInt())
      }
      setWillNotDraw(false)
      setOnClickListener { onConfigure?.invoke() }
    }
    addView(
      configureButton,
      LayoutParams(chromeBtnSize, chromeBtnSize).apply {
        gravity = Gravity.TOP or Gravity.START
        topMargin = -chromeInset
        marginStart = -chromeInset
      },
    )

    setEditMode(false)
  }

  fun syncItem(newItem: WidgetPanelItem) {
    if (item == newItem) return
    item = newItem
    previewSpanX = newItem.spanX
    previewSpanY = newItem.spanY
    requestLayout()
  }

  fun attachHostView(hostView: AppWidgetHostView) {
    loadingPlaceholder.visibility = GONE
    scalableFrame.visibility = VISIBLE
    scalableFrame.bindWidget(hostView, item.appWidgetId, item.spanX, item.spanY)
    post { updateScalableTargetFromContainer() }
  }

  private fun attachHostWhenReady(hostView: AppWidgetHostView) {
    loadingPlaceholder.visibility = GONE
    scalableFrame.visibility = VISIBLE
    post {
      updateScalableTargetFromContainer()
      scalableFrame.commitHostLayout()
    }
  }

  fun setEditMode(enabled: Boolean) {
    editModeEnabled = enabled
    if (!enabled) clearResizePreview()
    deleteButton.visibility = if (enabled) VISIBLE else GONE
    resizeHandle.visibility = if (enabled) VISIBLE else GONE
    scalableFrame.setWidgetTouchEnabled(!enabled)

    val info = WidgetPopupHost.providerInfo(context, item.appWidgetId)
    val hasConfigure = info?.configure != null
    configureButton.visibility = if (enabled && hasConfigure) VISIBLE else GONE
    invalidate()
  }

  private fun drawEditOutline(canvas: Canvas) {
    val half = editOutlinePaint.strokeWidth / 2f
    val rect = RectF(half, half, width - half, height - half)
    canvas.drawRoundRect(rect, cardCornerRadiusPx, cardCornerRadiusPx, editOutlinePaint)
  }

  fun isTouchOnChrome(localX: Float, localY: Float): Boolean {
    if (deleteButton.visibility == VISIBLE && isPointInView(deleteButton, localX, localY)) return true
    if (resizeHandle.visibility == VISIBLE && isPointInView(resizeHandle, localX, localY)) return true
    if (configureButton.visibility == VISIBLE && isPointInView(configureButton, localX, localY)) return true
    return false
  }

  private fun isPointInView(view: View, localX: Float, localY: Float): Boolean {
    val pad = 6f * density
    return localX >= view.left - pad &&
      localX <= view.right + pad &&
      localY >= view.top - pad &&
      localY <= view.bottom + pad
  }

  private fun requestDisallowInterceptAllParents(disallow: Boolean) {
    var current: android.view.ViewParent? = parent
    while (current != null) {
      current.requestDisallowInterceptTouchEvent(disallow)
      current = current.parent
    }
  }

  fun updateScalableTargetFromContainer() {
    scalableFrame.applySpan(layoutSpanX(), layoutSpanY())
  }

  fun previewResize(spanX: Int, spanY: Int, gridStepPx: Int) {
    if (previewSpanX == spanX && previewSpanY == spanY) return
    previewSpanX = spanX
    previewSpanY = spanY
    if (!previewingResize) {
      previewingResize = true
      scalableFrame.setResizing(true)
    }
    (parent as? View)?.requestLayout()
  }

  fun clearResizePreview() {
    if (!previewingResize) return
    previewingResize = false
    previewSpanX = item.spanX
    previewSpanY = item.spanY
    scalableFrame.setResizing(false)
    scalableFrame.commitHostLayout()
    updateScalableTargetFromContainer()
    (parent as? View)?.requestLayout()
  }

  fun layoutSpanX(): Int = if (previewingResize) previewSpanX else item.spanX

  fun layoutSpanY(): Int = if (previewingResize) previewSpanY else item.spanY

  fun refreshWidgetLayout() {
    updateScalableTargetFromContainer()
    scalableFrame.commitHostLayout()
    requestLayout()
  }

  private fun runAfterNextLayout(block: () -> Unit) {
    if (isAttachedToWindow && width > 0 && height > 0 && !isLayoutRequested) {
      block()
      return
    }
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
      override fun onGlobalLayout() {
        viewTreeObserver.removeOnGlobalLayoutListener(this)
        block()
      }
    })
  }

  private fun finishResize(newSpanX: Int, newSpanY: Int, sizeChanged: Boolean) {
    if (sizeChanged) {
      syncItem(item.copy(spanX = newSpanX, spanY = newSpanY))
    } else {
      previewSpanX = item.spanX
      previewSpanY = item.spanY
    }
    previewingResize = false
    scalableFrame.setResizing(false)
    (parent as? View)?.requestLayout()
    runAfterNextLayout {
      updateScalableTargetFromContainer()
      scalableFrame.commitHostLayout(force = true)
      if (sizeChanged) {
        onResize?.invoke(newSpanX, newSpanY)
      }
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (width <= 0 || height <= 0) return
    updateScalableTargetFromContainer()
    if (!previewingResize) {
      scalableFrame.commitHostLayout()
    }
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    if (editModeEnabled) {
      drawEditOutline(canvas)
    }
    if (editModeEnabled && deleteButton.visibility == VISIBLE) {
      val paint = deleteButton.tag as? Paint ?: return
      val cx = deleteButton.left + deleteButton.width / 2f
      val cy = deleteButton.top + deleteButton.height / 2f
      val r = deleteButton.width * 0.16f
      canvas.drawLine(cx - r, cy - r, cx + r, cy + r, paint)
      canvas.drawLine(cx + r, cy - r, cx - r, cy + r, paint)
    }
    if (editModeEnabled && configureButton.visibility == VISIBLE) {
      drawGearIcon(
        canvas,
        configureButton.left + configureButton.width / 2f,
        configureButton.top + configureButton.height / 2f,
        configureButton.width * 0.28f,
      )
    }
  }

  private fun drawGearIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      style = Paint.Style.STROKE
      strokeWidth = 1.2f * density
      strokeCap = Paint.Cap.ROUND
    }
    val toothCount = 8
    val outerR = radius
    val innerR = radius * 0.72f
    val holeR = radius * 0.32f
    val path = Path()
    for (i in 0 until toothCount) {
      val angle = Math.PI * 2 * i / toothCount
      val next = Math.PI * 2 * (i + 0.5) / toothCount
      val x1 = cx + cos(angle).toFloat() * outerR
      val y1 = cy + sin(angle).toFloat() * outerR
      val x2 = cx + cos(next).toFloat() * innerR
      val y2 = cy + sin(next).toFloat() * innerR
      if (i == 0) path.moveTo(x1, y1) else path.lineTo(x1, y1)
      path.lineTo(x2, y2)
    }
    path.close()
    canvas.drawPath(path, paint)
    canvas.drawCircle(cx, cy, holeR, paint)
  }

  private inner class ResizeTouchListener : OnTouchListener {
    private var startSpanX = item.spanX
    private var startSpanY = item.spanY
    private var startRawX = 0f
    private var startRawY = 0f
    private var gridStepPx = 0
    private var previewSpanX = item.spanX
    private var previewSpanY = item.spanY

    override fun onTouch(v: View, event: MotionEvent): Boolean {
      if (!editModeEnabled) return false
      val parent = parent as? WidgetCanvasLayout ?: return false
      gridStepPx = parent.gridStepPx
      val step = gridStepPx.coerceAtLeast(1)
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          startSpanX = item.spanX
          startSpanY = item.spanY
          previewSpanX = startSpanX
          previewSpanY = startSpanY
          startRawX = event.rawX
          startRawY = event.rawY
          scalableFrame.setResizing(true)
          onInteractionActiveChange?.invoke(true)
          requestDisallowInterceptAllParents(true)
          return true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = ((event.rawX - startRawX) / step).roundToInt()
          val dy = ((event.rawY - startRawY) / step).roundToInt()
          previewSpanX = (startSpanX + dx).coerceIn(1, parent.pageColumnCount - item.x)
          previewSpanY = (startSpanY + dy).coerceIn(1, parent.pageRowCount - item.y)
          val page = parent.currentPage ?: return true
          if (WidgetPanelGridLogic.isAreaFree(
              page,
              item.x,
              item.y,
              previewSpanX,
              previewSpanY,
              item.appWidgetId,
            )
          ) {
            previewResize(previewSpanX, previewSpanY, gridStepPx)
          }
          return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          onInteractionActiveChange?.invoke(false)
          requestDisallowInterceptAllParents(false)
          val newSpanX = previewSpanX
          val newSpanY = previewSpanY
          val sizeChanged = newSpanX != item.spanX || newSpanY != item.spanY
          finishResize(newSpanX, newSpanY, sizeChanged)
          return true
        }
      }
      return false
    }
  }

  private class ResizeHandleView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val armPx = 8f * density
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xCC1A1A1A.toInt()
      strokeWidth = 2.8f * density
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      strokeWidth = 1.8f * density
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }

    init {
      setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      val right = width - 1.5f * density
      val bottom = height - 1.5f * density
      val top = bottom - armPx
      val left = right - armPx
      canvas.drawLine(right, top, right, bottom, outlinePaint)
      canvas.drawLine(left, bottom, right, bottom, outlinePaint)
      canvas.drawLine(right, top, right, bottom, paint)
      canvas.drawLine(left, bottom, right, bottom, paint)
    }
  }
}
