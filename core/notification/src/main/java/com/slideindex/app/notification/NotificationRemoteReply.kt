package com.slideindex.app.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Sends inline replies through any notification [Notification.Action] that exposes [RemoteInput].
 * Works with native app replies (Telegram, etc.) and decorator-injected actions (e.g. WeKit).
 */
object NotificationRemoteReply {
    private const val TAG = "NotificationRemoteReply"

    fun hasReplyAction(notification: Notification?): Boolean =
        findReplyAction(notification) != null

    fun sendReply(
        context: Context,
        key: String,
        postTime: Long,
        text: String,
    ): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        val sbn = NotificationSbnCache.find(key, postTime) ?: run {
            Log.w(TAG, "SBN not found for key=$key postTime=$postTime")
            return false
        }

        val action = findReplyAction(sbn.notification) ?: run {
            Log.w(TAG, "No reply action on notification key=$key")
            return false
        }

        val remoteInput = action.remoteInputs?.firstOrNull() ?: run {
            Log.w(TAG, "Reply action has no RemoteInput key=$key")
            return false
        }

        return sendThroughAction(context, action, remoteInput, trimmed)
    }

    internal fun findReplyAction(notification: Notification?): Notification.Action? {
        val actions = notification?.actions ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            actions.firstOrNull { action ->
                action.remoteInputs?.isNotEmpty() == true &&
                    action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
            }?.let { return it }
        }
        return actions.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        }
    }

    private fun sendThroughAction(
        context: Context,
        action: Notification.Action,
        remoteInput: RemoteInput,
        text: CharSequence,
    ): Boolean {
        val fillIn = Intent()
        val results = Bundle().apply {
            putCharSequence(remoteInput.resultKey, text)
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIn, results)
        return runCatching {
            action.actionIntent.send(context, 0, fillIn, null, null)
            true
        }.onFailure { error ->
            if (error is PendingIntent.CanceledException) {
                Log.w(TAG, "Reply PendingIntent canceled", error)
            } else {
                Log.e(TAG, "Failed to send notification reply", error)
            }
        }.getOrDefault(false)
    }
}
