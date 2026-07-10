package com.slideindex.app.ui.viewmodel

import android.content.Context
import com.slideindex.app.message.MessageAction
import com.slideindex.app.message.MessageAppFilterRule
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class MessageSettingsViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    fun setMessageReminderEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageReminderEnabled(enabled)
    }

    fun setMessageHideInLandscape(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageHideInLandscape(enabled)
    }

    fun setMessagePortraitDanmaku(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessagePortraitDanmaku(enabled)
    }

    fun setMessageLandscapeDanmaku(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageLandscapeDanmaku(enabled)
    }

    fun setMessageGestureAction(slot: String, action: MessageAction) = launchSettingsWrite {
        settingsRepository.setMessageGestureAction(slot, action)
    }

    fun setMessageSuppressWhenSystemDnd(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageSuppressWhenSystemDnd(enabled)
    }

    fun addMessageEnabledPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.addMessageEnabledPackage(packageName)
    }

    fun removeMessageEnabledPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.removeMessageEnabledPackage(packageName)
    }

    fun upsertMessageAppFilterRule(rule: MessageAppFilterRule) = launchSettingsWrite {
        settingsRepository.upsertMessageAppFilterRule(rule)
    }

    fun addMessageDndPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.addMessageDndPackage(packageName)
    }

    fun removeMessageDndPackage(packageName: String) = launchSettingsWrite {
        settingsRepository.removeMessageDndPackage(packageName)
    }

    fun setMessageStyleId(styleId: String) = launchSettingsWrite {
        settingsRepository.setMessageStyleId(styleId)
    }

    fun setMessageThemeId(themeId: String) = launchSettingsWrite {
        settingsRepository.setMessageThemeId(themeId)
    }

    fun setMessagePrimaryStyleEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessagePrimaryStyleEnabled(enabled)
    }

    fun setMessageDanmakuEnabled(enabled: Boolean) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuEnabled(enabled)
    }

    fun setMessageDanmakuThemeId(themeId: String) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuThemeId(themeId)
    }

    fun setMessageFloatIconOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageFloatIconOpacity(opacity)
    }

    fun setMessageCardOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageCardOpacity(opacity)
    }

    fun setMessageSideBubbleOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageSideBubbleOpacity(opacity)
    }

    fun setMessageDanmakuOpacity(opacity: Float) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuOpacity(opacity)
    }

    fun setMessageCardMaxLines(lines: Int) = launchSettingsWrite {
        settingsRepository.setMessageCardMaxLines(lines)
    }

    fun setMessageDanmakuMaxLines(lines: Int) = launchSettingsWrite {
        settingsRepository.setMessageDanmakuMaxLines(lines)
    }

    fun setMessageSideMaxCount(count: Int) = launchSettingsWrite {
        settingsRepository.setMessageSideMaxCount(count)
    }

    fun setMessageSideMaxWidthDp(width: Float) = launchSettingsWrite {
        settingsRepository.setMessageSideMaxWidthDp(width)
    }

    fun setMessageSideMaxLines(lines: Int) = launchSettingsWrite {
        settingsRepository.setMessageSideMaxLines(lines)
    }

    fun setMessageAutoDismissSeconds(seconds: Int) = launchSettingsWrite {
        settingsRepository.setMessageAutoDismissSeconds(seconds)
    }

    fun setMessageFloatIconSizeDp(sizeDp: Float) = launchSettingsWrite {
        settingsRepository.setMessageFloatIconSizeDp(sizeDp)
    }
}
