package com.slideindex.app.overlay.appswitcher

import android.content.Context
import android.graphics.Bitmap
import com.slideindex.app.settings.FvAppSwitcherSlotIconOverride
import com.slideindex.app.shell.ShellCommandIconResolver

object FvAppSwitcherSlotIconResolver {
    fun resolveBitmap(
        context: Context,
        override: FvAppSwitcherSlotIconOverride,
        fallbackLabel: String,
        sizePx: Int,
    ): Bitmap? {
        if (!override.iconPath.isNullOrBlank()) {
            return ShellCommandIconResolver.loadUriBitmap(context, override.iconPath, sizePx)
        }
        if (!override.textIcon.isNullOrBlank()) {
            return ShellCommandIconResolver.renderTextBitmap(override.textIcon, fallbackLabel, sizePx)
        }
        return null
    }
}
