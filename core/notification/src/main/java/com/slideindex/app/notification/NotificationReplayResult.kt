package com.slideindex.app.notification

sealed class NotificationReplayResult {
    data object Success : NotificationReplayResult()

    data class Failure(
        val reason: String,
        val offerOpenApp: Boolean = false,
        val packageName: String? = null,
    ) : NotificationReplayResult()

    val isSuccess: Boolean
        get() = this is Success
}
