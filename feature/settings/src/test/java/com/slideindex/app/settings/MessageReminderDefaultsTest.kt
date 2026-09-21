package com.slideindex.app.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 「启用消息提醒」总开关的语义：只由 `MESSAGE_REMINDER_ENABLED` 决定，
 * 不再被默认开启的提醒样式连带打开（否则全新安装即为开）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MessageReminderDefaultsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun freshInstall_messageReminderDisabledByDefault() {
        val settings = SettingsSnapshotReader.read(emptyPreferences(), context)

        assertFalse(settings.messageReminderSettings.enabled)
    }

    @Test
    fun freshInstall_stylesDoNotTurnMasterSwitchOn() {
        val settings = SettingsSnapshotReader.read(emptyPreferences(), context).messageReminderSettings

        // 侧边气泡/弹幕默认仍是开，但不得把它带成开。
        assertTrue(settings.sideBubbleEnabled || settings.danmakuEnabled)
        assertFalse(settings.enabled)
    }

    @Test
    fun masterKeyControlsMessageReminder() {
        val enabledPrefs = mutablePreferencesOf(
            SettingsPreferenceKeys.MESSAGE_REMINDER_ENABLED to true,
        )
        val disabledPrefs = mutablePreferencesOf(
            SettingsPreferenceKeys.MESSAGE_REMINDER_ENABLED to false,
        )

        assertTrue(SettingsSnapshotReader.read(enabledPrefs, context).messageReminderSettings.enabled)
        assertFalse(SettingsSnapshotReader.read(disabledPrefs, context).messageReminderSettings.enabled)
    }
}
