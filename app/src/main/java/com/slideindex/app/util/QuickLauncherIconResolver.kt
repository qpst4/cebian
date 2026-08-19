package com.slideindex.app.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toDrawable
import android.graphics.drawable.Drawable
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ManagedShortcutIconResolver
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver

object QuickLauncherIconResolver {
    fun iconBitmap(
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
        size: Int = 128,
        context: Context? = null,
        actionIconTintArgb: Int = Color.WHITE,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
    ): Bitmap? {
        if (item.type == QuickLauncherItemType.FOLDER) {
            return renderFolderBitmap(
                item = item,
                appsByPackage = appsByPackage,
                size = size.coerceAtLeast(1),
                context = context,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
            )
        }
        if (item.type == QuickLauncherItemType.ACTION &&
            shouldUseGestureVectorIcon(item, shellCommands)
        ) {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
            return GestureActionIconBitmap.get(
                action = action,
                sizePx = size.coerceAtLeast(1),
                tintArgb = Color.WHITE,
                outlined = true,
                withPlate = true,
            )
        }
        val drawable = iconDrawable(item, appsByPackage, context, activityShortcuts, shellCommands) ?: return null
        return iconBitmapFromDrawable(drawable, size)
    }

    fun iconDrawable(
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
        context: Context? = null,
        activityShortcuts: List<ActivityShortcut> = emptyList(),
        shellCommands: List<ShellCommand> = emptyList(),
    ): Drawable? {
        return when (item.type) {
            QuickLauncherItemType.FOLDER -> {
                val bmp = renderFolderBitmap(
                    item = item,
                    appsByPackage = appsByPackage,
                    size = 128,
                    context = context,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                )
                context?.resources?.let { bmp.toDrawable(it) } ?: BitmapDrawable(null, bmp)
            }
            QuickLauncherItemType.APP -> getIconSafe(appsByPackage[item.payload], context)
                ?: getIconSafe(item.payload, context)
            QuickLauncherItemType.SHORTCUT -> {
                context?.let { ctx ->
                    ManagedShortcutIconResolver.drawableForQuickItem(ctx, item, activityShortcuts)
                        ?.let { return it }
                }
                shortcutDrawable(item.payload, appsByPackage, context)
            }
            QuickLauncherItemType.ACTION -> {
                val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
                when (action) {
                    is GestureAction.LaunchApp -> getIconSafe(appsByPackage[action.packageName], context)
                    is GestureAction.LaunchShortcut -> {
                        context?.let { ctx ->
                            ManagedShortcutIconResolver.drawableForLaunchShortcut(
                                ctx,
                                action,
                                activityShortcuts,
                            )?.let { return it }
                        }
                        gestureShortcutDrawable(action.payloadKey, appsByPackage, context)
                    }
                    is GestureAction.ExecuteShellCommand -> {
                        val matched = ShellCommandIconResolver.findForCommandLine(action.command, shellCommands)
                        if (matched != null && context != null) {
                            ShellCommandIconResolver.resolveBitmap(context, matched, 128)?.let { bitmap ->
                                return bitmap.toDrawable(context.resources)
                            }
                        }
                        gestureActionDrawable(action, context)
                    }
                    else -> gestureActionDrawable(action, context)
                }
            }
            QuickLauncherItemType.WIDGET -> null
        }
    }

    private fun getIconSafe(packageName: String, context: Context?): Drawable? {
        if (context == null) return null
        return runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    private fun getIconSafe(appInfo: AppInfo?, context: Context?): Drawable? {
        if (appInfo == null || context == null) return null
        return runCatching { context.packageManager.getApplicationIcon(appInfo.packageName) }.getOrNull()
    }

    fun shouldUseGestureVectorIcon(
        item: QuickLauncherItem,
        shellCommands: List<ShellCommand> = emptyList(),
    ): Boolean {
        if (item.type != QuickLauncherItemType.ACTION) return false
        val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return false
        if (action is GestureAction.ExecuteShellCommand &&
            ShellCommandIconResolver.findForCommandLine(action.command, shellCommands) != null
        ) {
            return false
        }
        return action !is GestureAction.LaunchApp && action !is GestureAction.LaunchShortcut
    }

    private fun gestureActionDrawable(action: GestureAction, context: Context?): Drawable? {
        val ctx = context ?: return null
        val bitmap = GestureActionIconBitmap.get(
            action = action,
            sizePx = 128,
            tintArgb = Color.WHITE,
            outlined = true,
            withPlate = true,
        )
        return bitmap.toDrawable(ctx.resources)
    }

    private fun shortcutDrawable(
        payload: String,
        appsByPackage: Map<String, AppInfo>,
        context: Context?,
    ): Drawable? {
        QuickLauncherItemCodec.resolveHostPackageName(payload) { uri ->
            KnownAppShortcuts.packageForIntentUri(uri)
        }?.let { packageName ->
            getIconSafe(appsByPackage[packageName], context)?.let { return it }
        }
        if (payload.startsWith("c:")) {
            val componentFlat = payload.removePrefix("c:").substringBefore('\u001D')
            packageFromComponentFlat(componentFlat)?.let { getIconSafe(appsByPackage[it], context) }?.let { return it }
        }
        context?.let { ctx ->
            resolvePackageFromIntentPayload(ctx, payload)?.let { packageName ->
                getIconSafe(appsByPackage[packageName], context)?.let { return it }
            }
        }
        return null
    }

    private fun gestureShortcutDrawable(
        payloadKey: String,
        appsByPackage: Map<String, AppInfo>,
        context: Context?,
    ): Drawable? = when (val decoded = GestureShortcutPayload.decode(payloadKey)) {
        is GestureShortcutPayload.Decoded.Dynamic ->
            getIconSafe(appsByPackage[decoded.packageName], context)
        is GestureShortcutPayload.Decoded.Component ->
            packageFromComponentFlat(decoded.componentFlat)?.let { getIconSafe(appsByPackage[it], context) }
        is GestureShortcutPayload.Decoded.IntentShortcut ->
            resolveIntentShortcutDrawable(decoded.intentUri, appsByPackage, context)
        is GestureShortcutPayload.Decoded.IntentsShortcut ->
            decoded.intentUris.firstNotNullOfOrNull { uri ->
                resolveIntentShortcutDrawable(uri, appsByPackage, context)
            }
        null -> null
    }

    private fun resolveIntentShortcutDrawable(
        intentUri: String,
        appsByPackage: Map<String, AppInfo>,
        context: Context?,
    ): Drawable? {
        QuickLauncherItemCodec.resolveHostPackageName(
            "${QuickLauncherItemCodec.INTENT_PAYLOAD_PREFIX}$intentUri",
        ) { uri -> KnownAppShortcuts.packageForIntentUri(uri) }?.let { getIconSafe(appsByPackage[it], context) }?.let { return it }
        context?.let { ctx ->
            packageFromIntentUri(ctx, intentUri)?.let { getIconSafe(appsByPackage[it], context) }?.let { return it }
        }
        return null
    }

    private fun resolvePackageFromIntentPayload(context: Context, payload: String): String? {
        QuickLauncherItemCodec.parseIntentPayload(payload)?.let { uri ->
            packageFromIntentUri(context, uri)?.let { return it }
        }
        QuickLauncherItemCodec.parseIntentListPayload(payload)?.firstOrNull()?.let { uri ->
            packageFromIntentUri(context, uri)?.let { return it }
        }
        return null
    }

    private fun packageFromIntentUri(context: Context, intentUri: String): String? {
        val intent = runCatching {
            Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return null
        return context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName?.takeIf { it.isNotBlank() }
    }

    private fun packageFromComponentFlat(componentFlat: String): String? =
        componentFlat.substringBefore('/').trim().takeIf { it.isNotBlank() }

    private fun iconBitmapFromDrawable(drawable: Drawable, size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val mutate = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        mutate.setBounds(0, 0, size, size)
        mutate.draw(canvas)
        return bitmap
    }

    private fun renderFolderBitmap(
        item: QuickLauncherItem,
        appsByPackage: Map<String, AppInfo>,
        size: Int,
        context: Context?,
        activityShortcuts: List<ActivityShortcut>,
        shellCommands: List<ShellCommand>,
    ): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val children = item.folderItems()
        val corner = size * 0.22f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 48, 48, 54)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(50, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = (size * 0.02f).coerceAtLeast(1f)
        }
        val plateRect = RectF(
            strokePaint.strokeWidth / 2f,
            strokePaint.strokeWidth / 2f,
            size - strokePaint.strokeWidth / 2f,
            size - strokePaint.strokeWidth / 2f,
        )
        canvas.drawRoundRect(plateRect, corner, corner, bgPaint)
        canvas.drawRoundRect(plateRect, corner, corner, strokePaint)

        if (children.isEmpty()) {
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = (size * 0.05f).coerceAtLeast(2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val w = size * 0.44f
            val h = size * 0.34f
            val cx = size / 2f
            val cy = size / 2f
            val l = cx - w / 2f
            val t = cy - h / 2f
            val r = cx + w / 2f
            val b = cy + h / 2f
            val tabW = w * 0.38f
            val tabH = h * 0.25f
            val folderPath = Path().apply {
                moveTo(l, t + tabH)
                lineTo(l + tabW * 0.8f, t + tabH)
                lineTo(l + tabW, t)
                lineTo(r - corner * 0.5f, t)
                quadTo(r, t, r, t + corner * 0.5f)
                lineTo(r, b - corner * 0.5f)
                quadTo(r, b, r - corner * 0.5f, b)
                lineTo(l + corner * 0.5f, b)
                quadTo(l, b, l, b - corner * 0.5f)
                close()
            }
            canvas.drawPath(folderPath, iconPaint)
            return bitmap
        }

        val miniItems = children.take(4)
        val miniSize = size * 0.36f
        val gap = (size - miniSize * 2f) / 3f
        val miniClipPath = Path()
        val miniRect = RectF()
        val miniCorner = miniSize * 0.22f
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (i in miniItems.indices) {
            val child = miniItems[i]
            val col = i % 2
            val row = i / 2
            val left = gap + col * (miniSize + gap)
            val top = gap + row * (miniSize + gap)
            miniRect.set(left, top, left + miniSize, top + miniSize)

            val childBmp = iconBitmap(
                item = child,
                appsByPackage = appsByPackage,
                size = miniSize.toInt().coerceAtLeast(16),
                context = context,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
            )

            miniClipPath.reset()
            miniClipPath.addRoundRect(miniRect, miniCorner, miniCorner, Path.Direction.CW)
            if (childBmp != null) {
                canvas.withClip(miniClipPath) {
                    drawBitmap(childBmp, null, miniRect, bmpPaint)
                }
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(120, 100, 100, 110)
                }
                canvas.drawRoundRect(miniRect, miniCorner, miniCorner, placeholderPaint)
            }
        }
        return bitmap
    }
}
