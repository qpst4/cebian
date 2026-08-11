package com.slideindex.app.overlay

import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.ShellCommandEditorTrampoline
import com.slideindex.app.service.ShellCommandResultTrampoline

/**
 * Trampoline Activities that replace overlay touch handling.
 * Shell 命令面板浮窗刻意保留侧边触钮，故不纳入此处（回桌面时 Activity 可能不 destroy）。
 */
internal object OverlayTrampolineGuard {
    fun blocksOverlayPresentationTouch(): Boolean =
        QuickLauncherAddTrampoline.isActive()

    fun blocksOverlayResume(): Boolean =
        blocksOverlayPresentationTouch() ||
            ShellCommandEditorTrampoline.isActive() ||
            ShellCommandResultTrampoline.isActive()
}
