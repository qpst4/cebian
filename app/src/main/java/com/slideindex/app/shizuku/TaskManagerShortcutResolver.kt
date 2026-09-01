package com.slideindex.app.shizuku

import android.os.Process
import android.util.Log
import com.slideindex.app.util.AbxXmlParser
import com.slideindex.app.util.ShortcutDisplayRules
import com.slideindex.app.util.ShortcutShellParser
import com.slideindex.app.util.ShortcutSystemFileReader

internal class TaskManagerShortcutResolver(
    private val shell: TaskManagerShellExecutor = TaskManagerShellExecutor,
    private val preferRoot: Boolean = false,
) {
    private val dumpsysLoader = TaskManagerShortcutDumpsysLoader(shell)
    private val xmlLoader = TaskManagerShortcutXmlLoader(shell, dumpsysLoader)

    fun getPublishedShortcuts(packageName: String?): Array<String> {
        if (packageName.isNullOrBlank()) return emptyArray()
        val useRoot = preferRoot || shell.probeRootAvailable()
        val merged = linkedMapOf<String, String>()
        dumpsysLoader.collectPublishedShortcuts(packageName).forEach { (id, label) ->
            merged[id] = label
        }
        if (merged.isEmpty()) {
            xmlLoader.collectPackageXmlShortcuts(packageName, useRoot).forEach { (id, label) ->
                merged[id] = label
            }
        }
        return merged.map { (id, label) -> "$id\t$label" }.toTypedArray()
    }

    fun getAllPublishedShortcuts(): Array<String> {
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

        val useRoot = preferRoot || shell.probeRootAvailable()
        Log.i(TAG, "getAllPublishedShortcuts start root=$useRoot uid=${Process.myUid()}")

        val dumpsysDump = dumpsysLoader.loadAllPackagesShortcutDump(useRoot)
        val bulkHasShortcutInfo = dumpsysDump.contains("ShortcutInfo", ignoreCase = true)
        ShortcutShellParser.parseAllPackages(dumpsysDump).forEach { (pkg, entries) ->
            absorbPackage(pkg, entries)
        }

        val launcherPackage = dumpsysLoader.resolveDefaultLauncherPackage()
        if (launcherPackage.isNotBlank()) {
            val labelsByPackage = merged.mapValues { it.value.toMap() }
            val launcherDump = dumpsysLoader.shortcutLauncherDumpOutput(useRoot)
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
            val fullDump = dumpsysLoader.shortcutCommandOutput(
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
                    xmlLoader.shellRead(root, timeout, command)
                }
                if (AbxXmlParser.isBinaryXmlSupported() ||
                    ShortcutSystemFileReader.probeAbx2XmlShell(xmlRunner, useRoot)
                ) {
                    Log.i(TAG, "getAllPublishedShortcuts: full dumpsys empty, system XML (abx2xml/framework)")
                    xmlLoader.absorbSystemShortcutXml(merged, useRoot)
                }
            }
            if (merged.isEmpty()) {
                Log.i(TAG, "getAllPublishedShortcuts: parallel per-package scan (last resort)")
                dumpsysLoader.collectAllShortcutsParallel(useRoot).forEach { (packageName, entries) ->
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
                            val id = info.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
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
            xmlLoader.absorbSystemShortcutXml(merged, useRoot)
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

    companion object {
        private const val TAG = "TaskManagerUserService"
        private const val DUMP_SHORTCUT_FULL_TIMEOUT_MS = 45_000L
    }
}
