package com.slideindex.app.launcher

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver

fun GestureAction.showsShellCommandBadge(shellCommands: List<ShellCommand>): Boolean {
    if (this !is GestureAction.ExecuteShellCommand) return false
    return ShellCommandIconResolver.findForCommandLine(command, shellCommands) != null
}

fun QuickLauncherItem.showsShellCommandBadge(shellCommands: List<ShellCommand>): Boolean {
    if (type != QuickLauncherItemType.ACTION) return false
    val action = QuickLauncherItemCodec.parseActionPayload(payload) ?: return false
    return action.showsShellCommandBadge(shellCommands)
}
