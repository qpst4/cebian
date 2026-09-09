package com.slideindex.app.shizuku

import android.util.Log
import com.slideindex.app.util.ShortcutDisplayRules
import com.slideindex.app.util.ShortcutShellParser
import com.slideindex.app.util.ShortcutSystemFileReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

internal class TaskManagerShortcutDumpsysLoader(
    private val shell: TaskManagerShellExecutor = TaskManagerShellExecutor
) {
    fun queryCmdPublishedShortcuts(packageName: String, useRoot: Boolean): List<Pair<String, String>> {
        val merged = linkedMapOf<String, String>()
        fun absorb(entries: List<Pair<String, String>>) {
            entries.forEach { (id, label) -> merged.putIfAbsent(id, label) }
        }
        val dump = shortcutCommandOutput(
            useRoot = useRoot,
            "cmd", "shortcut", "get-shortcuts", "--user", "0", "--flags", "4095", packageName
        )
        absorb(ShortcutShellParser.parse(dump, packageName))
        return merged.filter { (id, label) -> ShortcutDisplayRules.isDisplayable(id, label) }
            .map { it.key to it.value }
    }

    fun collectPublishedShortcuts(packageName: String, logResult: Boolean = true): List<Pair<String, String>> {
        val merged = linkedMapOf<String, String>()
        fun absorb(entries: List<Pair<String, String>>) {
            entries.forEach { (id, label) -> merged.putIfAbsent(id, label) }
        }
        val useRoot = shell.probeRootAvailable()
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
        val filtered = merged.filter { (id, label) -> ShortcutDisplayRules.isDisplayable(id, label) }
        if (logResult) {
            Log.i(TAG, "getPublishedShortcuts($packageName, root=$useRoot) -> ${filtered.size}")
        }
        return filtered.map { it.key to it.value }
    }

    fun loadAllPackagesShortcutDump(useRoot: Boolean): String {
        val plain = shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = DUMP_SHORTCUT_FULL_TIMEOUT_MS,
            "dumpsys",
            "shortcut"
        )
        if (plain.contains("ShortcutInfo", ignoreCase = true)) {
            Log.i(TAG, "loadAllPackagesShortcutDump via dumpsys shortcut -> ${plain.length} bytes")
            return plain
        }
        loadBulkDumpsysViaFile(useRoot)?.let { dump ->
            if (dump.contains("ShortcutInfo", ignoreCase = true)) {
                Log.i(
                    TAG,
                    "loadAllPackagesShortcutDump via temp file -> ${dump.length} bytes"
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
            "-n"
        )
        if (full.contains("ShortcutInfo", ignoreCase = true)) {
            Log.i(
                TAG,
                "loadAllPackagesShortcutDump via dumpsys shortcut --user 0 -n -> ${full.length} bytes"
            )
            return full
        }
        val bulk = dumpAllPackagesWithPattern(useRoot)
        if (bulk.contains("ShortcutInfo", ignoreCase = true)) {
            Log.i(
                TAG,
                "loadAllPackagesShortcutDump via dumpsys -p '$DUMP_SHORTCUT_ALL_PACKAGES_REGEX' -> " +
                    "${bulk.length} bytes"
            )
            return bulk
        }
        Log.w(
            TAG,
            "loadAllPackagesShortcutDump: bulk dumpsys returned no ShortcutInfo"
        )
        return ""
    }

    fun shortcutLauncherDumpOutput(useRoot: Boolean): String =
        shortcutCommandOutput(
            useRoot,
            DUMP_SHORTCUT_TIMEOUT_MS,
            "dumpsys",
            "shortcut",
            "--user",
            "0",
            "-n"
        )

    fun collectAllShortcutsParallel(useRoot: Boolean): Map<String, Map<String, String>> {
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
                "shortcuts=${merged.values.sumOf { it.size }}"
        )
        return merged.mapValues { (_, entries) -> entries.toMap() }
    }

    fun shortcutCommandOutput(useRoot: Boolean, vararg cmd: String): String =
        shortcutCommandOutput(useRoot, TaskManagerShellExecutor.SHELL_COMMAND_TIMEOUT_MS, *cmd)

    fun shortcutCommandOutput(useRoot: Boolean, timeoutMs: Long, vararg cmd: String): String {
        if (useRoot) {
            val command = cmd.joinToString(" ") { part ->
                if (part.contains(' ') || part.contains('\'')) {
                    shell.shellQuote(part)
                } else {
                    part
                }
            }
            return shell.runAsRootUser(command, timeoutMs).output
        }
        return shell.shellCommandWithOutput(timeoutMs, *cmd).output
    }

    fun resolveDefaultLauncherPackage(): String {
        val roleDump = shell.shellOutput("cmd", "role", "get-role-holders", "android.app.role.HOME")
        parseLauncherPackage(roleDump)?.let { return it }
        val legacyDump = shell.shellOutput("cmd", "shortcut", "get-default-launcher", "--user", "0")
        return parseLauncherPackage(legacyDump).orEmpty()
    }

    private fun shortcutDumpOutput(packageName: String, useRoot: Boolean): String =
        shortcutCommandOutput(
            useRoot,
            DUMP_SHORTCUT_PER_PACKAGE_TIMEOUT_MS,
            "dumpsys",
            "shortcut",
            "--user",
            "0",
            "-p",
            packageName
        )

    private fun loadBulkDumpsysViaFile(useRoot: Boolean): String? {
        val path = "/data/local/tmp/cebian_shortcut_${android.os.Process.myUid()}.txt"
        val script = "rm -f $path; dumpsys shortcut --user 0 -n > $path 2>/dev/null; cat $path"
        val output = shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = DUMP_SHORTCUT_ALL_PACKAGES_TIMEOUT_MS,
            "sh",
            "-c",
            script
        )
        if (output.isBlank()) return null
        return output
    }

    private fun dumpAllPackagesWithPattern(useRoot: Boolean): String =
        shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = DUMP_SHORTCUT_ALL_PACKAGES_TIMEOUT_MS,
            "dumpsys",
            "shortcut",
            "--user",
            "0",
            "-p",
            DUMP_SHORTCUT_ALL_PACKAGES_REGEX
        )

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
                timeoutMs = TaskManagerShellExecutor.SHELL_COMMAND_TIMEOUT_MS,
                "pm",
                "list",
                "packages",
                "-3"
            )
        )
        if (thirdParty.isNotEmpty()) {
            val systemWithShortcuts = parsePmList(
                shortcutCommandOutput(
                    useRoot = useRoot,
                    timeoutMs = TaskManagerShellExecutor.SHELL_COMMAND_TIMEOUT_MS,
                    "pm",
                    "list",
                    "packages",
                    "-s"
                )
            ).filter { pkg ->
                pkg.startsWith("com.google.") || pkg == "com.android.vending"
            }
            return (thirdParty + systemWithShortcuts).distinct()
        }
        return parsePmList(
            shortcutCommandOutput(
                useRoot = useRoot,
                timeoutMs = TaskManagerShellExecutor.SHELL_COMMAND_TIMEOUT_MS,
                "pm",
                "list",
                "packages"
            )
        )
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

    companion object {
        private const val TAG = "TaskManagerUserService"
        private const val DUMP_SHORTCUT_TIMEOUT_MS = 8_000L
        private const val DUMP_SHORTCUT_ALL_PACKAGES_TIMEOUT_MS = 60_000L
        private const val DUMP_SHORTCUT_PER_PACKAGE_TIMEOUT_MS = 2_000L
        private const val DUMP_SHORTCUT_FULL_TIMEOUT_MS = 45_000L
        private const val PARALLEL_SHORTCUT_SCAN_TIMEOUT_MS = 45_000L
        private const val DUMP_SHORTCUT_ALL_PACKAGES_REGEX = ".*"
    }
}
