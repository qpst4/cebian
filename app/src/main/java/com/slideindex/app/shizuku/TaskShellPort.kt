package com.slideindex.app.shizuku

import com.slideindex.app.util.TaskManagerUtil

internal interface TaskShellPort {
    fun shellCommand(vararg cmd: String): Boolean

    fun shellOutput(vararg cmd: String): String
}

internal object DefaultTaskShellPort : TaskShellPort {
    override fun shellCommand(vararg cmd: String): Boolean =
        TaskManagerShellExecutor.shellCommand(*cmd)

    override fun shellOutput(vararg cmd: String): String =
        TaskManagerShellExecutor.shellOutput(*cmd)
}

internal object RootTaskShellPort : TaskShellPort {
    override fun shellCommand(vararg cmd: String): Boolean =
        runRootCommand(*cmd).success

    override fun shellOutput(vararg cmd: String): String =
        runRootCommand(*cmd).output

    private fun runRootCommand(vararg cmd: String): TaskManagerUtil.ShellCommandResult {
        val command = cmd.joinToString(" ") { arg ->
            if (arg.contains(' ')) {
                "'" + arg.replace("'", "'\\''") + "'"
            } else {
                arg
            }
        }
        val result = TaskManagerShellExecutor.runAsRootUser(command)
        return TaskManagerUtil.ShellCommandResult(result.exitCode, result.output)
    }
}
