package com.slideindex.app.di

import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.settings.SettingsBackupCloudConfigPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VlmOcrSettingsBackupCloudConfigPort @Inject constructor(
    private val vlmOcrConfigManager: VlmOcrConfigManager,
) : SettingsBackupCloudConfigPort {
    override fun exportRawJson(): String = vlmOcrConfigManager.exportRawJson()

    override fun importRawJson(raw: String, replaceExisting: Boolean) {
        vlmOcrConfigManager.importRawJson(raw, replaceExisting)
    }
}
