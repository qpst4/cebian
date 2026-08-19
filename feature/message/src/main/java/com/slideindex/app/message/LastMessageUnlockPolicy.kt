package com.slideindex.app.message

/** “解锁后进入最后一条消息”的决策逻辑（纯函数，便于测试）。 */
internal fun shouldAutoOpenLastMessageOnUnlock(
    settings: MessageSettings,
    pendingUnlockMessage: NotificationData?,
): Boolean = settings.enabled && settings.openLastMessageOnUnlock && pendingUnlockMessage != null
