package com.slideindex.app.util

import android.content.Context
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandTemplate
import com.slideindex.app.shell.ShellOutputHistoryRecorder
import com.slideindex.app.shell.ShellTemplateContext
import com.slideindex.app.shell.ShellTemplateContextFactory
import com.slideindex.app.shell.withNormalizedCommand

data class ShellCommandRunOutcome(
    val exitCode: Int,
    val output: String,
    val expandedCommand: String,
)

object ShellCommandRunner {
    fun execute(
        context: Context,
        command: ShellCommand,
        templateContext: ShellTemplateContext = ShellTemplateContextFactory.current(),
    ): ShellCommandRunOutcome {
        val normalized = command.withNormalizedCommand()
        val expandedCommand = ShellCommandTemplate.expand(normalized.command, templateContext)
        val toRun = normalized.copy(command = expandedCommand)
        val result = ShellCommandExecutor.execute(toRun)
        ShellOutputHistoryRecorder.record(
            context = context,
            label = command.label,
            command = expandedCommand,
            exitCode = result.exitCode,
            output = result.output,
        )
        return ShellCommandRunOutcome(
            exitCode = result.exitCode,
            output = result.output,
            expandedCommand = expandedCommand,
        )
    }
}
