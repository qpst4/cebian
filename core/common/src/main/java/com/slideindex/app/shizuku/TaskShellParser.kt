package com.slideindex.app.shizuku

import com.slideindex.app.util.TaskExclusions
import com.slideindex.app.util.RecentPackageResolver
import java.util.regex.Pattern

data class ShellTaskEntry(
    val taskId: Int,
    val packageName: String,
    val rawIdentifier: String = packageName,
    val taskTitle: String? = null,
)

object TaskShellParser {

    private val RECENT_TASK_ID = Pattern.compile(
        "(?:Recent #\\d+: )?Task(?:Record)?\\{[^#]*#(\\d+)\\b",
    )
    private val RECENT_A_FIELD = Pattern.compile("[\\s{]A=(?:\\d+:)?([^\\s}/]+)")
    private val RECENT_COMPONENT_FIELD = Pattern.compile("[\\s{](?:I|aI)=([^\\s}/]+)")
    private val CMP_FIELD = Pattern.compile("cmp=([\\w.]+)/")
    private val TASK_ID_HEADER = Pattern.compile("Task id #(\\d+)")
    private val CMD_TASK_HEADER = Pattern.compile("^\\*?\\s*TASK\\s+(\\d+):\\s*([\\w.]+)")
    private val TASK_ID_FIELD = Pattern.compile("\\btaskId=(\\d+)\\b")
    private val TASK_ID_LINE = Pattern.compile("Task id #(\\d+)")
    private val PACKAGE_NAME_FIELD = Pattern.compile("(?:packageName|realActivity|baseActivity)=([\\w.]+)")
    private val RESUMED_ACTIVITY = Pattern.compile(
        "(?:mResumedActivity|ResumedActivity|topResumedActivity)[^\\n]*\\bt(\\d+)\\b",
    )
    private val GENERIC_TASK_ID = Pattern.compile("\\bt(\\d+)\\b")
    private val GENERIC_HASH_ID = Pattern.compile("#(\\d+)")
    private val TASK_RECORD_HEADER = Pattern.compile(
        "Task(?:Record)?\\{[^#]*#(\\d+)\\b[^}]*(?:A=(?:\\d+:)?([^\\s}/]+)|(?:I|aI)=([^\\s}/]+))",
    )
    private val TASK_DESC_LABEL = Pattern.compile("""taskDescription.*\blabel="([^"]+)"""")
    private val TASK_DESC_LABEL_PLAIN = Pattern.compile("""taskDescription.*\blabel=([^\s},]+)""")
    private val TASK_TITLE_QUOTED = Pattern.compile("""\btitle="([^"]+)"""")
    private val TASK_TITLE_PLAIN = Pattern.compile("""\btitle=([^\s},]+)""")
    private val TASK_DESC_LABEL_CN = Pattern.compile("""\b(?:lastDescription|description|contentTitle|mLabel)="([^"]+)"""")
    private val TASK_DESC_LABEL_CN_PLAIN = Pattern.compile("""\b(?:lastDescription|description|contentTitle|mLabel)=([^\s},]+)""")
    private val ACTIVITY_RECORD_COMPONENT = Pattern.compile(
        "ActivityRecord\\{[^}]*? u\\d+ ([^\\s}]+) t(\\d+)\\b",
    )
    private val HIST_ACTIVITY_COMPONENT = Pattern.compile(
        "Hist #\\d+: ActivityRecord\\{[^}]*? u\\d+ ([^\\s}]+) t(\\d+)\\b",
    )
    private val M_LAST_TASK_DESC = Pattern.compile(
        "mLastTaskDescription=TaskDescription\\s*\\{[^}]*?label=\"([^\"]+)\"",
    )
    private val REAL_ACTIVITY_FIELD = Pattern.compile("realActivity=([^\\s}/]+(?:/[^\\s}/]+)?)")
    private val TOP_ACTIVITY_FIELD = Pattern.compile("(?:topActivity|mTopActivity|resumedActivity)=")

    private val EXCLUDED_PACKAGES = TaskExclusions.LAUNCHER_AND_SYSTEM

    /** Task card titles from dumpsys (taskDescription / window title). */
    fun parseTaskTitles(vararg sources: String): Map<Int, String> {
        val out = linkedMapOf<Int, String>()
        sources.forEach { source ->
            if (source.isBlank()) return@forEach
            parseTaskBlockTitles(source, out)
            parseWindowTitles(source, out)
            parseProximityTitles(source, out)
        }
        return out
    }

    /** Top/resumed activity component per task id. */
    fun parseTaskTopComponents(vararg sources: String): Map<Int, String> {
        val perTask = linkedMapOf<Int, MutableList<String>>()
        sources.forEach { source ->
            if (source.isBlank()) return@forEach
            collectActivityRecordComponents(source, perTask)
            collectTopActivityLines(source, perTask)
            collectRealActivityFields(source, perTask)
        }
        return perTask.mapValues { (_, components) -> components.last() }
    }

    fun enrichTaskEntry(
        entry: ShellTaskEntry,
        titleMap: Map<Int, String>,
        componentMap: Map<Int, String>,
    ): ShellTaskEntry {
        val component = componentMap[entry.taskId]
        return ShellTaskEntry(
            taskId = entry.taskId,
            packageName = entry.packageName,
            rawIdentifier = if (component != null) {
                preferRawIdentifier(component, entry.rawIdentifier)
            } else {
                entry.rawIdentifier
            },
            taskTitle = preferNonBlank(entry.taskTitle, titleMap[entry.taskId]),
        )
    }

    private fun parseTaskBlockTitles(source: String, out: MutableMap<Int, String>) {
        var currentTaskId: Int? = null
        for (line in source.lineSequence()) {
            RECENT_TASK_ID.matcher(line).let { matcher ->
                if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
            }
            TASK_ID_LINE.matcher(line).let { matcher ->
                if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
            }
            if (line.contains("Task{") || line.contains("TaskRecord{")) {
                GENERIC_HASH_ID.matcher(line).let { matcher ->
                    if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
                }
            }
            M_LAST_TASK_DESC.matcher(line).let { matcher ->
                if (matcher.find()) {
                    val title = cleanTaskTitle(matcher.group(1)) ?: return@let
                    val taskId = currentTaskId ?: parseTaskIdFromActivityLine(line) ?: return@let
                    out.putIfAbsent(taskId, title)
                }
            }
            extractTitleFromLine(line)?.let { title ->
                val taskId = currentTaskId ?: parseTaskIdFromActivityLine(line) ?: return@let
                out.putIfAbsent(taskId, title)
            }
        }
    }

    private fun parseWindowTitles(source: String, out: MutableMap<Int, String>) {
        if (!source.contains("Window #") && !source.contains("mWindowAttributes")) return
        var currentTaskId: Int? = null
        for (line in source.lineSequence()) {
            if (line.contains("taskId=")) {
                currentTaskId = TASK_ID_FIELD.matcher(line).let { matcher ->
                    if (matcher.find()) matcher.group(1)?.toIntOrNull() else null
                } ?: line.substringAfter("taskId=").substringBefore(' ').substringBefore('}').toIntOrNull()
            }
            TASK_TITLE_PLAIN.matcher(line).let { matcher ->
                if (matcher.find()) {
                    val title = cleanTaskTitle(matcher.group(1)) ?: return@let
                    val taskId = currentTaskId ?: return@let
                    if (title.length in 2..40) out.putIfAbsent(taskId, title)
                }
            }
        }
    }

    private fun parseProximityTitles(source: String, out: MutableMap<Int, String>) {
        GENERIC_TASK_ID.matcher(source).let { matcher ->
            while (matcher.find()) {
                val taskId = matcher.group(1)?.toIntOrNull() ?: continue
                if (out.containsKey(taskId)) continue
                val start = (matcher.start() - 120).coerceAtLeast(0)
                val end = (matcher.end() + 900).coerceAtMost(source.length)
                val window = source.substring(start, end)
                extractFirstTitleFromText(window)?.let { title ->
                    out.putIfAbsent(taskId, title)
                }
            }
        }
    }

    private fun extractFirstTitleFromText(text: String): String? {
        M_LAST_TASK_DESC.matcher(text).let { matcher ->
            if (matcher.find()) return cleanTaskTitle(matcher.group(1))
        }
        for (line in text.lineSequence()) {
            extractTitleFromLine(line)?.let { return it }
        }
        return null
    }

    private fun collectActivityRecordComponents(source: String, perTask: MutableMap<Int, MutableList<String>>) {
        listOf(ACTIVITY_RECORD_COMPONENT, HIST_ACTIVITY_COMPONENT).forEach { pattern ->
            pattern.matcher(source).let { matcher ->
                while (matcher.find()) {
                    val component = matcher.group(1)?.trim().orEmpty()
                    val taskId = matcher.group(2)?.toIntOrNull() ?: continue
                    if (component.contains('/')) {
                        perTask.getOrPut(taskId) { mutableListOf() }.add(component)
                    }
                }
            }
        }
    }

    private fun collectTopActivityLines(source: String, perTask: MutableMap<Int, MutableList<String>>) {
        var currentTaskId: Int? = null
        for (line in source.lineSequence()) {
            TASK_ID_LINE.matcher(line).let { matcher ->
                if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
            }
            if (!TOP_ACTIVITY_FIELD.matcher(line).find()) continue
            val component = parseComponentFromActivityLine(line) ?: continue
            val taskId = parseTaskIdFromActivityLine(line) ?: currentTaskId ?: continue
            perTask.getOrPut(taskId) { mutableListOf() }.add(component)
        }
    }

    private fun collectRealActivityFields(source: String, perTask: MutableMap<Int, MutableList<String>>) {
        var currentTaskId: Int? = null
        for (line in source.lineSequence()) {
            TASK_ID_LINE.matcher(line).let { matcher ->
                if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
            }
            if (line.contains("Task{") || line.contains("TaskRecord{")) {
                GENERIC_HASH_ID.matcher(line).let { matcher ->
                    if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
                }
            }
            REAL_ACTIVITY_FIELD.matcher(line).let { matcher ->
                if (!matcher.find()) return@let
                val component = matcher.group(1)?.trim().orEmpty()
                if (!component.contains('/')) return@let
                val taskId = currentTaskId ?: parseTaskIdFromActivityLine(line) ?: return@let
                perTask.getOrPut(taskId) { mutableListOf() }.add(component)
            }
        }
    }

    fun parseRecentsDump(output: String): List<ShellTaskEntry> {
        val entries = mutableListOf<ShellTaskEntry>()
        var pendingTaskId: Int? = null
        var pendingRawIdentifier: String? = null
        var pendingTitle: String? = null
        var inRecentsFile = output.contains("ACTIVITY MANAGER RECENT TASKS")

        fun flushPending() {
            val id = pendingTaskId ?: return
            addEntry(entries, id, pendingRawIdentifier, pendingTitle)
            pendingTaskId = null
            pendingRawIdentifier = null
            pendingTitle = null
        }

        for (line in output.lineSequence()) {
            when {
                line.contains("ACTIVITY MANAGER RECENT TASKS") -> inRecentsFile = true
                inRecentsFile && line.startsWith("ACTIVITY MANAGER") &&
                    !line.contains("RECENT TASKS") -> inRecentsFile = false
                line.contains("Recent tasks:") -> inRecentsFile = true
            }
            val header = RECENT_TASK_ID.matcher(line)
            if (header.find() && (inRecentsFile || line.contains("Recent #"))) {
                flushPending()
                pendingTaskId = header.group(1)?.toIntOrNull()
                pendingRawIdentifier = extractRawIdentifierFromRecentLine(line)
                pendingTitle = extractTitleFromLine(line)
                if (pendingRawIdentifier != null) {
                    flushPending()
                }
                continue
            }
            if (pendingTaskId != null) {
                extractTitleFromLine(line)?.let { pendingTitle = it }
                extractRawIdentifierFromLine(line)?.let { pendingRawIdentifier = it }
                if (pendingRawIdentifier != null) {
                    flushPending()
                }
            }
        }
        flushPending()
        return entries
    }

    fun parseRecentsTaskIds(output: String): Set<Int> {
        val ids = linkedSetOf<Int>()
        var inRecentsFile = output.contains("ACTIVITY MANAGER RECENT TASKS")
        for (line in output.lineSequence()) {
            when {
                line.contains("ACTIVITY MANAGER RECENT TASKS") -> inRecentsFile = true
                inRecentsFile && line.startsWith("ACTIVITY MANAGER") &&
                    !line.contains("RECENT TASKS") -> inRecentsFile = false
                line.contains("Recent tasks:") -> inRecentsFile = true
            }
            if (!inRecentsFile && !line.contains("Recent #")) continue
            RECENT_TASK_ID.matcher(line).let { matcher ->
                if (matcher.find()) {
                    matcher.group(1)?.toIntOrNull()?.takeIf(::isValidTaskId)?.let { ids += it }
                }
            }
        }
        return ids
    }

    fun scanPackagesInRecentsDump(recentsDump: String): Set<String> {
        val packages = linkedSetOf<String>()
        var inRecentsFile = recentsDump.contains("ACTIVITY MANAGER RECENT TASKS")
        for (line in recentsDump.lineSequence()) {
            when {
                line.contains("ACTIVITY MANAGER RECENT TASKS") -> inRecentsFile = true
                inRecentsFile && line.startsWith("ACTIVITY MANAGER") &&
                    !line.contains("RECENT TASKS") -> inRecentsFile = false
                line.contains("Recent tasks:") -> inRecentsFile = true
            }
            if (!inRecentsFile && !line.contains("Recent #")) continue
            extractPackageFromRecentLine(line)?.let { raw ->
                packageFromRawIdentifier(raw)?.let { packages += it }
            }
            extractPackageFromLine(line)?.let { pkg ->
                packages += pkg
            }
        }
        parseRecentsDump(recentsDump).forEach { packages += it.packageName }
        return packages.filterNot { it in EXCLUDED_PACKAGES }.toSet()
    }

    fun parseCmdTaskList(output: String): List<ShellTaskEntry> {
        val entries = mutableListOf<ShellTaskEntry>()
        var currentTaskId: Int? = null
        var headerPackage: String? = null
        var bestComponent: String? = null
        var pendingTitle: String? = null

        fun flushCurrent() {
            val id = currentTaskId ?: return
            val raw = bestComponent ?: headerPackage ?: return
            addEntry(entries, id, raw, pendingTitle)
            currentTaskId = null
            headerPackage = null
            bestComponent = null
            pendingTitle = null
        }

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            when {
                CMD_TASK_HEADER.matcher(trimmed).let { matcher ->
                    if (matcher.find()) {
                        flushCurrent()
                        currentTaskId = matcher.group(1)?.toIntOrNull()
                        headerPackage = matcher.group(2)?.trim()?.takeIf { isLikelyPackage(it) }
                        true
                    } else {
                        false
                    }
                } -> Unit
                TASK_ID_HEADER.matcher(line).find() -> {
                    flushCurrent()
                    currentTaskId = TASK_ID_HEADER.matcher(line).let { matcher ->
                        matcher.find()
                        matcher.group(1)?.toIntOrNull()
                    }
                }
                line.contains("taskId=") && currentTaskId == null -> {
                    currentTaskId = TASK_ID_FIELD.matcher(line).let { matcher ->
                        if (matcher.find()) matcher.group(1)?.toIntOrNull() else null
                    } ?: line.substringAfter("taskId=").substringBefore(' ').toIntOrNull()
                }
                currentTaskId != null -> {
                    extractTitleFromLine(line)?.let { pendingTitle = it }
                    when {
                        line.contains("topActivity=") -> {
                            parseComponentFromActivityLine(line)?.let { bestComponent = it }
                        }
                        bestComponent == null && (
                            line.contains("baseActivity=") ||
                                line.contains("realActivity=") ||
                                line.contains("mActivityComponent=")
                            ) -> {
                            parseComponentFromActivityLine(line)?.let { bestComponent = it }
                        }
                        bestComponent == null && line.contains("baseIntent=") -> {
                            parseComponentFromActivityLine(line)?.let { bestComponent = it }
                        }
                    }
                }
            }
        }
        flushCurrent()
        return entries
    }

    fun findTaskIdForIdentifier(identifier: String, vararg outputs: String): Int? {
        val trimmed = identifier.trim()
        if (trimmed.isEmpty()) return null
        listRecentTaskEntries(*outputs).forEach { entry ->
            if (entry.rawIdentifier == trimmed) return entry.taskId
        }
        if (trimmed.contains('/')) {
            findTaskIdForComponent(trimmed, *outputs)?.let { return it }
        }
        val fuzzy = findTaskIdsForPackage(trimmed, *outputs)
        return fuzzy.singleOrNull()
    }

    fun findTaskIdForComponent(component: String, vararg outputs: String): Int? {
        val trimmed = component.trim()
        if (!trimmed.contains('/')) return null
        parseTaskTopComponents(*outputs).forEach { (taskId, topComponent) ->
            if (topComponent == trimmed) return taskId
        }
        val pkg = trimmed.substringBefore('/')
        var cls = trimmed.substringAfter('/')
        if (cls.startsWith('.')) cls = pkg + cls
        val normalized = "$pkg/$cls"
        parseTaskTopComponents(*outputs).forEach { (taskId, topComponent) ->
            if (topComponent == normalized) return taskId
        }
        return null
    }

    fun findTaskIdsForPackage(packageName: String, vararg outputs: String): List<Int> {
        val ids = linkedSetOf<Int>()
        outputs.forEach { output ->
            parseRecentsDump(output).forEach {
                if (matchesEntry(it, packageName)) ids += it.taskId
            }
            parseCmdTaskList(output).forEach {
                if (matchesEntry(it, packageName)) ids += it.taskId
            }
            parseActivitiesDump(output).forEach {
                if (matchesEntry(it, packageName)) ids += it.taskId
            }
        }
        return ids.filter(::isValidTaskId).toList()
    }

    fun listRecentPackages(vararg outputs: String): List<String> {
        val packages = LinkedHashSet<String>()
        listRecentTaskEntries(*outputs).forEach { packages += it.packageName }
        if (outputs.isNotEmpty()) {
            scanPackagesInRecentsDump(outputs[0]).forEach { packages += it }
        }
        packages.removeAll(EXCLUDED_PACKAGES)
        return packages.toList()
    }

    private fun collectRecentPackageNames(vararg outputs: String): List<String> {
        val packages = LinkedHashSet<String>()
        outputs.forEach { output ->
            parseRecentsDump(output).forEach { packages += it.packageName }
            parseCmdTaskList(output).forEach { packages += it.packageName }
            parseAmStackList(output).forEach { packages += it.packageName }
            parseActivitiesDump(output).forEach { packages += it.packageName }
        }
        packages.removeAll(EXCLUDED_PACKAGES)
        return packages.toList()
    }

    fun listRecentTaskEntries(vararg outputs: String): List<ShellTaskEntry> {
        if (outputs.isEmpty()) return emptyList()
        val recentsDump = outputs[0]
        val taskListDump = outputs.getOrNull(1).orEmpty()
        val activitiesDump = outputs.getOrNull(2).orEmpty()
        val ordered = LinkedHashMap<Int, ShellTaskEntry>()

        val cmdByTaskId = if (taskListDump.isNotEmpty()) {
            parseCmdTaskList(taskListDump).associateBy { it.taskId }
        } else {
            emptyMap()
        }

        parseRecentsDump(recentsDump).forEach { entry ->
            if (!shouldIncludeRecentEntry(entry)) return@forEach
            val enriched = enrichFromCmd(entry, cmdByTaskId[entry.taskId])
            putOrMergeEntry(ordered, enriched)
        }

        if (activitiesDump.isNotEmpty()) {
            parseActivitiesDump(activitiesDump).forEach { entry ->
                if (!shouldIncludeRecentEntry(entry)) return@forEach
                val enriched = enrichFromCmd(entry, cmdByTaskId[entry.taskId])
                putOrMergeEntry(ordered, enriched)
            }
        }

        val recentsTaskIds = parseRecentsTaskIds(recentsDump).toMutableSet()
        ordered.keys.forEach { recentsTaskIds += it }
        val recentsPackages = scanPackagesInRecentsDump(recentsDump)
        val activityRecentPackages = if (activitiesDump.isNotEmpty()) {
            parseActivitiesDump(activitiesDump).map { it.packageName }.toSet()
        } else {
            emptySet()
        }

        if (taskListDump.isNotEmpty()) {
            parseCmdTaskList(taskListDump).forEach { entry ->
                if (!shouldIncludeRecentEntry(entry)) return@forEach
                val inRecents = entry.taskId in recentsTaskIds
                val quickShare = RecentPackageResolver.isQuickShareIdentifier(entry.rawIdentifier)
                val packageInRecents = recentsPackages.any { pkg ->
                    RecentPackageResolver.matches(entry.packageName, pkg) ||
                        RecentPackageResolver.matches(entry.rawIdentifier, pkg)
                }
                val inActivityRecents = activityRecentPackages.any { pkg ->
                    RecentPackageResolver.matches(entry.packageName, pkg) ||
                        RecentPackageResolver.matches(entry.rawIdentifier, pkg)
                }
                if (inRecents || quickShare || packageInRecents || inActivityRecents) {
                    putOrMergeEntry(ordered, entry)
                }
            }
        }

        mergeMissingRecentsPackages(ordered, recentsPackages, taskListDump)

        return ordered.values.filter { entry ->
            shouldIncludeRecentEntry(entry) && (isValidTaskId(entry.taskId) || entry.taskId == 0)
        }
    }

    private fun mergeMissingRecentsPackages(
        ordered: LinkedHashMap<Int, ShellTaskEntry>,
        recentsPackages: Set<String>,
        taskListDump: String,
    ) {
        if (recentsPackages.isEmpty()) return
        val cmdEntries = if (taskListDump.isNotEmpty()) parseCmdTaskList(taskListDump) else emptyList()
        for (pkg in recentsPackages) {
            if (ordered.values.any { matchesEntry(it, pkg) }) continue
            val cmdEntry = cmdEntries.firstOrNull { matchesEntry(it, pkg) }
            if (cmdEntry != null && shouldIncludeRecentEntry(cmdEntry)) {
                ordered.putIfAbsent(cmdEntry.taskId, cmdEntry)
                continue
            }
            ordered.putIfAbsent(-pkg.hashCode(), ShellTaskEntry(0, pkg, pkg))
        }
    }

    private fun enrichFromCmd(entry: ShellTaskEntry, cmd: ShellTaskEntry?): ShellTaskEntry {
        if (cmd == null) return entry
        return mergeTaskEntries(entry, cmd)
    }

    private fun putOrMergeEntry(
        ordered: LinkedHashMap<Int, ShellTaskEntry>,
        entry: ShellTaskEntry,
    ) {
        val existing = ordered[entry.taskId]
        ordered[entry.taskId] = if (existing == null) entry else mergeTaskEntries(existing, entry)
    }

    private fun mergeTaskEntries(existing: ShellTaskEntry, incoming: ShellTaskEntry): ShellTaskEntry {
        return ShellTaskEntry(
            taskId = existing.taskId,
            packageName = incoming.packageName.ifBlank { existing.packageName },
            rawIdentifier = preferRawIdentifier(existing.rawIdentifier, incoming.rawIdentifier),
            taskTitle = preferNonBlank(existing.taskTitle, incoming.taskTitle),
        )
    }

    private fun preferNonBlank(current: String?, incoming: String?): String? {
        return current?.takeIf { it.isNotBlank() } ?: incoming?.takeIf { it.isNotBlank() }
    }

    private fun preferRawIdentifier(current: String, incoming: String): String {
        if (incoming.contains('/') && !current.contains('/')) return incoming
        if (incoming.count { it == '.' } > current.count { it == '.' }) return incoming
        if (incoming.length > current.length) return incoming
        return current
    }

    fun matchesIdentifier(entry: ShellTaskEntry, identifier: String): Boolean {
        val raw = identifier.trim()
        if (raw.isEmpty()) return false
        return matchesEntry(entry, raw) || entry.rawIdentifier == raw
    }

    private fun matchesEntry(entry: ShellTaskEntry, packageName: String): Boolean {
        return RecentPackageResolver.matches(entry.rawIdentifier, packageName) ||
            RecentPackageResolver.matches(entry.packageName, packageName)
    }

    fun listRecentTaskEntriesFromCmdTaskList(taskListDump: String): List<ShellTaskEntry> {
        return parseCmdTaskList(taskListDump).filter { entry ->
            shouldIncludeRecentEntry(entry) && isValidTaskId(entry.taskId)
        }
    }

    /** All tasks from cmd output — no recents-dump cross-check (Flyme / OEM fast path). */
    fun listAllCmdTaskEntries(taskListDump: String): List<ShellTaskEntry> {
        return parseCmdTaskList(taskListDump).filter { shouldIncludeRecentEntry(it) && isValidTaskId(it.taskId) }
    }

    fun listRecentTaskEntriesFromRecentsDump(recentsDump: String): List<ShellTaskEntry> {
        return parseRecentsDump(recentsDump).filter { shouldIncludeRecentEntry(it) && isValidTaskId(it.taskId) }
    }

    fun filterRecentEntries(entries: List<ShellTaskEntry>): List<ShellTaskEntry> =
        entries.filter { shouldIncludeRecentEntry(it) && (isValidTaskId(it.taskId) || it.taskId == 0) }

    private fun shouldIncludeRecentEntry(entry: ShellTaskEntry): Boolean {
        if (entry.packageName in EXCLUDED_PACKAGES) return false
        if (shouldExcludeRawIdentifier(entry.rawIdentifier)) return false
        return isLikelyPackage(entry.packageName)
    }

    private fun shouldExcludeRawIdentifier(raw: String): Boolean {
        val lower = raw.lowercase()
        return lower.contains("fallbackhome")
    }

    fun parseAmStackList(output: String): List<ShellTaskEntry> {
        val entries = mutableListOf<ShellTaskEntry>()
        var currentTaskId: Int? = null
        for (line in output.lineSequence()) {
            when {
                line.contains("taskId=") -> {
                    currentTaskId = line.substringAfter("taskId=").substringBefore(' ').toIntOrNull()
                }
                currentTaskId != null && line.contains("component=") -> {
                    parsePackageFromActivityLine(line)?.let { pkg ->
                        addEntry(entries, currentTaskId, pkg)
                    }
                    currentTaskId = null
                }
            }
        }
        return entries
    }

    fun parseActivitiesDump(output: String): List<ShellTaskEntry> {
        val entries = mutableListOf<ShellTaskEntry>()
        var pendingTaskId: Int? = null
        var pendingRawIdentifier: String? = null
        var pendingTitle: String? = null
        var inRecents = false

        fun flushPending() {
            if (!inRecents) {
                pendingTaskId = null
                pendingRawIdentifier = null
                pendingTitle = null
                return
            }
            val id = pendingTaskId ?: return
            addEntry(entries, id, pendingRawIdentifier, pendingTitle)
            pendingTaskId = null
            pendingRawIdentifier = null
            pendingTitle = null
            inRecents = false
        }

        for (line in output.lineSequence()) {
            TASK_RECORD_HEADER.matcher(line).let { matcher ->
                if (matcher.find()) {
                    flushPending()
                    pendingTaskId = matcher.group(1)?.toIntOrNull()
                    pendingRawIdentifier = matcher.group(3)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: matcher.group(2)?.trim()?.takeIf { it.isNotEmpty() }
                    inRecents = line.contains("inRecents=true")
                    pendingTitle = extractTitleFromLine(line)
                    if (pendingRawIdentifier != null && inRecents) {
                        flushPending()
                    }
                    return@let
                }
            }
            if (pendingTaskId != null) {
                when {
                    line.contains("inRecents=true") -> inRecents = true
                    line.contains("inRecents=false") -> inRecents = false
                }
                extractTitleFromLine(line)?.let { pendingTitle = it }
                extractRawIdentifierFromLine(line)?.let { pendingRawIdentifier = it }
                if (pendingRawIdentifier != null && inRecents) {
                    flushPending()
                }
            }
        }
        flushPending()
        return entries
    }

    fun findFrontTask(activitiesDump: String): ShellTaskEntry? {
        for (line in activitiesDump.lineSequence()) {
            if (!line.contains("ResumedActivity") && !line.contains("mResumedActivity") &&
                !line.contains("topResumedActivity")
            ) {
                continue
            }
            val taskId = parseTaskIdFromActivityLine(line) ?: continue
            val packageName = parsePackageFromActivityLine(line) ?: continue
            if (!isValidTaskId(taskId)) continue
            val pkg = packageName.substringBefore('/').trim()
            if (pkg.isEmpty() || pkg in EXCLUDED_PACKAGES) continue
            return ShellTaskEntry(taskId, pkg)
        }
        RESUMED_ACTIVITY.matcher(activitiesDump).let { matcher ->
            if (matcher.find()) {
                val taskId = matcher.group(1)?.toIntOrNull()?.takeIf(::isValidTaskId) ?: return null
                return ShellTaskEntry(taskId, "")
            }
        }
        return null
    }

    fun findPackageForTaskId(taskId: Int, vararg outputs: String): String? {
        outputs.forEach { output ->
            parseCmdTaskList(output).forEach {
                if (it.taskId == taskId && it.packageName.isNotBlank()) return it.packageName
            }
            parseRecentsDump(output).forEach {
                if (it.taskId == taskId && it.packageName.isNotBlank()) return it.packageName
            }
            parseActivitiesDump(output).forEach {
                if (it.taskId == taskId && it.packageName.isNotBlank()) return it.packageName
            }
        }
        return null
    }

    fun findComponentForTaskId(taskId: Int, vararg outputs: String): String? {
        parseTaskTopComponents(*outputs)[taskId]?.let { return it }
        for (output in outputs) {
            var currentTaskId: Int? = null
            for (line in output.lineSequence()) {
                if (line.contains("Task id #") || (line.contains("Task{") && line.contains("#$taskId"))) {
                    TASK_ID_LINE.matcher(line).let { matcher ->
                        if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
                    }
                }
                if (line.contains("taskId=")) {
                    val id = line.substringAfter("taskId=").substringBefore(' ').toIntOrNull()
                    if (id == taskId) currentTaskId = taskId
                }
                if (currentTaskId == taskId || parseTaskIdFromActivityLine(line) == taskId) {
                    parseComponentFromActivityLine(line)?.let { return it }
                }
            }
        }
        val taskListDump = outputs.firstOrNull().orEmpty()
        var currentTaskId: Int? = null
        for (line in taskListDump.lineSequence()) {
            when {
                line.contains("Task id #") || line.contains("Task{") && line.contains("#$taskId") -> {
                    TASK_ID_LINE.matcher(line).let { matcher ->
                        if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
                    }
                    GENERIC_HASH_ID.matcher(line).let { matcher ->
                        if (matcher.find()) currentTaskId = matcher.group(1)?.toIntOrNull()
                    }
                }
                line.contains("taskId=") -> {
                    currentTaskId = line.substringAfter("taskId=").substringBefore(' ').toIntOrNull()
                }
                currentTaskId == taskId && line.contains("realActivity=") -> {
                    return parseComponentFromActivityLine(line)
                }
                currentTaskId == taskId && line.contains("baseIntent=") -> {
                    return parseComponentFromActivityLine(line)
                }
                currentTaskId == taskId && line.contains("topActivity=") -> {
                    return parseComponentFromActivityLine(line)
                }
            }
        }
        return null
    }

    private fun extractPackageFromLine(line: String): String? {
        return packageFromRawIdentifier(extractRawIdentifierFromLine(line))
    }

    private fun extractRawIdentifierFromRecentLine(line: String): String? {
        RECENT_COMPONENT_FIELD.matcher(line).let { matcher ->
            if (matcher.find()) {
                return matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        RECENT_A_FIELD.matcher(line).let { matcher ->
            if (matcher.find()) {
                return matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return extractRawIdentifierFromLine(line)
    }

    private fun extractRawIdentifierFromLine(line: String): String? {
        parseComponentFromActivityLine(line)?.let { return it.trim() }
        CMP_FIELD.matcher(line).let { matcher ->
            if (matcher.find()) {
                val pkg = matcher.group(1)?.trim().orEmpty()
                if (pkg.isNotEmpty() && line.contains('/')) {
                    val cls = line.substringAfter("$pkg/").substringBefore(' ').substringBefore('}')
                        .trim()
                    if (cls.isNotEmpty()) return "$pkg/$cls"
                }
            }
        }
        PACKAGE_NAME_FIELD.matcher(line).let { matcher ->
            if (matcher.find()) {
                return matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun extractPackageFromRecentLine(line: String): String? {
        return packageFromRawIdentifier(extractRawIdentifierFromRecentLine(line))
    }

    private fun packageFromComponent(raw: String?): String? = packageFromRawIdentifier(raw)

    private fun packageFromRawIdentifier(raw: String?): String? {
        val component = raw?.trim().orEmpty()
        if (component.isEmpty()) return null
        val normalized = RecentPackageResolver.normalizeIdentifier(component)
        return normalized.takeIf { isLikelyPackage(it) }
    }

    private fun isLikelyPackage(pkg: String): Boolean =
        pkg.contains('.') && !pkg.startsWith('.')

    private fun addEntry(
        entries: MutableList<ShellTaskEntry>,
        taskId: Int?,
        packageName: String?,
        taskTitle: String? = null,
    ) {
        val id = taskId ?: return
        val raw = packageName?.trim().orEmpty()
        if (raw.isEmpty() || shouldExcludeRawIdentifier(raw)) return
        val normalized = packageFromRawIdentifier(raw) ?: return
        if (!isValidTaskId(id) || normalized in EXCLUDED_PACKAGES) return
        entries += ShellTaskEntry(
            taskId = id,
            packageName = normalized,
            rawIdentifier = raw,
            taskTitle = cleanTaskTitle(taskTitle),
        )
    }

    private fun extractTitleFromLine(line: String): String? {
        listOf(
            TASK_DESC_LABEL,
            TASK_DESC_LABEL_PLAIN,
            TASK_DESC_LABEL_CN,
            TASK_DESC_LABEL_CN_PLAIN,
            TASK_TITLE_QUOTED,
            TASK_TITLE_PLAIN,
        ).forEach { pattern ->
            pattern.matcher(line).let { matcher ->
                if (matcher.find()) {
                    return cleanTaskTitle(matcher.group(1))
                }
            }
        }
        return null
    }

    private fun cleanTaskTitle(raw: String?): String? {
        val title = raw?.trim()?.trim('"').orEmpty()
            .substringBefore(" icon=")
            .substringBefore(" primaryColor=")
            .trim()
        if (title.isEmpty() || title.equals("null", ignoreCase = true)) return null
        return title
    }

    private fun parseTaskIdFromActivityLine(line: String): Int? {
        GENERIC_TASK_ID.matcher(line).let { matcher ->
            if (matcher.find()) return matcher.group(1)?.toIntOrNull()
        }
        GENERIC_HASH_ID.matcher(line).let { matcher ->
            if (matcher.find()) return matcher.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun parsePackageFromActivityLine(line: String): String? {
        return parseComponentFromActivityLine(line)?.substringBefore('/')?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun parseComponentFromActivityLine(line: String): String? {
        val uIndex = line.indexOf(" u0 ")
        if (uIndex >= 0) {
            val segment = line.substring(uIndex + 4)
            val end = segment.indexOfFirst { it == ' ' || it == '}' }
            val component = if (end > 0) segment.substring(0, end) else segment
            if (component.contains('/')) return component.trim()
        }
        val componentIndex = line.indexOf("ComponentInfo{")
        if (componentIndex >= 0) {
            val segment = line.substring(componentIndex + "ComponentInfo{".length)
            val end = segment.indexOf('}')
            val component = if (end > 0) segment.substring(0, end) else segment
            if (component.contains('/')) return component.trim()
        }
        PACKAGE_NAME_FIELD.matcher(line).let { matcher ->
            if (matcher.find()) {
                return matcher.group(1)?.substringBefore('/')?.trim()
            }
        }
        return null
    }

    private fun isValidTaskId(taskId: Int): Boolean = taskId > 0
}
