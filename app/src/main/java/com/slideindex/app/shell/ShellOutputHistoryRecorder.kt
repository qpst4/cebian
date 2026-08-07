package com.slideindex.app.shell

import android.content.Context
import com.slideindex.app.di.AppGraphEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

object ShellOutputHistoryRecorder {
    fun record(
        context: Context,
        label: String,
        command: String,
        exitCode: Int,
        output: String,
    ) {
        val deps = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AppGraphEntryPoint::class.java,
            ).dependencies()
        }.getOrNull() ?: return
        deps.applicationScope.launch {
            deps.shellOutputHistoryRepository.append(
                label = label,
                command = command,
                exitCode = exitCode,
                output = output,
            )
        }
    }
}
