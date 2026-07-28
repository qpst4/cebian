package com.slideindex.app.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateState(
    val latestVersion: String = "",
    val notes: String = "",
    val apkUrl: String = "",
    val apkSize: Long = 0L,
    val lastCheckSuccessTime: Long = 0L,
    val lastCheckAttemptTime: Long = 0L,
    val nextRetryTime: Long = 0L,
)

@Serializable
data class UpdatePreferences(
    val state: UpdateState = UpdateState(),
    val ignoredUpdateVersion: String = "",
    val autoCheckUpdate: Boolean = true,
    val notificationPermissionRequested: Boolean = false,
)
