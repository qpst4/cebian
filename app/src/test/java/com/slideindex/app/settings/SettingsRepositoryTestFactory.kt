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

/**
 * 标记惯量灵敏度已从 V2 迁移到 V3。
 *
 * 没有这两个标记时，[com.slideindex.app.settings.SettingsSnapshotReader] 会把默认值按
 * V2 语义迁移（14.0 → 13.6667），于是「断言读到 AppSettings() 默认值」的用例结果依赖于
 * 同一 JVM 里是否有别的用例先写过 shake 设置——即执行顺序。显式打标记即可消除该顺序依赖。
 *
 * 这里通过公开的写入口来打标记（写灵敏度会一并置位迁移标记），避免依赖模块内 internal 的键名。
 */
suspend fun seedShakeSensitivityMigrationFlags(context: Context) {
    testSettingsRepository(context).setShakeGlobalSensitivity(
        AppSettings().shakeGestureSettings.globalSensitivity,
    )
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
