package com.slideindex.app.launcher

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureRule
import java.util.UUID

object QuickLauncherPanelMutator {
    fun addPanel(
        panels: List<QuickLauncherPanel>,
        defaultColumns: Int = 3,
        defaultRows: Int = 4,
        name: String = QuickLauncherPanelDefaults.nextPanelName(panels.size),
    ): List<QuickLauncherPanel>? {
        val effective = QuickLauncherPanelDefaults.effectivePanels(panels)
        if (effective.size >= QuickLauncherPanelDefaults.MAX_PANELS) return null
        return effective + QuickLauncherPanelDefaults.defaultPanel(
            name = name,
            columnsPerPage = defaultColumns,
            rowsPerPage = defaultRows,
            id = UUID.randomUUID().toString(),
        )
    }

    fun duplicatePanel(panels: List<QuickLauncherPanel>, panelId: String): List<QuickLauncherPanel>? {
        val effective = QuickLauncherPanelDefaults.effectivePanels(panels)
        if (effective.size >= QuickLauncherPanelDefaults.MAX_PANELS) return null
        val source = effective.firstOrNull { it.id == panelId } ?: return null
        val copyName = source.name.ifBlank {
            QuickLauncherPanelDefaults.nextPanelName(effective.size)
        } + " (copy)"
        return effective + source.copy(
            id = UUID.randomUUID().toString(),
            name = copyName,
        )
    }

    fun removePanel(panels: List<QuickLauncherPanel>, panelId: String): List<QuickLauncherPanel>? {
        val effective = QuickLauncherPanelDefaults.effectivePanels(panels)
        if (effective.size <= 1) return null
        return effective.filterNot { it.id == panelId }
    }

    fun replacePanel(
        panels: List<QuickLauncherPanel>,
        panelId: String,
        updated: QuickLauncherPanel,
    ): List<QuickLauncherPanel> {
        val effective = QuickLauncherPanelDefaults.effectivePanels(panels)
        val index = effective.indexOfFirst { it.id == panelId }
        if (index < 0) return effective
        return effective.toMutableList().also { it[index] = updated.copy(id = panelId) }
    }

    fun updatePanelItems(
        panels: List<QuickLauncherPanel>,
        panelId: String,
        items: List<QuickLauncherItem>,
    ): List<QuickLauncherPanel> {
        val effective = QuickLauncherPanelDefaults.effectivePanels(panels)
        val index = effective.indexOfFirst { it.id == panelId }
        if (index < 0) return effective
        return effective.toMutableList().also { it[index] = effective[index].copy(items = items) }
    }

    fun remapQuickLauncherAction(action: GestureAction, removedPanelId: String, fallbackPanelId: String): GestureAction {
        if (action !is GestureAction.QuickLauncher) return action
        if (action.panelId != removedPanelId) return action
        return action.copy(panelId = fallbackPanelId)
    }

    fun sanitizeGestureRules(
        rules: List<GestureRule>,
        validPanelIds: Set<String>,
        fallbackPanelId: String,
    ): List<GestureRule> = rules.map { rule ->
        val action = rule.action
        if (action is GestureAction.QuickLauncher &&
            action.panelId.isNotBlank() &&
            action.panelId !in validPanelIds
        ) {
            rule.copy(action = action.copy(panelId = fallbackPanelId))
        } else {
            rule
        }
    }

    fun sanitizeQuickLauncherAction(
        action: GestureAction,
        validPanelIds: Set<String>,
        fallbackPanelId: String,
    ): GestureAction {
        if (action !is GestureAction.QuickLauncher) return action
        if (action.panelId.isBlank() || action.panelId in validPanelIds) return action
        return action.copy(panelId = fallbackPanelId)
    }
}
