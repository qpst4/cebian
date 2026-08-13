package com.slideindex.app.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toDrawable
import android.graphics.drawable.Drawable
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ManagedShortcutIconResolver
import com.slideindex.app.data.AppInfo
import android.graphics.Color
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
        if (item.type == QuickLauncherItemType.ACTION &&
            shouldUseGestureVectorIcon(item, shellCommands)
        ) {
            val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return null
            return GestureActionIconBitmap.get(
                action = action,
                sizePx = size.coerceAtLeast(1),
                tintArgb = actionIconTintArgb,
                outlined = true,
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
            QuickLauncherItemType.APP -> getIconSafe(appsByPackage[item.payload], context)
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
}
