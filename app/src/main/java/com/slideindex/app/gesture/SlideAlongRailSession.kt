package com.slideindex.app.gesture

import com.slideindex.app.data.AppInfo
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedLaunchPolicy

interface IndexSessionHost {
    fun hapticLetterTick()
    fun hapticAppTick()
    fun hapticConfirmLaunch()
    fun scheduleDelayed(runnable: Runnable, delayMs: Long)
    fun cancelDelayed(runnable: Runnable)
    fun requestInvalidate()
}

class SlideAlongRailSession(
    private val side: com.slideindex.app.overlay.PanelSide,
    private val zoneLayout: GestureZoneLayout,
    private val host: IndexSessionHost,
) {
    private var settings = AppSettings()
    private var apps: List<AppInfo> = emptyList()
    private var appsByLetter: Map<Char, List<AppInfo>> = emptyMap()
    private val railLetters: List<Char> = ('A'..'Z').toList() + '#'

    var selectedLetter: Char? = null
        private set
    var highlightedApp: AppInfo? = null
        private set
    var filteredApps: List<AppInfo> = emptyList()
        private set
    var longPressArmed = false
        private set

    val gridCellBounds = mutableListOf<Pair<AppInfo, android.graphics.RectF>>()

    private var longPressTrackingApp: AppInfo? = null
    private var longPressRunnable: Runnable? = null

    fun applySettings(newSettings: AppSettings) {
        settings = newSettings
    }

    fun setApps(newApps: List<AppInfo>) {
        apps = newApps
        appsByLetter = newApps.groupBy { it.letter }
    }

    fun resetSelection() {
        cancelLongPressTracking()
        selectedLetter = null
        highlightedApp = null
        filteredApps = emptyList()
        gridCellBounds.clear()
    }

    fun updateSelection(localX: Float, localY: Float): Boolean {
        if (zoneLayout.isInRailZone(localX)) {
            val hadLongPress = longPressArmed
            cancelLongPressTracking()
            val letter = letterAtY(localY) ?: return hadLongPress
            if (selectedLetter != letter) {
                selectedLetter = letter
                filteredApps = appsByLetter[letter].orEmpty()
                highlightedApp = null
                gridCellBounds.clear()
                host.hapticLetterTick()
                return true
            }
            return hadLongPress
        }
        val app = appAtGrid(localX, localY)
        if (app == highlightedApp) return false
        cancelLongPressTracking()
        highlightedApp = app
        if (app != null) {
            host.hapticAppTick()
            scheduleLongPressTracking(app)
        }
        return true
    }

    fun selectedLetterCenterY(): Float? {
        val letter = selectedLetter ?: return null
        val rail = zoneLayout.indexRailRect()
        val index = railLetters.indexOf(letter)
        if (index < 0) return null
        val slotHeight = rail.height() / railLetters.size
        return rail.top + slotHeight * index + slotHeight * 0.65f
    }

    private fun scheduleLongPressTracking(app: AppInfo) {
        if (!settings.freeWindowEnabled || !settings.resolvedLaunchPolicy().usesLongPress()) return
        longPressTrackingApp = app
        longPressArmed = false
        val runnable = Runnable {
            if (highlightedApp == longPressTrackingApp) {
                longPressArmed = true
                host.hapticAppTick()
                host.requestInvalidate()
            }
        }
        longPressRunnable = runnable
        host.scheduleDelayed(runnable, settings.effectiveLongPressDurationMs().toLong())
    }

    private fun cancelLongPressTracking() {
        longPressRunnable?.let { host.cancelDelayed(it) }
        longPressRunnable = null
        longPressTrackingApp = null
        longPressArmed = false
    }

    fun endSession() {
        cancelLongPressTracking()
        resetSelection()
    }

    private fun letterAtY(localY: Float): Char? {
        val rail = zoneLayout.indexRailRect()
        val y = (localY - rail.top).coerceIn(0f, rail.height())
        val index = ((y / rail.height()) * railLetters.size)
            .toInt()
            .coerceIn(0, railLetters.lastIndex)
        return railLetters[index]
    }

    private fun appAtGrid(localX: Float, localY: Float): AppInfo? {
        gridCellBounds.forEach { (app, rect) ->
            if (rect.contains(localX, localY)) return app
        }
        return null
    }
}
