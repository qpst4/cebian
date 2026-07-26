package com.slideindex.app.overlay

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/** 覆盖层布局用真实屏幕尺寸（含导航栏区域），与 SideGesture ScreenUtils 一致。 */
internal object OverlayScreenMetrics {
    fun sizePx(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val fallback = context.resources.displayMetrics
        return if (wm != null) sizePx(wm, fallback) else fallback.widthPixels to fallback.heightPixels
    }

    fun sizePx(wm: WindowManager, fallback: DisplayMetrics): Pair<Int, Int> {
        val bounds = runCatching { wm.currentWindowMetrics.bounds }.getOrNull()
        if (bounds != null) {
            return bounds.width() to bounds.height()
        }
        return fallback.widthPixels to fallback.heightPixels
    }
}
