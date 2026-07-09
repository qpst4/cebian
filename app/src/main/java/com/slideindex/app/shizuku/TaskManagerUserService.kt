package com.slideindex.app.shizuku

import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import com.slideindex.app.util.AbxXmlParser
import com.slideindex.app.util.ShortcutDisplayRules
import com.slideindex.app.util.ShortcutShellParser
import com.slideindex.app.util.ShortcutSystemFileReader
import com.slideindex.app.util.ShortcutSystemXmlParser
import com.slideindex.app.util.SystemShortcutEntry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

class TaskManagerUserService() : ITaskManagerService.Stub() {

    @Keep
    constructor(context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun removeTaskById(taskIdStr: String?): Boolean {
        val id = taskIdStr?.toIntOrNull()?.takeIf { it > 0 } ?: return false
        if (SystemRecentsAccess.removeTask(id)) {
            Log.i(TAG, "removeTaskById($id) via system API succeeded")
            return true
        }
        val commands = listOf(
            arrayOf("cmd", "activity", "task", "remove", id.toString()),
            arrayOf("cmd", "activity", "task", "remove-task", id.toString()),
            arrayOf("am", "task", "remove", id.toString()),
            arrayOf("am", "stack", "remove", id.toString()),
        )
        for (command in commands) {
            if (shellCommand(*command)) {
                Log.i(TAG, "removeTaskById($id) via ${command.joinToString(" ")} succeeded")
                return true
            }
        }
        Log.w(TAG, "removeTaskById($id) failed")
        return false
    }

    override fun getFrontTaskId(): String {
        return try {
            readFrontTask()?.taskId?.toString().orEmpty().also {
                Log.i(TAG, "getFrontTaskId -> $it")
            }
        } catch (error: Exception) {
            Log.e(TAG, "getFrontTaskId failed", error)
            ""
        }
    }

    override fun getFrontTaskPackage(): String {
        return try {
            readFrontTask()?.packageName?.takeIf { it.isNotBlank() }.orEmpty().also {
                Log.i(TAG, "getFrontTaskPackage -> $it")
            }
        } catch (error: Exception) {
            Log.e(TAG, "getFrontTaskPackage failed", error)
            ""
        }
    }

    override fun getApiVersion(): Int = API_VERSION

    override fun getTaskIdsForPackage(packageName: String?): Array<String> {
        if (packageName.isNullOrBlank()) return emptyArray()
        return SystemRecentsAccess.listTasks()
            .filter { SystemRecentsAccess.matchesPackage(it, packageName) }
            .map { it.taskId }
            .filter { it > 0 }
            .distinct()
            .map { it.toString() }
            .toTypedArray()
    }

    override fun getRecentTaskPackages(): Array<String> {
        return SystemRecentsAccess.listTasks()
            .map { it.packageName }
            .distinct()
            .toTypedArray()
    }

    override fun getRecentTasks(): Array<String> {
        val entries = SystemRecentsAccess.listTasks()
        Log.i(TAG, "getRecentTasks -> ${entries.size}")
        return entries.map { task ->
            val topComponent = task.component.takeIf { it.contains('/') }.orEmpty()
            "${task.taskId}\t${task.component}\t${task.title.orEmpty()}\t$topComponent"
        }.toTypedArray()
    }

    override fun switchToTask(
        taskIdStr: String?,
        identifier: String?,
        topComponentStr: String?,
    ): Boolean {
        val rawId = identifier?.trim().orEmpty()
        val knownTaskId = taskIdStr?.toIntOrNull()?.takeIf { it > 0 }
        val resolvedId = knownTaskId
            ?: if (rawId.isNotEmpty()) SystemRecentsAccess.findTaskId(rawId) else null
        if (resolvedId == null || resolvedId <= 0) {
            Log.w(TAG, "switchToTask unresolved taskIdStr=$taskIdStr identifier=$rawId")
            return false
        }
        return SystemRecentsAccess.switchToTask(resolvedId)
    }

    override fun showVoiceAssistant(): Boolean {
        if (shellCommand("cmd", "voiceinteraction", "show")) {
            Log.i(TAG, "showVoiceAssistant via cmd voiceinteraction show succeeded")
            return true
        }
        Log.w(TAG, "showVoiceAssistant failed")
        return false
    }

    override fun runShellCommand(cmd: Array<out String>?): Boolean {
        if (cmd.isNullOrEmpty()) return false
        return shellCommand(*cmd)
    }

    override fun runShellCommandOutput(cmd: Array<out String>?): String {
        if (cmd.isNullOrEmpty()) return formatShellOutput(-1, "Empty command")
        val result = shellCommandWithOutput(*cmd)
        return formatShellOutput(result.exitCode, result.output)
    }

    override fun runShellCommandLine(command: String?, useRoot: Boolean, forceAdb: Boolean): String {
        val trimmed = command?.trim().orEmpty()
        if (trimmed.isEmpty()) return formatShellOutput(-1, "Empty command")
        val wantRoot = useRoot && !forceAdb
        val result = when {
            wantRoot -> runAsRootUser(trimmed)
            else -> runAsShellUser(trimmed)
        }
        return formatShellOutput(result.exitCode, result.output)
    }

    override fun probeRootAvailable(): Boolean {
        return when {
            Process.myUid() == 0 -> true
            else -> {
                val result = runAsRootUser("id -u")
                result.exitCode == 0 && parseNumericUid(result.output) == 0
            }
        }
    }

    override fun forceStopPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val stopped = shellCommand("am", "force-stop", packageName)
        Log.i(TAG, "forceStopPackage($packageName) -> $stopped")
        return stopped
    }

    override fun getPublishedShortcuts(packageName: String?): Array<String> {
        if (packageName.isNullOrBlank()) return emptyArray()
        return collectPublishedShortcuts(packageName).map { (id, label) -> "$id\t$label" }.toTypedArray()
    }

    override fun getAllPublishedShortcuts(): Array<String> {
        val merged = linkedMapOf<String, LinkedHashMap<String, String>>()
        fun absorbPackage(packageName: String, entries: List<Pair<String, String>>) {
            val bucket = merged.getOrPut(packageName) { linkedMapOf() }
            entries.forEach { (id, label) ->
                if (ShortcutDisplayRules.isDisplayable(id, label)) {
                    bucket.putIfAbsent(id, label)
                }
            }
        }
        fun toRows(): Array<String> =
            merged.flatMap { (pkg, shortcuts) ->
                shortcuts.map { (id, label) -> "$pkg\t$id\t$label" }
            }.toTypedArray()

        val useRoot = probeRootAvailable()
        Log.i(TAG, "getAllPublishedShortcuts start root=$useRoot uid=${Process.myUid()}")

        val dumpsysDump = loadAllPackagesShortcutDump(useRoot)
        val bulkHasShortcutInfo = dumpsysDump.contains("ShortcutInfo", ignoreCase = true)
        ShortcutShellParser.parseAllPackages(dumpsysDump).forEach { (pkg, entries) ->
            absorbPackage(pkg, entries)
        }

        val launcherPackage = resolveDefaultLauncherPackage()
        if (launcherPackage.isNotBlank()) {
            val labelsByPackage = merged.mapValues { it.value.toMap() }
            val launcherDump = shortcutLauncherDumpOutput(useRoot)
            ShortcutShellParser.parseAllLauncherPinned(launcherDump, launcherPackage).forEach { (pkg, ids) ->
                val bucket = merged.getOrPut(pkg) { linkedMapOf() }
                ids.forEach { id ->
                    if (bucket.containsKey(id)) return@forEach
                    val label = labelsByPackage[pkg]?.get(id)
                        ?: ShortcutShellParser.parsePackage(dumpsysDump, pkg)
                            .firstOrNull { it.first == id }
                            ?.second
                        ?: ShortcutShellParser.parsePackage(launcherDump, pkg)
                            .firstOrNull { it.first == id }
                            ?.second
                        ?: id
                    if (ShortcutDisplayRules.isDisplayable(id, label)) {
                        bucket[id] = label
                    }
                }
            }
        }

        if (!bulkHasShortcutInfo) {
            val fullDump = shortcutCommandOutput(
                useRoot = useRoot,
                timeoutMs = DUMP_SHORTCUT_FULL_TIMEOUT_MS,
                "dumpsys",
                "shortcut",
            )
            val fullHasInfo = fullDump.contains("ShortcutInfo", ignoreCase = true)
            Log.i(
                TAG,
                "getAllPublishedShortcuts dumpsys shortcut bytes=${fullDump.length} hasInfo=$fullHasInfo",
            )
            if (fullHasInfo) {
                ShortcutShellParser.parseAllPackages(fullDump).forEach { (pkg, entries) ->
                    absorbPackage(pkg, entries)
                }
            }
            if (merged.isEmpty()) {
                val xmlRunner = { command: String, root: Boolean, timeout: Long ->
                    shortcutShellRead(root, timeout, command)
                }
                if (AbxXmlParser.isBinaryXmlSupported() ||
                    ShortcutSystemFileReader.probeAbx2XmlShell(xmlRunner, useRoot)
                ) {
                    Log.i(TAG, "getAllPublishedShortcuts: full dumpsys empty, system XML (abx2xml/framework)")
                    absorbSystemShortcutXml(merged, useRoot)
                }
            }
            if (merged.isEmpty()) {
                Log.i(TAG, "getAllPublishedShortcuts: parallel per-package scan (last resort)")
                collectAllShortcutsParallel(useRoot).forEach { (packageName, entries) ->
                    absorbPackage(packageName, entries.map { it.key to it.value })
                }
            }
        }

        if (merged.isEmpty() && Process.myUid() == Process.ROOT_UID) {
            val fromSystemApi = ShortcutSystemApiFetcher.getAllShortcutsViaSystemApi(0)
            if (fromSystemApi.isNotEmpty()) {
                fromSystemApi.forEach { (packageName, shortcuts) ->
                    absorbPackage(
                        packageName,
                        shortcuts.mapNotNull { info ->
                            val id = info.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val label = info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                                ?: info.longLabel?.toString()?.takeIf { it.isNotBlank() }
                                ?: id
                            id to label
                        },
                    )
                }
                Log.i(
                    TAG,
                    "getAllPublishedShortcuts via system API -> packages=${merged.size} " +
                        "shortcuts=${merged.values.sumOf { it.size }}",
                )
            }
        }

        if (merged.isEmpty() && AbxXmlParser.canReadShortcutServiceXml(ShortcutSystemFileReader.isAbx2XmlShellAvailable())) {
            absorbSystemShortcutXml(merged, useRoot)
            if (merged.isNotEmpty()) {
                Log.i(
                    TAG,
                    "getAllPublishedShortcuts via system XML (late) -> packages=${merged.size} " +
                        "shortcuts=${merged.values.sumOf { it.size }}",
                )
            }
        }

        Log.i(
            TAG,
            "getAllPublishedShortcuts(root=$useRoot, packages=${merged.size}) -> ${merged.values.sumOf { it.size }}",
        )
        return toRows()
    }

    private fun absorbSystemShortcutXml(
        merged: LinkedHashMap<String, LinkedHashMap<String, String>>,
        useRoot: Boolean,
    ) {
        val shellRunner = { command: String, root: Boolean, timeout: Long ->
            shortcutShellRead(root, timeout, command)
        }
        if (!AbxXmlParser.isBinaryXmlSupported() &&
            !ShortcutSystemFileReader.probeAbx2XmlShell(shellRunner, useRoot)
        ) {
            Log.i(TAG, "absorbSystemShortcutXml: skip (no framework ABX parser and no abx2xml)")
            return
        }
        fun absorbParsed(parsed: Map<String, List<SystemShortcutEntry>>) {
            parsed.forEach { (packageName, entries) ->
                val bucket = merged.getOrPut(packageName) { linkedMapOf() }
                entries.forEach { entry ->
                    if (ShortcutDisplayRules.isDisplayable(entry.id, entry.label)) {
                        bucket.putIfAbsent(entry.id, entry.label)
                    }
                }
            }
        }

        val diskPaths = ShortcutSystemFileReader.listPackageXmlFilesFromDisk()
        if (diskPaths.isNotEmpty()) {
            Log.i(TAG, "absorbSystemShortcutXml packages/ disk files=${diskPaths.size}")
            val documents = readPackageXmlDocumentsParallel(diskPaths, useRoot)
            val before = merged.values.sumOf { it.size }
            absorbParsed(ShortcutSystemXmlParser.parsePackageDocuments(documents))
            val after = merged.values.sumOf { it.size }
            Log.i(TAG, "absorbSystemShortcutXml packages/ disk read=${documents.size} +${after - before} -> $after total")
            if (after > before) return
        }

        val shellPaths = listPackageXmlPathsViaShell(useRoot)
        if (shellPaths.isNotEmpty()) {
            Log.i(TAG, "absorbSystemShortcutXml packages/ shell files=${shellPaths.size} root=$useRoot")
            val documents = readPackageXmlDocumentsParallel(shellPaths, useRoot)
            val before = merged.values.sumOf { it.size }
            absorbParsed(ShortcutSystemXmlParser.parsePackageDocuments(documents))
            val after = merged.values.sumOf { it.size }
            Log.i(TAG, "absorbSystemShortcutXml packages/ shell read=${documents.size} +${after - before} -> $after total")
            if (after > before) return
        }

        for (path in ShortcutSystemXmlParser.shortcutSystemXmlPaths) {
            val bytes = readRootFileBytes(path, useRoot) ?: continue
            val before = merged.values.sumOf { it.size }
            absorbParsed(ShortcutSystemXmlParser.parseShortcutsBytes(bytes))
            val after = merged.values.sumOf { it.size }
            if (after > before) {
                Log.i(TAG, "absorbSystemShortcutXml($path) +${after - before} -> $after total")
            }
        }
    }

    private fun readPackageXmlDocumentsParallel(
        paths: List<String>,
        useRoot: Boolean,
    ): Map<String, ByteArray> {
        if (paths.isEmpty()) return emptyMap()
        val documents = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
        val workers = min(24, paths.size)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(workers)
        val startedAt = System.currentTimeMillis()
        try {
            paths.forEach { path ->
                pool.submit {
                    readRootFileBytes(path, useRoot)?.let { documents[path] = it }
                }
            }
            pool.shutdown()
            if (!pool.awaitTermination(PACKAGE_XML_READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "readPackageXmlDocumentsParallel timed out after ${PACKAGE_XML_READ_TIMEOUT_MS}ms")
                pool.shutdownNow()
            }
        } catch (error: Exception) {
            Log.e(TAG, "readPackageXmlDocumentsParallel failed", error)
            pool.shutdownNow()
        }
        Log.i(
            TAG,
            "readPackageXmlDocumentsParallel paths=${paths.size} read=${documents.size} " +
                "ms=${System.currentTimeMillis() - startedAt}",
        )
        return documents.toMap()
    }

    private fun listPackageXmlPathsViaShell(useRoot: Boolean): List<String> {
        val output = shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = ROOT_FILE_READ_TIMEOUT_MS,
            "sh",
            "-c",
            "ls -1 ${ShortcutSystemXmlParser.SHORTCUT_PACKAGES_DIR}/*.xml 2>/dev/null",
        ).trim()
        if (output.isBlank() || output.contains("No such file", ignoreCase = true)) return emptyList()
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.endsWith(".xml", ignoreCase = true) }
            .distinct()
            .sorted()
            .toList()
    }

    private fun readRootFileBytes(path: String, useRoot: Boolean): ByteArray? =
        ShortcutSystemFileReader.readShortcutXmlBytes(
            path = path,
            runner = { command, root, timeout -> shortcutShellRead(root, timeout, command) },
            useRoot = useRoot,
            timeoutMs = ROOT_FILE_READ_TIMEOUT_MS,
        )

    private fun shortcutShellRead(
        useRoot: Boolean,
        timeoutMs: Long,
        command: String,
    ): ShortcutSystemFileReader.ShellReadResult {
        val result = if (useRoot) {
            runAsRootUser(command, timeoutMs)
        } else {
            shellCommandWithOutput(timeoutMs, "sh", "-c", command)
        }
        return ShortcutSystemFileReader.ShellReadResult(result.exitCode, result.output)
    }

    private fun queryCmdPublishedShortcuts(packageName: String, useRoot: Boolean): List<Pair<String, String>> {
        val merged = linkedMapOf<String, String>()
        fun absorb(entries: List<Pair<String, String>>) {
            entries.forEach { (id, label) -> merged.putIfAbsent(id, label) }
        }
        val dump = shortcutCommandOutput(
            useRoot = useRoot,
            "cmd", "shortcut", "get-shortcuts", "--user", "0", "--flags", "4095", packageName,
        )
        absorb(ShortcutShellParser.parse(dump, packageName))
        return merged.filter { (id, label) -> ShortcutDisplayRules.isDisplayable(id, label) }
            .map { it.key to it.value }
    }

    private fun collectPublishedShortcuts(packageName: String, logResult: Boolean = true): List<Pair<String, String>> {
        val merged = linkedMapOf<String, String>()
        fun absorb(entries: List<Pair<String, String>>) {
            entries.forEach { (id, label) -> merged.putIfAbsent(id, label) }
        }
        val useRoot = probeRootAvailable()
        absorb(ShortcutShellParser.parse(shortcutDumpOutput(packageName, useRoot), packageName))
        if (merged.isEmpty()) {
            absorb(queryCmdPublishedShortcuts(packageName, useRoot))
        }
        if (merged.isEmpty()) {
            val launcherPackage = resolveDefaultLauncherPackage()
            if (launcherPackage.isNotBlank()) {
                val launcherDump = shortcutLauncherDumpOutput(useRoot)
                ShortcutShellParser.parseAllLauncherPinned(launcherDump, launcherPackage)[packageName]
                    ?.forEach { id ->
                        val label = ShortcutShellParser.parsePackage(launcherDump, packageName)
                            .firstOrNull { it.first == id }
                            ?.second
                            ?: id
                        merged.putIfAbsent(id, label)
                    }
            }
        }
        if (merged.isEmpty()) {
            val packageXml = readRootFileBytes(ShortcutSystemXmlParser.packageXmlPath(packageName), useRoot)
            if (packageXml != null) {
                absorb(
                    ShortcutSystemXmlParser.parsePackageDocumentBytes(packageXml, packageName)
                        .map { it.id to it.label },
                )
            }
        }
        val filtered = merged.filter { (id, label) -> ShortcutDisplayRules.isDisplayable(id, label) }
        if (logResult) {
            Log.i(TAG, "getPublishedShortcuts($packageName, root=$useRoot) -> ${filtered.size}")
        }
        return filtered.map { it.key to it.value }
    }

    /** `dumpsys shortcut -p` for one package. */
    private fun shortcutDumpOutput(packageName: String, useRoot: Boolean): String =
        shortcutCommandOutput(
            useRoot,
            DUMP_SHORTCUT_PER_PACKAGE_TIMEOUT_MS,
            "dumpsys",
            "shortcut",
            "--user",
            "0",
            "-p",
            packageName,
        )

    /** Full dump without config noise — used only for launcher pinned sections. */
    private fun shortcutLauncherDumpOutput(useRoot: Boolean): String =
        shortcutCommandOutput(
            useRoot,
            DUMP_SHORTCUT_TIMEOUT_MS,
            "dumpsys",
            "shortcut",
            "--user",
            "0",
            "-n",
        )

    private fun loadAllPackagesShortcutDump(useRoot: Boolean): String {
        val plain = shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = DUMP_SHORTCUT_FULL_TIMEOUT_MS,
            "dumpsys",
            "shortcut",
        )
        if (plain.contains("ShortcutInfo", ignoreCase = true)) {
            Log.i(TAG, "loadAllPackagesShortcutDump via dumpsys shortcut -> ${plain.length} bytes")
            return plain
        }
        loadBulkDumpsysViaFile(useRoot)?.let { dump ->
            if (dump.contains("ShortcutInfo", ignoreCase = true)) {
                Log.i(
                    TAG,
                    "loadAllPackagesShortcutDump via temp file -> ${dump.length} bytes",
                )
                return dump
            }
        }
        val full = shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = DUMP_SHORTCUT_ALL_PACKAGES_TIMEOUT_MS,
            "dumpsys",
            "shortcut",
            "--user",
            "0",
            "-n",
        )
        if (full.contains("ShortcutInfo", ignoreCase = true)) {
            Log.i(
                TAG,
                "loadAllPackagesShortcutDump via dumpsys shortcut --user 0 -n -> ${full.length} bytes",
            )
            return full
        }
        val bulk = dumpAllPackagesWithPattern(useRoot)
        if (bulk.contains("ShortcutInfo", ignoreCase = true)) {
            Log.i(
                TAG,
                "loadAllPackagesShortcutDump via dumpsys -p '$DUMP_SHORTCUT_ALL_PACKAGES_REGEX' -> " +
                    "${bulk.length} bytes",
            )
            return bulk
        }
        Log.w(
            TAG,
            "loadAllPackagesShortcutDump: bulk dumpsys returned no ShortcutInfo",
        )
        return ""
    }

    private fun loadBulkDumpsysViaFile(useRoot: Boolean): String? {
        val path = "/data/local/tmp/ohooho_shortcut_${Process.myUid()}.txt"
        val script = "rm -f $path; dumpsys shortcut --user 0 -n > $path 2>/dev/null; cat $path"
        val output = shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = DUMP_SHORTCUT_ALL_PACKAGES_TIMEOUT_MS,
            "sh",
            "-c",
            script,
        )
        if (output.isBlank()) return null
        return output
    }

    private fun collectAllShortcutsParallel(useRoot: Boolean): Map<String, Map<String, String>> {
        val packages = listInstalledPackageNames(useRoot)
        if (packages.isEmpty()) return emptyMap()
        val merged = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()
        val workers = min(32, packages.size)
        val pool = Executors.newFixedThreadPool(workers)
        Log.i(TAG, "collectAllShortcutsParallel packages=${packages.size} workers=$workers root=$useRoot")
        try {
            packages.forEach { packageName ->
                pool.submit {
                    collectPublishedShortcuts(packageName, logResult = false).forEach { (id, label) ->
                        if (ShortcutDisplayRules.isDisplayable(id, label)) {
                            merged.computeIfAbsent(packageName) { ConcurrentHashMap() }[id] = label
                        }
                    }
                }
            }
            pool.shutdown()
            if (!pool.awaitTermination(PARALLEL_SHORTCUT_SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "collectAllShortcutsParallel timed out after ${PARALLEL_SHORTCUT_SCAN_TIMEOUT_MS}ms")
                pool.shutdownNow()
            }
        } catch (error: Exception) {
            Log.e(TAG, "collectAllShortcutsParallel failed", error)
            pool.shutdownNow()
        }
        Log.i(
            TAG,
            "collectAllShortcutsParallel done packages=${merged.size} " +
                "shortcuts=${merged.values.sumOf { it.size }}",
        )
        return merged.mapValues { (_, entries) -> entries.toMap() }
    }

    /** One `dumpsys shortcut -p <regex>` call — matches every installed package. */
    private fun dumpAllPackagesWithPattern(useRoot: Boolean): String =
        shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = DUMP_SHORTCUT_ALL_PACKAGES_TIMEOUT_MS,
            "dumpsys",
            "shortcut",
            "--user",
            "0",
            "-p",
            DUMP_SHORTCUT_ALL_PACKAGES_REGEX,
        )

    private fun dumpAllPackagesPerPackage(useRoot: Boolean): String {
        val packages = listInstalledPackageNames(useRoot)
        Log.i(TAG, "dumpAllPackagesPerPackage: dumpsys shortcut -p for ${packages.size} packages")
        var packagesWithShortcuts = 0
        val dump = buildString {
            packages.forEach { packageName ->
                val chunk = shortcutDumpOutput(packageName, useRoot)
                if (chunk.contains("ShortcutInfo", ignoreCase = true)) {
                    packagesWithShortcuts++
                    appendLine(chunk)
                }
            }
        }
        Log.i(
            TAG,
            "dumpAllPackagesPerPackage: packagesWithShortcuts=$packagesWithShortcuts/${packages.size}",
        )
        return dump
    }

    private fun listInstalledPackageNames(useRoot: Boolean): List<String> {
        fun parsePmList(output: String): List<String> =
            output.lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()

        val thirdParty = parsePmList(
            shortcutCommandOutput(
                useRoot = useRoot,
                timeoutMs = SHELL_COMMAND_TIMEOUT_MS,
                "pm",
                "list",
                "packages",
                "-3",
            ),
        )
        if (thirdParty.isNotEmpty()) {
            val systemWithShortcuts = parsePmList(
                shortcutCommandOutput(
                    useRoot = useRoot,
                    timeoutMs = SHELL_COMMAND_TIMEOUT_MS,
                    "pm",
                    "list",
                    "packages",
                    "-s",
                ),
            ).filter { pkg ->
                pkg.startsWith("com.google.") || pkg == "com.android.vending"
            }
            return (thirdParty + systemWithShortcuts).distinct()
        }
        return parsePmList(
            shortcutCommandOutput(
                useRoot = useRoot,
                timeoutMs = SHELL_COMMAND_TIMEOUT_MS,
                "pm",
                "list",
                "packages",
            ),
        )
    }

    private fun shortcutCommandOutput(useRoot: Boolean, vararg cmd: String): String =
        shortcutCommandOutput(useRoot, SHELL_COMMAND_TIMEOUT_MS, *cmd)

    private fun shortcutCommandOutput(useRoot: Boolean, timeoutMs: Long, vararg cmd: String): String {
        if (useRoot) {
            val command = cmd.joinToString(" ") { part ->
                if (part.contains(' ') || part.contains('\'')) {
                    shellQuote(part)
                } else {
                    part
                }
            }
            return runAsRootUser(command, timeoutMs).output
        }
        return shellCommandWithOutput(timeoutMs, *cmd).output
    }

    override fun startPublishedShortcut(packageName: String?, shortcutId: String?): Boolean {
        if (packageName.isNullOrBlank() || shortcutId.isNullOrBlank()) return false
        val attempts = listOf(
            arrayOf("cmd", "shortcut", "start-shortcut", "--user", "0", packageName, shortcutId),
            arrayOf("cmd", "shortcut", "start-shortcut", packageName, shortcutId),
        )
        val started = attempts.any { shellCommand(*it) }
        Log.i(TAG, "startPublishedShortcut($packageName, $shortcutId) -> $started")
        return started
    }

    override fun moveTaskToFreeWindow(
        taskIdStr: String?,
        windowingMode: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean {
        return try {
            val taskId = taskIdStr?.toIntOrNull()?.takeIf { it > 0 } ?: return false
            val bounds = Rect(left, top, right, bottom)
            val frontTaskId = readFrontTask()?.taskId
            val isFrontTask = frontTaskId == taskId
            Log.i(
                TAG,
                "moveTaskToFreeWindow taskId=$taskId frontTaskId=$frontTaskId isFront=$isFrontTask mode=$windowingMode bounds=$bounds",
            )
            if (isFrontTask) {
                moveFrontTaskToFreeWindow(taskId, windowingMode, bounds)
            } else {
                moveBackgroundTaskToFreeWindow(taskId, windowingMode, bounds)
            }
        } catch (error: Exception) {
            Log.e(TAG, "moveTaskToFreeWindow failed", error)
            false
        }
    }

    private fun buildPlainShellArgs(command: String): Array<String> =
        arrayOf(resolveShPath(), "-c", command)

    private fun runAsRootUser(
        command: String,
        timeoutMs: Long = SHELL_COMMAND_TIMEOUT_MS,
    ): ShellExecResult {
        if (Process.myUid() == 0) {
            return shellCommandWithOutput(timeoutMs, *buildPlainShellArgs(command))
        }
        val su = resolveSuInvocation()
        val q = shellQuote(command)
        val scripts = listOf(
            "$su -c $q",
            "$su 0 sh -c $q",
        )
        return runFirstSuccessfulShellScript(scripts, timeoutMs)
    }

    private fun runAsShellUser(command: String): ShellExecResult {
        if (Process.myUid() != 0) {
            return shellCommandWithOutput(*buildPlainShellArgs(command))
        }
        val wrapper = findShellDowngradeWrapper()
            ?: return ShellExecResult(
                exitCode = -1,
                output = "无法降级到 adb/shell 身份（Shizuku 当前以 Root 运行）。\n" +
                    "请改用 adb/无线调试方式启动 Shizuku。",
            )
        val result = shellCommandWithOutput(resolveShPath(), "-c", wrapper(command))
        return ShellExecResult(
            exitCode = result.exitCode,
            output = SHELL_DOWNGRADE_HINT + result.output,
        )
    }

    private fun findShellDowngradeWrapper(): ((String) -> String)? {
        val sh = resolveShPath()
        val su = resolveSuInvocation()
        val candidates = listOf(
            { cmd: String -> "toybox setuidgid 2000 $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "toybox setuidgid shell $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "/system/bin/toybox setuidgid 2000 $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "/system/bin/toybox setuidgid shell $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "setuidgid 2000 $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "setuidgid shell $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "$su --user shell -c ${shellQuote(cmd)}" },
            { cmd: String -> "$su shell -c ${shellQuote(cmd)}" },
            { cmd: String -> "$su shell $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "$su -c \"$sh -c ${shellQuote(cmd)}\" shell" },
            { cmd: String -> "$su 2000 $sh -c ${shellQuote(cmd)}" },
            { cmd: String -> "/data/adb/ap/bin/apd su shell -c ${shellQuote(cmd)}" },
            { cmd: String -> "/data/adb/ksud su shell -c ${shellQuote(cmd)}" },
        )
        for (wrap in candidates) {
            val probe = shellCommandWithOutput(sh, "-c", wrap("id -u"))
            val uid = parseNumericUid(probe.output)
            if (probe.exitCode == 0 && uid != null && uid != 0) {
                return wrap
            }
        }
        return null
    }

    private fun runFirstSuccessfulShellScript(
        scripts: List<String>,
        timeoutMs: Long = SHELL_COMMAND_TIMEOUT_MS,
    ): ShellExecResult {
        var last = ShellExecResult(-1, "su 执行失败")
        for (script in scripts) {
            val result = shellCommandWithOutput(timeoutMs, resolveShPath(), "-c", script)
            last = result
            if (result.exitCode == 0) return result
        }
        return last
    }

    private fun parseNumericUid(output: String): Int? {
        val trimmed = output.trim()
        trimmed.toIntOrNull()?.let { return it }
        return Regex("""uid=(\d+)""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun resolveShPath(): String =
        if (java.io.File(SYSTEM_SH).exists()) SYSTEM_SH else "/bin/sh"

    private fun resolveSuInvocation(): String {
        val candidates = listOf(
            "/sbin/su",
            "/system/xbin/su",
            SYSTEM_SU,
            "/vendor/bin/su",
            "/debug_ramdisk/su",
            "/data/adb/magisk/magisk",
        )
        return candidates.firstOrNull { java.io.File(it).exists() } ?: "su"
    }

    private fun shellQuote(command: String): String =
        "'" + command.replace("'", "'\\''") + "'"

    private fun resolveDefaultLauncherPackage(): String {
        val roleDump = shellOutput("cmd", "role", "get-role-holders", "android.app.role.HOME")
        parseLauncherPackage(roleDump)?.let { return it }
        val legacyDump = shellOutput("cmd", "shortcut", "get-default-launcher", "--user", "0")
        return parseLauncherPackage(legacyDump).orEmpty()
    }

    private fun parseLauncherPackage(dump: String): String? {
        return dump.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotBlank() &&
                    !line.equals("Success", ignoreCase = true) &&
                    !line.startsWith("Error:", ignoreCase = true) &&
                    line.contains('.') &&
                    !line.contains(' ')
            }
    }

    private fun readFrontTask(): ShellTaskEntry? =
        SystemRecentsAccess.frontTask()?.let { SystemRecentsAccess.toShellEntry(it) }

    private fun moveFrontTaskToFreeWindow(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        if (moveTaskToFreeWindowViaRecents(taskId, windowingMode, bounds)) return true
        if (moveTaskToFreeWindowViaSystemApi(taskId, windowingMode, bounds)) return true
        if (moveTaskToFreeWindowViaShell(taskId, bounds)) return true
        for (mode in candidateWindowingModes(windowingMode)) {
            if (mode == windowingMode) continue
            if (moveTaskToFreeWindowViaRecents(taskId, mode, bounds)) return true
            if (moveTaskToFreeWindowViaSystemApi(taskId, mode, bounds)) return true
        }
        for (mode in candidateWindowingModes(windowingMode)) {
            if (relaunchViaShell(taskId, mode, bounds)) return true
        }
        return false
    }

    private fun moveBackgroundTaskToFreeWindow(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        for (mode in candidateWindowingModes(windowingMode)) {
            if (relaunchViaShell(taskId, mode, bounds)) return true
        }
        for (mode in candidateWindowingModes(windowingMode)) {
            if (moveTaskToFreeWindowViaSystemApi(taskId, mode, bounds)) return true
        }
        if (focusAndResizeTaskViaShell(taskId, bounds)) return true
        for (mode in candidateWindowingModes(windowingMode)) {
            if (moveTaskToFreeWindowViaRecents(taskId, mode, bounds)) return true
        }
        return moveTaskToFreeWindowViaShell(taskId, bounds)
    }

    private fun candidateWindowingModes(primary: Int): IntArray =
        linkedSetOf(primary, 11, 5, 100, 102).toIntArray()

    private fun moveTaskToFreeWindowViaRecents(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        return try {
            val options = ActivityOptions.makeBasic()
            applyLaunchWindowingMode(options, windowingMode)
            options.setLaunchBounds(bounds)
            val bundle = options.toBundle() ?: Bundle()
            if (bundle.getInt(KEY_WINDOWING_MODE, -1) == -1) {
                bundle.putInt(KEY_WINDOWING_MODE, windowingMode)
            }
            invokeStartActivityFromRecents(taskId, bundle)
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFreeWindowViaRecents failed taskId=$taskId", e)
            false
        }
    }

    private fun invokeStartActivityFromRecents(taskId: Int, bundle: Bundle): Boolean {
        return try {
            val atm = activityTaskManager()
            val result = SystemReflect.invoke(atm, "startActivityFromRecents", taskId, bundle) as? Number
                ?: return false
            val code = result.toInt()
            code in START_SUCCESS..START_DELIVERED_TO_TOP
        } catch (e: Exception) {
            Log.w(TAG, "invokeStartActivityFromRecents failed taskId=$taskId", e)
            false
        }
    }

    private fun applyLaunchWindowingMode(options: ActivityOptions, mode: Int) {
        try {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, mode)
        } catch (_: Exception) {
        }
    }

    private fun moveTaskToFreeWindowViaSystemApi(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        return try {
            val atm = activityTaskManager()
            var windowingApplied = false
            runCatching {
                atm.javaClass.getMethod(
                    "setTaskWindowingMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                ).invoke(atm, taskId, windowingMode, true)
                windowingApplied = true
            }
            runCatching {
                atm.javaClass.getMethod(
                    "resizeTask",
                    Int::class.javaPrimitiveType,
                    Rect::class.java,
                    Int::class.javaPrimitiveType,
                ).invoke(atm, taskId, bounds, RESIZE_MODE_SYSTEM)
            }
            val options = ActivityOptions.makeBasic()
            applyLaunchWindowingMode(options, windowingMode)
            options.setLaunchBounds(bounds)
            val bundle = options.toBundle() ?: Bundle()
            if (bundle.getInt(KEY_WINDOWING_MODE, -1) == -1) {
                bundle.putInt(KEY_WINDOWING_MODE, windowingMode)
            }
            val movedToFront = runCatching {
                atm.javaClass.getMethod(
                    "moveTaskToFront",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Bundle::class.java,
                ).invoke(atm, taskId, 0, bundle) as? Boolean
            }.getOrNull() == true
            windowingApplied || movedToFront
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFreeWindowViaSystemApi failed taskId=$taskId", e)
            false
        }
    }

    private fun focusAndResizeTaskViaShell(taskId: Int, bounds: Rect): Boolean {
        val focused = shellCommand("cmd", "activity", "task", "focus", taskId.toString())
        val resized = moveTaskToFreeWindowViaShell(taskId, bounds)
        return focused && resized
    }

    private fun moveTaskToFreeWindowViaShell(taskId: Int, bounds: Rect): Boolean {
        return shellCommand(
            "cmd", "activity", "task", "resize",
            taskId.toString(),
            bounds.left.toString(),
            bounds.top.toString(),
            bounds.right.toString(),
            bounds.bottom.toString(),
        )
    }

    private fun relaunchViaShell(taskId: Int, windowingMode: Int, bounds: Rect): Boolean {
        val taskListDump = shellOutput("cmd", "activity", "task", "list")
        val activitiesDump = shellOutput("dumpsys", "activity", "activities")
        val component = TaskShellParser.findComponentForTaskId(
            taskId,
            taskListDump,
            activitiesDump,
        ) ?: return false
        if (!shellCommand(
                "am", "start", "-n", component,
                "--windowingMode", windowingMode.toString(),
                "--activity-single-top", "--activity-clear-top",
            )
        ) {
            return false
        }
        return moveTaskToFreeWindowViaShell(taskId, bounds)
    }

    private fun activityTaskManager(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "activity_task") as IBinder
        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
        return stubClass.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("IActivityTaskManager is null")
    }

    private fun shellCommand(vararg cmd: String): Boolean =
        shellCommandWithOutput(*cmd).exitCode == 0

    private fun shellOutput(vararg cmd: String): String =
        shellCommandWithOutput(*cmd).output

    private data class ShellExecResult(val exitCode: Int, val output: String)

    private fun shellCommandWithOutput(vararg cmd: String): ShellExecResult =
        shellCommandWithOutput(SHELL_COMMAND_TIMEOUT_MS, *cmd)

    private fun shellCommandWithOutput(timeoutMs: Long, vararg cmd: String): ShellExecResult {
        return runCatching {
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .directory(java.io.File("/"))
                .apply {
                    environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/product/bin"
                }
                .start()
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ShellExecResult(-1, "Command timed out after ${timeoutMs / 1000}s")
            }
            val text = buildString {
                process.inputStream.bufferedReader().use { reader ->
                    val content = reader.readText()
                    if (content.isNotEmpty()) append(content.trimEnd())
                }
                if (isEmpty()) {
                    append("(no output, exit=${process.exitValue()})")
                }
            }
            process.destroy()
            ShellExecResult(process.exitValue(), text)
        }.getOrElse { error ->
            ShellExecResult(-1, error.message ?: "Execution failed")
        }
    }

    private fun formatShellOutput(exitCode: Int, output: String): String =
        "$exitCode\n---\n$output"

    companion object {
        private const val TAG = "TaskManagerUserService"
        const val API_VERSION = ShizukuUserServiceHost.SERVICE_BUILD
        const val SHELL_DOWNGRADE_HINT = "提示:已将root降权至shell\n"
        private const val SHELL_COMMAND_TIMEOUT_MS = 2_500L
        private const val ROOT_FILE_READ_TIMEOUT_MS = 12_000L
        private const val PACKAGE_SHORTCUT_READ_TIMEOUT_MS = 45_000L
        private const val DUMP_SHORTCUT_TIMEOUT_MS = 8_000L
        private const val DUMP_SHORTCUT_ALL_PACKAGES_TIMEOUT_MS = 60_000L
        private const val DUMP_SHORTCUT_PER_PACKAGE_TIMEOUT_MS = 2_000L
        private const val PACKAGE_XML_READ_TIMEOUT_MS = 90_000L
        private const val DUMP_SHORTCUT_FULL_TIMEOUT_MS = 45_000L
        private const val PARALLEL_SHORTCUT_SCAN_TIMEOUT_MS = 45_000L
        /** Regex passed to `dumpsys shortcut -p` — matches every package name. */
        private const val DUMP_SHORTCUT_ALL_PACKAGES_REGEX = ".*"
        private const val SYSTEM_SH = "/system/bin/sh"
        private const val SYSTEM_SU = "/system/bin/su"
        private const val RESIZE_MODE_SYSTEM = 0
        private const val KEY_WINDOWING_MODE = "android.activity.windowingMode"
        private const val START_SUCCESS = 0
        private const val START_DELIVERED_TO_TOP = 3
    }
}
