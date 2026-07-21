package com.slideindex.app.overlay

import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.ShellCommandEditorTrampoline
import com.slideindex.app.service.ShellCommandPanelTrampoline
import com.slideindex.app.service.ShellCommandResultTrampoline

/**
 * Trampoline Activities replace overlay touch handling; while they are active the edge overlay
 * must stay detached and must not re-attach presentation or capture windows.
 */
internal object OverlayTrampolineGuard {
    fun blocksOverlayPresentationTouch(): Boolean =
        QuickLauncherAddTrampoline.isActive() ||
            ShellCommandPanelTrampoline.isActive()

    fun blocksOverlayResume(): Boolean =
        blocksOverlayPresentationTouch() ||
            ShellCommandEditorTrampoline.isActive() ||
            ShellCommandResultTrampoline.isActive()
}
