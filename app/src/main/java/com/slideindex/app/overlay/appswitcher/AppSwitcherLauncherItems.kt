package com.slideindex.app.overlay.appswitcher

import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType

/** Items shown in the app switcher overlay (matches runtime filter). */
fun List<QuickLauncherItem>.appSwitcherRuntimeItems(): List<QuickLauncherItem> =
    filter {
        it.type == QuickLauncherItemType.APP ||
            it.type == QuickLauncherItemType.SHORTCUT ||
            it.type == QuickLauncherItemType.ACTION
    }
