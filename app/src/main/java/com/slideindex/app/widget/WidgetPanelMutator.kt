package com.slideindex.app.widget

import android.content.Context

object WidgetPanelMutator {
  fun addWidgetToPage(
    context: Context,
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    appWidgetId: Int,
  ): List<WidgetPanelPage>? {
    val effective = WidgetPanelDefaults.effectivePages(pages)
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    val info = WidgetPopupHost.providerInfo(context, appWidgetId)
    if (info == null) {
      android.util.Log.e("WidgetPanelMutator", "addWidgetToPage failed: providerInfo is null for id $appWidgetId")
      android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(context, "Failed: Widget info is null", android.widget.Toast.LENGTH_LONG).show()
      }
      return null
    }
    val (rawSpanX, rawSpanY) = WidgetSpanUtil.spanFromProviderInfo(info)
    val spanX = rawSpanX.coerceAtMost(page.columnCount)
    val spanY = rawSpanY.coerceAtMost(page.rowCount)
    val slot = WidgetPanelGridLogic.findFirstFreeSlot(page, spanX, spanY)
    if (slot == null) {
      android.util.Log.e("WidgetPanelMutator", "addWidgetToPage failed: no free slot for span $spanX x $spanY")
      android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(context, "Failed: No free space on page", android.widget.Toast.LENGTH_LONG).show()
      }
      return null
    }
    val label = WidgetPopupHost.labelFor(context, appWidgetId)
    val item = WidgetPanelItem(
      appWidgetId = appWidgetId,
      x = slot.first,
      y = slot.second,
      spanX = spanX,
      spanY = spanY,
      label = label,
    )
    val updatedPage = WidgetPanelGridLogic.upsertItem(page, item)
    return effective.toMutableList().also { it[index] = updatedPage }
  }

  fun removeWidgetFromPage(
    context: Context,
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    appWidgetId: Int,
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    effective[index] = WidgetPanelGridLogic.removeItem(page, appWidgetId)
    WidgetPopupHost.deleteAppWidgetId(context, appWidgetId)
    return effective
  }

  fun replacePage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    page: WidgetPanelPage,
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    effective[index] = page
    return effective
  }

  fun replaceItemOnPage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    item: WidgetPanelItem,
  ): List<WidgetPanelPage> {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    effective[index] = WidgetPanelGridLogic.upsertItem(effective[index], item)
    return effective
  }

  fun updateItemOnPage(
    pages: List<WidgetPanelPage>,
    pageIndex: Int,
    item: WidgetPanelItem,
  ): List<WidgetPanelPage>? {
    val effective = WidgetPanelDefaults.effectivePages(pages).toMutableList()
    val index = pageIndex.coerceIn(0, effective.lastIndex)
    val page = effective[index]
    if (!WidgetPanelGridLogic.isAreaFree(page, item.x, item.y, item.spanX, item.spanY, item.appWidgetId)) {
      return null
    }
    effective[index] = WidgetPanelGridLogic.upsertItem(page, item)
    return effective
  }
}
