package com.slideindex.app.message

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.slideindex.app.R
import com.slideindex.app.message.MessageThemeCatalog
import com.slideindex.app.overlay.FloatIconOverlayWindow
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.SideBubbleOverlayWindow

object MessageReminderPreviewController {
    const val PREVIEW_KEY = "__message_reminder_preview__"

    private var activeStyle: MessageStyle? = null
    private var placementBaseline: MessageSettings? = null

    fun isPreviewActive(style: MessageStyle): Boolean = activeStyle == style

    fun start(context: Context, style: MessageStyle, settings: MessageSettings) {
        if (!MessageOverlayHost.canShow(context)) return
        val previewSettings = previewSettingsFor(style, settings)
        val plan = buildPreviewPlan(context, style, previewSettings) ?: return
        activeStyle = style
        placementBaseline = settings
        val noOpAction: (MessageAction) -> Unit = {}
        val noOpDismiss: () -> Unit = {}
        when (style) {
            MessageStyle.FloatIcon -> {
                FloatIconOverlayWindow.dismissPreview()
                FloatIconOverlayWindow.show(context, plan, noOpAction, noOpDismiss)
            }
            MessageStyle.SideBubble -> {
                SideBubbleOverlayWindow.dismissPreview()
                SideBubbleOverlayWindow.show(context, plan, noOpAction, noOpDismiss)
            }
            else -> Unit
        }
    }

    fun updatePlacement(context: Context, settings: MessageSettings) {
        val style = activeStyle ?: return
        val previewSettings = previewSettingsFor(style, settings)
        when (style) {
            MessageStyle.FloatIcon ->
                FloatIconOverlayWindow.updateWindowPlacement(context, previewSettings)
            MessageStyle.SideBubble ->
                SideBubbleOverlayWindow.updateWindowPlacement(context, previewSettings)
            else -> Unit
        }
    }

    fun updatePreview(context: Context, settings: MessageSettings) {
        val style = activeStyle ?: return
        val previewSettings = previewSettingsFor(style, settings)
        val plan = buildPreviewPlan(context, style, previewSettings) ?: return
        when (style) {
            MessageStyle.FloatIcon -> {
                FloatIconOverlayWindow.updateWindowPlacement(context, previewSettings)
                FloatIconOverlayWindow.updatePreviewPlan(plan)
            }
            MessageStyle.SideBubble -> {
                SideBubbleOverlayWindow.updateWindowPlacement(context, previewSettings)
                SideBubbleOverlayWindow.updatePreviewPlan(plan)
            }
            else -> Unit
        }
    }

    fun previewSettings(context: Context, settings: MessageSettings) {
        if (placementBaseline == null) {
            placementBaseline = settings
        }
        updatePreview(context, settings)
    }

    fun end(restorePlacement: Boolean) {
        val style = activeStyle
        val baseline = placementBaseline
        when (style) {
            MessageStyle.FloatIcon -> FloatIconOverlayWindow.dismissPreview()
            MessageStyle.SideBubble -> SideBubbleOverlayWindow.dismissPreview()
            else -> Unit
        }
        if (restorePlacement && style != null && baseline != null) {
            // Preview-only session; committed values are persisted separately.
        }
        activeStyle = null
        placementBaseline = null
    }

    fun clearPlacementBaseline() {
        placementBaseline = null
    }

    private fun previewSettingsFor(style: MessageStyle, settings: MessageSettings): MessageSettings =
        settings.copy(
            enabled = true,
            floatIconEnabled = style == MessageStyle.FloatIcon,
            sideBubbleEnabled = style == MessageStyle.SideBubble,
            autoDismissSeconds = 0,
        )

    private fun buildPreviewPlan(
        context: Context,
        style: MessageStyle,
        settings: MessageSettings,
    ): MessageDisplayPlan? {
        val appContext = context.applicationContext
        val appLabel = runCatching {
            appContext.packageManager.getApplicationLabel(
                appContext.packageManager.getApplicationInfo(appContext.packageName, 0),
            ).toString()
        }.getOrDefault(appContext.packageName)
        val appIcon = loadAppIcon(appContext, appContext.packageName)
        val data = NotificationData(
            packageName = appContext.packageName,
            key = PREVIEW_KEY,
            title = appLabel,
            content = context.getString(R.string.message_preview_content),
            largeIcon = appIcon,
            appIcon = null,
            contentIntent = null,
            postTime = -1L,
        )
        return MessageDisplayPlan(
            data = data,
            showFloatIcon = style == MessageStyle.FloatIcon,
            showSideBubble = style == MessageStyle.SideBubble,
            showDanmaku = false,
            sideTheme = if (style == MessageStyle.SideBubble) {
                MessageThemeCatalog.themeFor(MessageStyle.SideBubble, settings.sideThemeId)
            } else {
                null
            },
            danmakuTheme = null,
            settings = settings,
        )
    }

    private fun loadAppIcon(context: Context, packageName: String): Bitmap? {
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable, 144)
        }.getOrNull()
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val source = drawable.bitmap
            if (source.width == sizePx && source.height == sizePx) return source
            return Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
        }
    }
}
