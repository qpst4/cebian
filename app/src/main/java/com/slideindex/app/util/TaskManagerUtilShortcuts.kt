package com.slideindex.app.util

import android.os.Looper
import android.util.Log
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.ShizukuUserServiceHost

internal object TaskManagerUtilShortcuts {
    private const val TAG = "TaskManagerUtil"
    private const val ALL_SHORTCUTS_BINDER_TIMEOUT_MS = 180_000L

    fun loadCategorizedSystemShortcutMap(
        hasPermission: Boolean,
        bindFreshService: (Int) -> ITaskManagerService?,
        readServiceApi: (ITaskManagerService) -> Int,
        onProgress: ((ShortcutScanProgress) -> Unit)? = null,
    ): Map<ShortcutKind, Map<String, List<SystemShortcutEntry>>> {
        val merged = ShortcutKind.entries.associateWith { linkedMapOf<String, LinkedHashMap<String, SystemShortcutEntry>>() }
        fun putEntry(kind: ShortcutKind, packageName: String, entry: SystemShortcutEntry) {
            merged.getValue(kind).getOrPut(packageName) { linkedMapOf() }.putIfAbsent(entry.id, entry)
        }
        fun absorbFlat(packageName: String, id: String, label: String) {
            if (!ShortcutDisplayRules.isDisplayable(id, label)) return
            putEntry(
                ShortcutKind.DYNAMIC,
                packageName,
                SystemShortcutEntry(
                    id = id,
                    label = label,
                    kinds = setOf(ShortcutKind.DYNAMIC),
                ),
            )
        }

        onProgress?.invoke(ShortcutScanProgress(ShortcutScanPhase.DUMPSYS, 0, 0))
        if (!hasPermission) {
            Log.w(TAG, "loadCategorizedSystemShortcutMap: no Shizuku permission")
            return finalizeCategorizedShortcutMap(merged)
        }

        val rows = fetchAllPublishedShortcutRows(hasPermission, bindFreshService, readServiceApi)
        Log.i(TAG, "loadCategorizedSystemShortcutMap Shizuku rows=${rows.size}")
        rows.forEach { row ->
            val parts = row.split('\t')
            if (parts.size >= 3) {
                absorbFlat(parts[0].trim(), parts[1].trim(), parts[2].trim())
            }
        }
        return finalizeCategorizedShortcutMap(merged)
    }

    fun loadCategorizedSystemShortcutMapFromRows(
        rows: List<String>,
        onProgress: ((ShortcutScanProgress) -> Unit)? = null,
    ): Map<ShortcutKind, Map<String, List<SystemShortcutEntry>>> {
        val merged = ShortcutKind.entries.associateWith { linkedMapOf<String, LinkedHashMap<String, SystemShortcutEntry>>() }
        fun putEntry(kind: ShortcutKind, packageName: String, entry: SystemShortcutEntry) {
            merged.getValue(kind).getOrPut(packageName) { linkedMapOf() }.putIfAbsent(entry.id, entry)
        }
        fun absorbFlat(packageName: String, id: String, label: String) {
            if (!ShortcutDisplayRules.isDisplayable(id, label)) return
            putEntry(
                ShortcutKind.DYNAMIC,
                packageName,
                SystemShortcutEntry(
                    id = id,
                    label = label,
                    kinds = setOf(ShortcutKind.DYNAMIC),
                ),
            )
        }

        onProgress?.invoke(ShortcutScanProgress(ShortcutScanPhase.DUMPSYS, 0, 0))
        Log.i(TAG, "loadCategorizedSystemShortcutMap root rows=${rows.size}")
        rows.forEach { row ->
            val parts = row.split('\t')
            if (parts.size >= 3) {
                absorbFlat(parts[0].trim(), parts[1].trim(), parts[2].trim())
            }
        }
        return finalizeCategorizedShortcutMap(merged)
    }

    private fun finalizeCategorizedShortcutMap(
        merged: Map<ShortcutKind, LinkedHashMap<String, LinkedHashMap<String, SystemShortcutEntry>>>,
    ): Map<ShortcutKind, Map<String, List<SystemShortcutEntry>>> =
        merged.mapValues { (_, byPackage) ->
            byPackage.mapValues { (_, entries) -> entries.values.toList() }
        }

    private fun fetchAllPublishedShortcutRows(
        hasPermission: Boolean,
        bindFreshService: (Int) -> ITaskManagerService?,
        readServiceApi: (ITaskManagerService) -> Int,
    ): List<String> {
        if (!hasPermission) return emptyList()
        val fetch = fetchAllPublishedShortcutRowsBlocking(bindFreshService, readServiceApi)
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            TaskManagerUtil.runOnTaskWorker { fetch }
        } else {
            fetch
        }
    }

    private fun fetchAllPublishedShortcutRowsBlocking(
        bindFreshService: (Int) -> ITaskManagerService?,
        readServiceApi: (ITaskManagerService) -> Int,
    ): List<String> {
        val service = bindFreshService(0) ?: run {
            Log.w(TAG, "fetchAllPublishedShortcutRows: UserService bind failed")
            return emptyList()
        }
        val api = readServiceApi(service)
        Log.i(TAG, "fetchAllPublishedShortcutRows: bound api=$api")
        val startedAt = System.currentTimeMillis()
        val rows = TaskManagerUtilShell.invokeBinderWithTimeout<Array<String>>(ALL_SHORTCUTS_BINDER_TIMEOUT_MS) {
            service.getAllPublishedShortcuts()
        }
        val elapsedMs = System.currentTimeMillis() - startedAt
        if (rows == null) {
            Log.w(
                TAG,
                "fetchAllPublishedShortcutRows: binder timed out after ${elapsedMs}ms " +
                    "(limit=${ALL_SHORTCUTS_BINDER_TIMEOUT_MS}ms)",
            )
            return emptyList()
        }
        Log.i(TAG, "fetchAllPublishedShortcutRows: received ${rows.size} rows in ${elapsedMs}ms")
        return rows.toList()
    }
}
