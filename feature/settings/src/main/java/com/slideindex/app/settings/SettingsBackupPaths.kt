package com.slideindex.app.settings

internal object SettingsBackupPaths {
    val ALWAYS_BACKUP_DIRS = listOf(
        "search_icons",
        "shell_icons",
        "shortcut_icons",
        "float_ball_assets",
        "stash",
    )

    val SENSITIVE_BACKUP_DIRS = listOf(
        "clipboard",
        "share_image_ocr_history",
    )

    private val LEGACY_DIR_ALIASES = mapOf(
        "search_engine_icons" to "search_icons",
    )

    fun dirsForExport(includeSensitiveDirectories: Boolean): List<String> =
        buildList {
            addAll(ALWAYS_BACKUP_DIRS)
            if (includeSensitiveDirectories) {
                addAll(SENSITIVE_BACKUP_DIRS)
            }
        }

    fun isBackupPath(name: String): Boolean {
        val normalized = normalizeEntryPath(name)
        return (ALWAYS_BACKUP_DIRS + SENSITIVE_BACKUP_DIRS).any { dir ->
            normalized == dir || normalized.startsWith("$dir/")
        }
    }

    fun normalizeEntryPath(name: String): String {
        for ((legacy, current) in LEGACY_DIR_ALIASES) {
            if (name == legacy || name.startsWith("$legacy/")) {
                return name.replaceFirst(legacy, current)
            }
        }
        return name
    }

    fun topLevelDir(name: String): String? {
        val normalized = normalizeEntryPath(name)
        return (ALWAYS_BACKUP_DIRS + SENSITIVE_BACKUP_DIRS).firstOrNull { dir ->
            normalized == dir || normalized.startsWith("$dir/")
        }
    }
}
