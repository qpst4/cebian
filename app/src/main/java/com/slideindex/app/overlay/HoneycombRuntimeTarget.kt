package com.slideindex.app.overlay

/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.graphics.drawable.Drawable
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.showsShortcutBadge

class HoneycombRuntimeTarget(
    val item: QuickLauncherItem,
    @JvmField val label: String,
    @JvmField var icon: Drawable?,
) {
    val isShortcut: Boolean = item.showsShortcutBadge()
}
