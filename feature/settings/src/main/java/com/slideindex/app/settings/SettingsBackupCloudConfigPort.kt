package com.slideindex.app.settings

/**
 * 云端 OCR / 翻译凭据与提示词（独立于 DataStore，普通备份始终包含）。
 */
interface SettingsBackupCloudConfigPort {
    fun exportRawJson(): String

    fun importRawJson(raw: String, replaceExisting: Boolean = true)
}

internal object NoOpSettingsBackupCloudConfigPort : SettingsBackupCloudConfigPort {
    override fun exportRawJson(): String = ""

    override fun importRawJson(raw: String, replaceExisting: Boolean) = Unit
}
