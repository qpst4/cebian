package com.slideindex.app.shell

/**
 * Strips PC-side `adb shell` prefixes so pasted tutorial commands run on-device via Shizuku.
 */
private val ADB_SHELL_PREFIX = Regex(
    """^adb\b(?:\s+\S+)*?\s+shell\s+""",
    RegexOption.IGNORE_CASE,
)

fun normalizeShellCommand(raw: String): String =
    raw.trim().replace(ADB_SHELL_PREFIX, "").trim()

private const val AUTO_LABEL_MAX_LENGTH = 24

/** Uses [label] when non-blank; otherwise derives a short title from [command]. */
fun resolveShellCommandLabel(label: String, command: String): String {
    val trimmedLabel = label.trim()
    if (trimmedLabel.isNotEmpty()) return trimmedLabel
    val normalized = normalizeShellCommand(command)
    if (normalized.isBlank()) return ""
    val firstLine = normalized.lineSequence().first().trim()
    if (firstLine.length <= AUTO_LABEL_MAX_LENGTH) return firstLine
    return firstLine.take(AUTO_LABEL_MAX_LENGTH).trimEnd() + "…"
}

fun ShellCommand.withNormalizedCommand(): ShellCommand =
    copy(command = normalizeShellCommand(command))
