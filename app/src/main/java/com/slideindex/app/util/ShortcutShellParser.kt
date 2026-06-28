package com.slideindex.app.util

internal object ShortcutShellParser {
    private val idPattern = Regex("""(?mi)\bid=([^,\s}]+)""")
    private val packagePattern = Regex("""(?mi)\bpackageName=([^,\s}]+)""")
    private val labelPatterns = listOf(
        Regex("""(?mi)\btitle=([^,}]+)"""),
        Regex("""(?mi)\bshortLabel=([^,\n}]+)"""),
        Regex("""(?mi)\blongLabel=([^,\n}]+)"""),
        Regex("""(?mi)\blabel=([^,}]+)"""),
        Regex("""(?mi)\btext=([^,}]+)"""),
        Regex("""(?mi)\bname=([^,}]+)"""),
    )
    private val disabledPattern = Regex("""(?mi)\[Dis\]|(?:^|\s)enabled=false\b""")
    private val pinnedLinePattern = Regex("""(?mi)^\s*Pinned:\s*(\S+)\s*$""")

    fun parse(dump: String, packageName: String? = null): List<Pair<String, String>> {
        if (dump.isBlank()) return emptyList()
        val merged = linkedMapOf<String, String>()
        dump.lineSequence().forEach { line ->
            if (!line.contains("ShortcutInfo", ignoreCase = true)) return@forEach
            parseEntry(line, packageName)?.let { (id, label) ->
                merged.putIfAbsent(id, label)
            }
        }
        extractShortcutInfoBlocks(dump).forEach { block ->
            parseEntry(block, packageName)?.let { (id, label) ->
                merged.putIfAbsent(id, label)
            }
        }
        return merged.map { it.key to it.value }
    }

    /** Pinned shortcut IDs that the default launcher shows for [targetPackage]. */
    fun parseLauncherPinnedIds(
        dump: String,
        launcherPackage: String,
        targetPackage: String,
    ): List<String> {
        if (dump.isBlank() || launcherPackage.isBlank() || targetPackage.isBlank()) return emptyList()
        val lines = dump.lineSequence().toList()
        var inLauncher = false
        var inTargetPackage = false
        val ids = linkedSetOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Launcher:") -> {
                    val launcher = trimmed.substringAfter("Launcher:").trim()
                    inLauncher = launcher == launcherPackage
                    inTargetPackage = false
                }
                inLauncher && trimmed.startsWith("Package:") -> {
                    val pkg = trimmed.substringAfter("Package:").trim()
                    inTargetPackage = pkg == targetPackage
                }
                inLauncher && inTargetPackage -> {
                    pinnedLinePattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
                        if (isValidId(id)) ids += id
                    }
                    if (trimmed.startsWith("Package:") && !trimmed.endsWith(targetPackage)) {
                        inTargetPackage = false
                    }
                }
                trimmed.startsWith("Launcher:") && inLauncher -> {
                    // next launcher section
                }
            }
        }
        return ids.toList()
    }

    private fun extractShortcutInfoBlocks(dump: String): List<String> {
        val blocks = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < dump.length) {
            val start = dump.indexOf("ShortcutInfo {", searchFrom, ignoreCase = true)
            if (start < 0) break
            val braceStart = dump.indexOf('{', start)
            if (braceStart < 0) break
            var depth = 0
            var end = -1
            for (i in braceStart until dump.length) {
                when (dump[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }
            if (end < 0) break
            blocks += dump.substring(start, end + 1)
            searchFrom = end + 1
        }
        return blocks
    }

    private fun parseEntry(text: String, packageName: String?): Pair<String, String>? {
        if (text.isBlank() || disabledPattern.containsMatchIn(text)) return null
        if (packageName != null && text.contains("packageName=")) {
            val pkg = packagePattern.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (pkg != null && pkg != packageName) return null
        }
        val id = idPattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { isValidId(it) } ?: return null
        val label = readLabel(text) ?: return null
        return id to label
    }

    private fun readLabel(text: String): String? {
        val rawLabel = labelPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
        }?.trim('"')?.takeIf { it.isNotEmpty() } ?: return null
        val cleaned = rawLabel
            .substringBefore(" characters=")
            .substringBefore(" chars:")
            .trim()
            .trim('"')
        if (cleaned.isEmpty()) return null
        if (ShortcutDisplayRules.isInternalKey(cleaned)) return null
        return cleaned
    }

    private fun isValidId(id: String): Boolean {
        if (id.isBlank() || id == "null") return false
        if (id.startsWith("***")) return false
        return !id.contains('=')
    }
}
