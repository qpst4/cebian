package com.slideindex.app.ui.navigation

import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder

fun NavEntryBuilder.extensionNavEntries(ctx: MainNavContext) {
    layoutSettingsNavEntries(ctx)
    nativeEnginePackNavEntry(ctx)
    extensionHubNavEntries(ctx)
    quickLauncherNavEntries(ctx)
    honeycombLauncherNavEntries(ctx)
    holographicLauncherNavEntries(ctx)
    activityShortcutNavEntries(ctx)
    shellCommandNavEntries(ctx)
    widgetPanelNavEntries(ctx)
    stashClipboardNavEntries(ctx)
    searchPanelNavEntries(ctx)
    floatBallNavEntries(ctx)
    floatingPointerNavEntries(ctx)
}
