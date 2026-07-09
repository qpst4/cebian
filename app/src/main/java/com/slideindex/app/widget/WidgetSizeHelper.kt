package com.slideindex.app.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import kotlin.math.roundToInt

object WidgetSizeHelper {
  /** Launcher-style cell size in dp (matches [WidgetSpanUtil] and AOSP launcher grid). */
  const val LAUNCHER_CELL_DP = 70
  private const val LAUNCHER_CELL_PADDING_DP = 30

  fun spanToMinSizeDp(spanX: Int, spanY: Int): Pair<Int, Int> {
    val w = (LAUNCHER_CELL_DP * spanX - LAUNCHER_CELL_PADDING_DP).coerceAtLeast(40)
    val h = (LAUNCHER_CELL_DP * spanY - LAUNCHER_CELL_PADDING_DP).coerceAtLeast(40)
    return w to h
  }

  /** Native render size from provider metadata (dp → px). Falls back to launcher span formula. */
  fun providerRenderSizePx(
    context: android.content.Context,
    appWidgetId: Int,
    spanX: Int,
    spanY: Int,
  ): Pair<Int, Int> {
    val dm = context.resources.displayMetrics
    val info = WidgetPopupHost.providerInfo(context, appWidgetId)
    if (info != null && info.minWidth > 0 && info.minHeight > 0) {
      val wPx = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP,
        info.minWidth.toFloat(),
        dm,
      ).roundToInt().coerceAtLeast(1)
      val hPx = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP,
        info.minHeight.toFloat(),
        dm,
      ).roundToInt().coerceAtLeast(1)
      return wPx to hPx
    }
    val (minWDp, minHDp) = spanToMinSizeDp(spanX, spanY)
    return (minWDp * dm.density).roundToInt().coerceAtLeast(1) to
      (minHDp * dm.density).roundToInt().coerceAtLeast(1)
  }

  fun providerRenderSizeDp(context: android.content.Context, appWidgetId: Int, spanX: Int, spanY: Int): Pair<Int, Int> {
    val info = WidgetPopupHost.providerInfo(context, appWidgetId)
    if (info != null && info.minWidth > 0 && info.minHeight > 0) {
      return info.minWidth.coerceAtLeast(40) to info.minHeight.coerceAtLeast(40)
    }
    return spanToMinSizeDp(spanX, spanY)
  }

  /**
   * Sets the host view to its native render size once at bind time.
   * Must not be called with shrunk card sizes — visual scaling is handled by [ScalableFrameLayout].
   */
  fun applyNativeRenderSize(hostView: AppWidgetHostView, appWidgetId: Int, spanX: Int, spanY: Int) {
    val (widthDp, heightDp) = providerRenderSizeDp(hostView.context, appWidgetId, spanX, spanY)
    updateSizeDp(hostView, appWidgetId, widthDp, heightDp)
    hostView.setPadding(0, 0, 0, 0)
  }

  fun applyPixelSize(hostView: AppWidgetHostView, appWidgetId: Int, widthPx: Int, heightPx: Int) {
    if (widthPx <= 0 || heightPx <= 0) return
    val density = hostView.resources.displayMetrics.density
    val wDp = (widthPx / density).roundToInt().coerceAtLeast(40)
    val hDp = (heightPx / density).roundToInt().coerceAtLeast(40)
    updateSizeDp(hostView, appWidgetId, wDp, hDp)
  }

  private fun updateSizeDp(hostView: AppWidgetHostView, appWidgetId: Int, widthDp: Int, heightDp: Int) {
    val options = Bundle().apply {
      putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
      putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hostView.updateAppWidgetSize(options, listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
    } else {
      @Suppress("DEPRECATION")
      hostView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
    }
    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
      runCatching {
        AppWidgetManager.getInstance(hostView.context.applicationContext)
          .updateAppWidgetOptions(appWidgetId, options)
      }
    }
    hostView.requestLayout()
  }

  fun computeGridStepPx(innerWidthPx: Int, columnCount: Int): Int =
    WidgetGridMetrics.computeGridStepPx(innerWidthPx, columnCount)

  fun computeCellSizePx(innerWidthPx: Int, columnCount: Int, cellGapPx: Int): Int {
    if (columnCount <= 0) return 1
    val gaps = (columnCount - 1).coerceAtLeast(0) * cellGapPx
    return ((innerWidthPx - gaps) / columnCount).coerceAtLeast(1)
  }

  fun containerSizePx(spanX: Int, spanY: Int, gridStepPx: Int): Pair<Int, Int> {
    val sx = spanX.coerceAtLeast(1)
    val sy = spanY.coerceAtLeast(1)
    val step = gridStepPx.coerceAtLeast(1)
    return sx * step to sy * step
  }

  @Deprecated("Use containerSizePx(spanX, spanY, gridStepPx)")
  fun containerSizePx(
    spanX: Int,
    spanY: Int,
    cellSizePx: Int,
    cellGapPx: Int,
  ): Pair<Int, Int> {
    val w = spanX * cellSizePx + (spanX - 1).coerceAtLeast(0) * cellGapPx
    val h = spanY * cellSizePx + (spanY - 1).coerceAtLeast(0) * cellGapPx
    return w to h
  }
}
