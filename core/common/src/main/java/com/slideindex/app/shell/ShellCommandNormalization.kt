package com.slideindex.app.shell

/**
 * Strips PC-side `adb shell` prefixes so pasted tutorial commands run on-device via Shizuku.
 */
private val ADB_SHELL_PREFIX = Regex(
    """^adb(?:\s+-[\w.-]+)*\s+shell\s+""",
    RegexOption.IGNORE_CASE,
)

fun normalizeShellCommand(raw: String): String =
    raw.trim().replace(ADB_SHELL_PREFIX, "").trim()

fun ShellCommand.withNormalizedCommand(): ShellCommand =
    copy(command = normalizeShellCommand(command))
