package com.slideindex.app.notification

import java.util.UUID

data class NotificationHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val intentUri: String?,
    val intentParcelBase64: String? = null,
    val intentExtrasBase64: String? = null,
    val pendingIntentBase64: String? = null,
    val extrasBase64: String? = null,
    val notificationKey: String?,
    val hidden: Boolean = false,
    val extractedCode: String? = null,
    val extractionAttempted: Boolean = false,
)

data class ActiveNotificationEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val historyItem: NotificationHistoryItem?,
)
