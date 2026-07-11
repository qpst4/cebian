package com.slideindex.app.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import kotlin.math.roundToInt

/**
 * Hosts [AppWidgetHostView] at the visible slot size so providers can switch layouts
 * (e.g. Samsung Buds compact row). Falls back to expand+scale only when the provider
 * minimum exceeds the slot and clipping would otherwise occur.
 */
class ScalableFrameLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

  var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private set

  private var spanX: Int = 1
  private var spanY: Int = 1
  private var slotWidthPx: Int = 0
  private var slotHeightPx: Int = 0
  private var renderWidthPx: Int = 0
  private var renderHeightPx: Int = 0
  private var scaleVal: Float = 1f

  private var resizing: Boolean = false
  private var widgetTouchEnabled: Boolean = true
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private var interceptDownX = 0f
  private var interceptDownY = 0f
  private var scrollingInsideWidget = false
  private val cornerRadiusPx = 16f * context.resources.displayMetrics.density

  init {
    clipChildren = true
    clipToPadding = true
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (w <= 0 || h <= 0) return
    outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, w, h, cornerRadiusPx)
      }
    }
    clipToOutline = true
  }

  private var lastAppliedWidthPx = 0
  private var lastAppliedHeightPx = 0

  fun bindWidget(hostView: AppWidgetHostView, appWidgetId: Int, spanX: Int, spanY: Int) {
    val sx = spanX.coerceAtLeast(1)
    val sy = spanY.coerceAtLeast(1)
    if (childCount == 1 && getChildAt(0) === hostView && this.appWidgetId == appWidgetId) {
      this.spanX = sx
      this.spanY = sy
      requestLayout()
      invalidate()
      return
    }
    (hostView.parent as? ViewGroup)?.removeView(hostView)
    this.appWidgetId = appWidgetId
    this.spanX = sx
    this.spanY = sy
    slotWidthPx = 0
    slotHeightPx = 0
    renderWidthPx = 0
    renderHeightPx = 0
    scaleVal = 1f
    lastAppliedWidthPx = 0
    lastAppliedHeightPx = 0
    removeAllViews()

    hostView.clipToOutline = false
    hostView.clipChildren = false
    hostView.clipToPadding = false
    hostView.scaleX = 1f
    hostView.scaleY = 1f
    if (hostView is RoundedAppWidgetHostView) {
      hostView.setWidgetClippingEnabled(true)
    }

    addView(hostView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    requestLayout()
  }

  fun setWidgetTouchEnabled(enabled: Boolean) {
    widgetTouchEnabled = enabled
    if (!enabled) scrollingInsideWidget = false
  }

  fun setResizing(active: Boolean) {
    if (resizing == active) return
    resizing = active
  }

  fun applySpan(spanX: Int, spanY: Int) {
    val sx = spanX.coerceAtLeast(1)
    val sy = spanY.coerceAtLeast(1)
    if (this.spanX == sx && this.spanY == sy) return
    this.spanX = sx
    this.spanY = sy
    if (slotWidthPx > 0 && slotHeightPx > 0) {
      recalculateRenderAndScale()
      if (!resizing) {
        notifyHostSizeChanged()
      }
      requestLayout()
      invalidate()
    }
  }

  fun updateSpan(spanX: Int, spanY: Int) {
    applySpan(spanX, spanY)
  }

  fun commitHostLayout(force: Boolean = false) {
    flushHostSizeToProvider(force)
  }

  fun resetHostSizeCache() {
    lastAppliedWidthPx = 0
    lastAppliedHeightPx = 0
  }

  private fun flushHostSizeToProvider(force: Boolean = false) {
    if (slotWidthPx <= 0 || slotHeightPx <= 0 || renderWidthPx <= 0 || renderHeightPx <= 0) return
    applyWidgetSizeToHost(force)
    syncAppWidgetOptions()
  }

  private fun notifyHostSizeChanged() {
    if (slotWidthPx <= 0 || slotHeightPx <= 0 || renderWidthPx <= 0 || renderHeightPx <= 0) return
    if (resizing) return
    flushHostSizeToProvider()
  }

  private fun syncSlotAndRecalculate(widthPx: Int, heightPx: Int): Boolean {
    if (widthPx <= 0 || heightPx <= 0) return false
    if (slotWidthPx == widthPx && slotHeightPx == heightPx) return false
    slotWidthPx = widthPx
    slotHeightPx = heightPx
    recalculateRenderAndScale()
    return true
  }

  private fun recalculateRenderAndScale() {
    if (slotWidthPx <= 0 || slotHeightPx <= 0) {
      renderWidthPx = 0
      renderHeightPx = 0
      scaleVal = 1f
      return
    }
    renderWidthPx = slotWidthPx
    renderHeightPx = slotHeightPx
    scaleVal = 1f
  }

  private fun slotSizeDp(): Pair<Int, Int> {
    val density = resources.displayMetrics.density
    val slotWDp = (slotWidthPx / density).roundToInt().coerceAtLeast(40)
    val slotHDp = (slotHeightPx / density).roundToInt().coerceAtLeast(40)
    val (spanWDp, spanHDp) = WidgetSizeHelper.spanToMinSizeDp(spanX, spanY)
    return maxOf(slotWDp, spanWDp) to maxOf(slotHDp, spanHDp)
  }

  private fun applyWidgetSizeToHost(force: Boolean = false) {
    val child = if (isNotEmpty()) getChildAt(0) as? AppWidgetHostView else null
    if (child == null || slotWidthPx <= 0 || slotHeightPx <= 0) return
    if (!force &&
      slotWidthPx == lastAppliedWidthPx &&
      slotHeightPx == lastAppliedHeightPx
    ) {
      return
    }
    lastAppliedWidthPx = slotWidthPx
    lastAppliedHeightPx = slotHeightPx
    val (widthDp, heightDp) = slotSizeDp()
    val options = Bundle().apply {
      putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      child.updateAppWidgetSize(
        options,
        listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
      )
    } else {
      @Suppress("DEPRECATION")
      child.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
    }
    child.requestLayout()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val specW = MeasureSpec.getSize(widthMeasureSpec)
    val specH = MeasureSpec.getSize(heightMeasureSpec)
    setMeasuredDimension(specW, specH)

    if (specW <= 0 || specH <= 0 || isEmpty()) return

    syncSlotAndRecalculate(specW, specH)

    val child = getChildAt(0)
    if (renderWidthPx > 0 && renderHeightPx > 0) {
      child.measure(
        MeasureSpec.makeMeasureSpec(renderWidthPx, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(renderHeightPx, MeasureSpec.EXACTLY),
      )
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    val child = getChildAt(0)
    if (child == null) {
      super.onLayout(changed, left, top, right, bottom)
      return
    }

    val slotW = right - left
    val slotH = bottom - top
    val slotUpdated = syncSlotAndRecalculate(slotW, slotH)

    if (renderWidthPx <= 0 || renderHeightPx <= 0) {
      super.onLayout(changed, left, top, right, bottom)
      return
    }

    val childLeft = (slotW - renderWidthPx) / 2
    val childTop = (slotH - renderHeightPx) / 2
    child.layout(childLeft, childTop, childLeft + renderWidthPx, childTop + renderHeightPx)
    child.pivotX = renderWidthPx / 2f
    child.pivotY = renderHeightPx / 2f
    child.scaleX = scaleVal
    child.scaleY = scaleVal

    if (slotUpdated) {
      notifyHostSizeChanged()
    }
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    if (!widgetTouchEnabled) return false
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        interceptDownX = ev.x
        interceptDownY = ev.y
        scrollingInsideWidget = false
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = ev.x - interceptDownX
        val dy = ev.y - interceptDownY
        if (!scrollingInsideWidget &&
          (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)
        ) {
          val canScrollVertically = canDescendantScroll(0, dy)
          val canScrollHorizontally = canDescendantScroll(1, dx)
          if (canScrollVertically || canScrollHorizontally) {
            scrollingInsideWidget = true
            parent?.requestDisallowInterceptTouchEvent(true)
          }
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        scrollingInsideWidget = false
      }
    }
    return super.onInterceptTouchEvent(ev)
  }

  private fun canDescendantScroll(axis: Int, delta: Float): Boolean {
    if (isEmpty()) return false
    val direction = if (delta < 0) 1 else -1
    val queue = ArrayDeque<View>()
    queue.add(getChildAt(0))
    while (queue.isNotEmpty()) {
      val view = queue.removeFirst()
      val canScroll = when (axis) {
        0 -> view.canScrollVertically(direction)
        else -> view.canScrollHorizontally(direction)
      }
      if (canScroll) return true
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          queue.add(view.getChildAt(i))
        }
      }
    }
    return false
  }

  private fun syncAppWidgetOptions() {
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
    if (slotWidthPx <= 0 || slotHeightPx <= 0) return
    val (widthDp, heightDp) = slotSizeDp()
    val options = Bundle().apply {
      putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
    }
    runCatching {
      AppWidgetManager.getInstance(context.applicationContext)
        .updateAppWidgetOptions(appWidgetId, options)
    }
  }
}
