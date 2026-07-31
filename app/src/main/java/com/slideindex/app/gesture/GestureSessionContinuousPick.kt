package com.slideindex.app.gesture

internal class GestureSessionContinuousPick {
    var taskSwitcher = false
    var quickLauncher = false
    var shell = false
    var honeycomb = false

    fun taskSwitcherActive(): Boolean = taskSwitcher

    fun quickLauncherActive(): Boolean = quickLauncher

    fun shellActive(): Boolean = shell

    fun honeycombActive(): Boolean = honeycomb

    fun clearQuickLauncher() {
        quickLauncher = false
    }

    fun clearShell() {
        shell = false
    }

    fun clearHoneycomb() {
        honeycomb = false
    }

    fun reset() {
        taskSwitcher = false
        quickLauncher = false
        shell = false
        honeycomb = false
    }
}
