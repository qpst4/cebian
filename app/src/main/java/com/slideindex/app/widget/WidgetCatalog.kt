package com.slideindex.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import android.os.Build
import android.os.UserManager
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WidgetProviderEntry(
  val provider: AppWidgetProviderInfo,
  val packageName: String,
  val appLabel: String,
  val widgetLabel: String,
  val spanX: Int,
  val spanY: Int,
)

data class WidgetAppGroup(
  val packageName: String,
  val appLabel: String,
  val appIcon: Drawable?,
  val widgets: List<WidgetProviderEntry>,
)

object WidgetCatalog {
  suspend fun loadGroups(context: Context): List<WidgetAppGroup> = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)
    val pm = appContext.packageManager
    val providers = loadInstalledProviders(appContext, manager)
      .distinctBy { it.provider }
    val grouped = LinkedHashMap<String, MutableList<WidgetProviderEntry>>()
    for (info in providers) {
      val packageName = info.provider.packageName
      if (packageName == appContext.packageName) continue
      @Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
      val appLabel = runCatching {
        info.loadLabel(pm)?.toString()
      }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: runCatching {
          pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrElse { packageName }
      @Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
      val widgetLabel = info.loadLabel(pm)?.toString().orEmpty().ifBlank { appLabel }
      val (spanX, spanY) = WidgetSpanUtil.spanFromProviderInfo(info)
      val entry = WidgetProviderEntry(
        provider = info,
        packageName = packageName,
        appLabel = appLabel,
        widgetLabel = widgetLabel,
        spanX = spanX,
        spanY = spanY,
      )
      grouped.getOrPut(packageName) { mutableListOf() }.add(entry)
    }
    grouped.map { (pkg, widgets) ->
      val appLabel = widgets.firstOrNull()?.appLabel ?: pkg
      val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
      WidgetAppGroup(
        packageName = pkg,
        appLabel = appLabel,
        appIcon = icon,
        widgets = widgets.sortedBy { it.widgetLabel },
      )
    }.sortedBy { it.appLabel.lowercase() }
  }

  private fun loadInstalledProviders(
    context: Context,
    manager: AppWidgetManager,
  ): List<AppWidgetProviderInfo> {
    val seen = HashSet<String>()
    val merged = mutableListOf<AppWidgetProviderInfo>()

    fun absorb(list: List<AppWidgetProviderInfo>?) {
      if (list.isNullOrEmpty()) return
      for (info in list) {
        val key = info.provider.flattenToString()
        if (seen.add(key)) merged.add(info)
      }
    }

    return runCatching {
      // Always seed from the full installed list first. On some OEM builds
      // getInstalledProvidersForProfile() returns an incomplete subset.
      absorb(manager.installedProviders)
      val userManager = context.getSystemService(UserManager::class.java)
      if (userManager != null) {
        for (profile in userManager.userProfiles) {
          absorb(manager.getInstalledProvidersForProfile(profile))
        }
      }
      merged
    }.getOrElse {
      manager.installedProviders
    }
  }
}

object WidgetPreviewLoader {
  fun loadPreviewBitmap(context: Context, info: AppWidgetProviderInfo, maxPx: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val previewRes = info.previewImage
      if (previewRes != 0) {
        loadDrawableBitmap(context, info.provider.packageName, previewRes, maxPx)?.let { return it }
      }
    }
    val icon = runCatching {
      info.loadIcon(context, context.resources.displayMetrics.densityDpi)
    }.getOrNull()
    return icon?.let { drawableToBitmap(it, maxPx) }
  }

  private fun loadDrawableBitmap(
    context: Context,
    packageName: String,
    resId: Int,
    maxPx: Int,
  ): Bitmap? = runCatching {
    val pm = context.packageManager
    val resources = pm.getResourcesForApplication(packageName)
    val drawable = ResourcesCompat.getDrawable(resources, resId, null) ?: return null
    drawableToBitmap(drawable, maxPx)
  }.getOrNull()

  private fun drawableToBitmap(drawable: Drawable, maxPx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      return scaleBitmap(drawable.bitmap, maxPx)
    }
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return scaleBitmap(bitmap, maxPx)
  }

  private fun scaleBitmap(source: Bitmap, maxPx: Int): Bitmap {
    val maxDim = maxOf(source.width, source.height)
    if (maxDim <= maxPx) return source
    val scale = maxPx.toFloat() / maxDim
    val w = (source.width * scale).toInt().coerceAtLeast(1)
    val h = (source.height * scale).toInt().coerceAtLeast(1)
    return source.scale(w, h)
  }
}
