package com.slideindex.app.shizuku

import android.util.Log
import com.slideindex.app.util.AbxXmlParser
import com.slideindex.app.util.ShortcutDisplayRules
import com.slideindex.app.util.ShortcutSystemFileReader
import com.slideindex.app.util.ShortcutSystemXmlParser
import com.slideindex.app.util.SystemShortcutEntry
import kotlin.math.min

internal class TaskManagerShortcutXmlLoader(
    private val shell: TaskManagerShellExecutor = TaskManagerShellExecutor,
    private val dumpsysLoader: TaskManagerShortcutDumpsysLoader
) {
    fun absorbSystemShortcutXml(
        merged: LinkedHashMap<String, LinkedHashMap<String, String>>,
        useRoot: Boolean
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

    fun collectPackageXmlShortcuts(
        packageName: String,
        useRoot: Boolean
    ): List<Pair<String, String>> {
        val packageXml = readRootFileBytes(ShortcutSystemXmlParser.packageXmlPath(packageName), useRoot)
            ?: return emptyList()
        return ShortcutSystemXmlParser.parsePackageDocumentBytes(packageXml, packageName)
            .filter { ShortcutDisplayRules.isDisplayable(it.id, it.label) }
            .map { it.id to it.label }
    }

    private fun readPackageXmlDocumentsParallel(
        paths: List<String>,
        useRoot: Boolean
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
                "ms=${System.currentTimeMillis() - startedAt}"
        )
        return documents.toMap()
    }

    private fun listPackageXmlPathsViaShell(useRoot: Boolean): List<String> {
        val output = dumpsysLoader.shortcutCommandOutput(
            useRoot = useRoot,
            timeoutMs = ROOT_FILE_READ_TIMEOUT_MS,
            "sh",
            "-c",
            "ls -1 ${ShortcutSystemXmlParser.SHORTCUT_PACKAGES_DIR}/*.xml 2>/dev/null"
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
            timeoutMs = ROOT_FILE_READ_TIMEOUT_MS
        )

    fun shellRead(
        useRoot: Boolean,
        timeoutMs: Long,
        command: String
    ): ShortcutSystemFileReader.ShellReadResult = shortcutShellRead(useRoot, timeoutMs, command)

    private fun shortcutShellRead(
        useRoot: Boolean,
        timeoutMs: Long,
        command: String
    ): ShortcutSystemFileReader.ShellReadResult {
        val result = if (useRoot) {
            shell.runAsRootUser(command, timeoutMs)
        } else {
            shell.shellCommandWithOutput(timeoutMs, "sh", "-c", command)
        }
        return ShortcutSystemFileReader.ShellReadResult(result.exitCode, result.output)
    }

    companion object {
        private const val TAG = "TaskManagerUserService"
        private const val ROOT_FILE_READ_TIMEOUT_MS = 12_000L
        private const val PACKAGE_XML_READ_TIMEOUT_MS = 90_000L
    }
}
