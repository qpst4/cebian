package com.slideindex.app.settings

import android.content.Context

private val testSettingsLock = Any()

/**
 * `feature:settings` 内的 `NoOpSettingsBackupCloudConfigPort` 是模块内 internal，`:app` 测试不可见，
 * 因此这里提供等价的测试用空实现。
 */
private object TestSettingsBackupCloudConfigPort : SettingsBackupCloudConfigPort {
    override fun exportRawJson(): String = ""

    override fun importRawJson(raw: String, replaceExisting: Boolean) = Unit
}

suspend fun clearTestSettings(context: Context) {
    val editor = SettingsPreferencesEditor(context)
    editor.edit { prefs ->
        prefs.asMap().keys.toList().forEach { key -> prefs.remove(key) }
    }
}

internal fun testSettingsRepository(context: Context): SettingsRepository = synchronized(testSettingsLock) {
    val editor = SettingsPreferencesEditor(context)
    SettingsRepository(
        context = context,
        editor = editor,
        backupManager = SettingsBackupManager(context, editor, TestSettingsBackupCloudConfigPort),
        edge = EdgeSettingsMutator(editor, context),
        overlay = OverlaySettingsMutator(editor),
        shake = ShakeSettingsMutator(editor),
        message = MessageSettingsMutator(editor),
        otp = OtpSettingsMutator(editor),
    )
}
