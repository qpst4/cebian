package com.slideindex.app.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.view.isNotEmpty

class RoundedAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
  private val cornerRadiusPx = 16f * context.resources.displayMetrics.density
  private var clippingEnabled = true

  init {
    clipChildren = false
    clipToOutline = false
  }

  fun setWidgetClippingEnabled(enabled: Boolean) {
    clippingEnabled = enabled
    if (!enabled) {
      clipToOutline = false
      outlineProvider = null
      for (i in 0 until childCount) {
        getChildAt(i).clipToOutline = false
      }
    } else {
      applyRoundedCorners()
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (clippingEnabled) {
      applyRoundedCorners()
    }
  }

  private fun applyRoundedCorners() {
    if (!clippingEnabled) return
    val background = findViewById<View>(android.R.id.background)
    if (background != null) {
      background.outlineProvider = RoundedOutlineProvider(cornerRadiusPx)
      background.clipToOutline = true
      clipToOutline = false
    } else if (isNotEmpty()) {
      getChildAt(0).apply {
        outlineProvider = RoundedOutlineProvider(cornerRadiusPx)
        clipToOutline = true
      }
      clipToOutline = false
    } else {
      outlineProvider = RoundedOutlineProvider(cornerRadiusPx)
      clipToOutline = true
    }
  }

  private class RoundedOutlineProvider(private val radius: Float) : ViewOutlineProvider() {
    override fun getOutline(view: View, outline: Outline) {
      outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
  }
}
