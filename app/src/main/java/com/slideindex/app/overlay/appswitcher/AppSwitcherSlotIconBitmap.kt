package com.slideindex.app.overlay.appswitcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.QuickLauncherIconResolver

internal object AppSwitcherSlotIconBitmap {
    private val cache = object : LruCache<String, Bitmap>(96) {}

    fun get(
        context: Context,
        item: QuickLauncherItem,
        sizePx: Int,
        appsByPackage: Map<String, com.slideindex.app.data.AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        resolvedIcon: Drawable? = null,
        appRepository: AppRepository? = null,
    ): Bitmap {
        val cacheKey = "${item.type}:${item.payload}:${item.label}:$sizePx"
        cache.get(cacheKey)?.let { return it }
        val bitmap = resolveBitmap(
            context = context,
            item = item,
            sizePx = sizePx,
            appsByPackage = appsByPackage,
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands,
            resolvedIcon = resolvedIcon,
            appRepository = appRepository,
        ) ?: createPlaceholderBitmap(sizePx, item.label.ifBlank { item.payload })
        cache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun resolveBitmap(
        context: Context,
        item: QuickLauncherItem,
        sizePx: Int,
        appsByPackage: Map<String, com.slideindex.app.data.AppInfo>,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
        resolvedIcon: Drawable?,
        appRepository: AppRepository?,
    ): Bitmap? {
        resolvedIcon?.let { return drawableToBitmap(it, sizePx) }
        if (item.type == QuickLauncherItemType.APP && item.payload.isNotBlank() && appRepository != null) {
            appRepository.peekLaunchIconBitmap(item.payload, sizePx)?.let { return it }
            appRepository.peekLaunchIconDrawable(item.payload)?.let { return drawableToBitmap(it, sizePx) }
        }
        return QuickLauncherIconResolver.iconBitmap(
            item = item,
            appsByPackage = appsByPackage,
            size = sizePx,
            context = context,
            actionIconTintArgb = android.graphics.Color.WHITE,
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands,
        )
    }

    private fun createPlaceholderBitmap(sizePx: Int, label: String): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val rect = android.graphics.RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        val cornerRadius = sizePx * 0.22f

        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(48, 48, 52)
        }
        val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 255, 255, 255)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = (sizePx * 0.04f).coerceAtLeast(1f)
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

        val char = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = sizePx * 0.44f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baseline = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(char, sizePx / 2f, baseline, textPaint)
        return bitmap
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val instance = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        instance.setBounds(0, 0, sizePx, sizePx)
        instance.draw(canvas)
        return bitmap
    }
}
