package com.slideindex.app.message

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import com.slideindex.app.util.BundleParcelCompat

data class NotificationMessage(
    val text: String,
    val timestamp: Long? = null,
    val sender: String? = null,
    val senderIcon: Bitmap? = null,
)

object NotificationMessagingHistory {
    fun extractMessages(context: Context, extras: Bundle): List<NotificationMessage> {
        val fromBundles = extractFromMessageBundles(context, extras)
        if (fromBundles.isNotEmpty()) return fromBundles
        return extractFromTextLines(extras)
    }

    fun resolveUnreadCount(
        messages: List<NotificationMessage>,
        badgeCount: Int,
        textLineCount: Int,
        fallback: Int,
        titleUnreadHint: Int = 0,
    ): Int {
        if (messages.size > 1) return messages.size
        if (textLineCount > 1) return textLineCount
        if (badgeCount > 0) return badgeCount
        if (titleUnreadHint > 1) return titleUnreadHint
        return fallback.coerceAtLeast(1)
    }

    fun parseTitleUnreadHint(title: String): Int {
        val normalized = title.trim()
        if (normalized.isBlank()) return 0
        val patterns = listOf(
            Regex("""(\d+)\s*条新消息"""),
            Regex("""(\d+)\s*条未读"""),
            Regex("""\((\d+)\)"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(normalized) ?: continue
            val count = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
            if (count > 0) return count
        }
        return 0
    }

    fun danmakuContentSignature(data: NotificationData): String {
        val latest = data.messages.lastOrNull()
        if (latest != null) {
            return messageContentSignature(latest)
        }
        return buildString {
            append(data.title)
            append('|')
            append(data.content)
        }
    }

    /** 预览横幅按消息体去重，忽略 TG 等刷新时的 postTime / badge 抖动。 */
    fun peekContentSignature(
        latestMessage: NotificationMessage?,
        data: NotificationData,
    ): String {
        if (latestMessage != null) {
            return messageContentSignature(latestMessage)
        }
        return danmakuContentSignature(data)
    }

    private fun messageContentSignature(message: NotificationMessage): String =
        buildString {
            append(message.text)
            append('|')
            append(message.timestamp ?: 0L)
            append('|')
            append(message.sender.orEmpty())
        }

    /**
     * 内部存储为新→旧；转为旧→新，供聊天式详情面板展示。
     */
    fun messagesOldestFirst(messages: List<NotificationMessage>): List<NotificationMessage> =
        messagesChronological(messages)

    /** 会话排序用：优先取消息体自带时间，避免 TG 刷新通知时 postTime 误置顶。 */
    fun latestConversationActivityTime(
        messages: List<NotificationMessage>,
        postTime: Long,
    ): Long {
        val fromMessages = messages.mapNotNull { it.timestamp }.maxOrNull()
        if (fromMessages != null && fromMessages > 0L) return fromMessages
        return postTime
    }

    /**
     * MessagingStyle / textLines 历史按时间正序存放（旧→新），展示前反转为新→旧。
     */
    fun messagesNewestFirst(messages: List<NotificationMessage>): List<NotificationMessage> =
        if (messages.size <= 1) messages else messages.asReversed()

    fun mergeHistory(
        existing: List<NotificationMessage>,
        incoming: List<NotificationMessage>,
        latestContent: String = "",
        latestTimestamp: Long? = null,
        latestSenderIcon: Bitmap? = null,
    ): List<NotificationMessage> =
        messagesNewestFirst(
            mergeHistoryChronological(
                existing = existing,
                incoming = incoming,
                latestContent = latestContent,
                latestTimestamp = latestTimestamp,
                latestSenderIcon = latestSenderIcon,
            ),
        )

    private fun mergeHistoryChronological(
        existing: List<NotificationMessage>,
        incoming: List<NotificationMessage>,
        latestContent: String = "",
        latestTimestamp: Long? = null,
        latestSenderIcon: Bitmap? = null,
    ): List<NotificationMessage> {
        val chronologicalExisting = deduplicateChronological(messagesChronological(existing))
        if (incoming.isNotEmpty()) {
            if (chronologicalExisting.isEmpty()) return deduplicateChronological(incoming)
            if (incoming.size > chronologicalExisting.size) {
                val prefixMatches = chronologicalExisting.indices.all { index ->
                    chronologicalExisting[index].hasSameContentAs(incoming[index])
                }
                if (prefixMatches) return deduplicateChronological(incoming)
            }
            val merged = chronologicalExisting.toMutableList()
            incoming.forEach { message ->
                if (!merged.any { it.hasSameContentAs(message) }) merged.add(message)
            }
            return deduplicateChronological(merged)
        }
        val trimmed = latestContent.trim()
        if (trimmed.isBlank()) return chronologicalExisting
        if (chronologicalExisting.isEmpty()) {
            return listOf(
                NotificationMessage(
                    text = trimmed,
                    timestamp = latestTimestamp,
                    senderIcon = latestSenderIcon,
                ),
            )
        }
        if (chronologicalExisting.any { it.text == trimmed }) return chronologicalExisting
        val previous = chronologicalExisting.lastOrNull()
        return deduplicateChronological(
            chronologicalExisting + NotificationMessage(
                text = trimmed,
                timestamp = latestTimestamp,
                sender = previous?.sender,
                senderIcon = latestSenderIcon,
            ),
        )
    }

    /**
     * QQ 等同条通知连发时，后续消息常省略 sender；仅继承发送者名，不继承 icon（避免群头像污染）。
     */
    internal fun inheritConsecutiveMessagingSenders(
        messages: List<NotificationMessage>,
    ): List<NotificationMessage> {
        if (messages.size <= 1) return messages
        var lastSender: String? = null
        return messages.map { message ->
            val explicitSender = message.sender?.trim()?.takeIf { it.isNotEmpty() }
            val sender = explicitSender ?: lastSender
            val enriched = when {
                sender == message.sender -> message
                else -> message.copy(sender = sender)
            }
            if (explicitSender != null) lastSender = explicitSender
            enriched
        }
    }

    private fun deduplicateChronological(messages: List<NotificationMessage>): List<NotificationMessage> {
        if (messages.size <= 1) return messages
        val result = ArrayList<NotificationMessage>(messages.size)
        for (message in messages) {
            if (result.none { it.hasSameContentAs(message) }) {
                result.add(message)
            }
        }
        return result
    }

    private fun messagesChronological(messages: List<NotificationMessage>): List<NotificationMessage> =
        if (messages.size <= 1) messages else messages.asReversed()

    private fun extractFromMessageBundles(context: Context, extras: Bundle): List<NotificationMessage> {
        val rawMessages = BundleParcelCompat.getParcelableArrayOfBundles(extras, Notification.EXTRA_MESSAGES)
            ?: BundleParcelCompat.getParcelableArrayOfBundles(extras, "android.messages")
            ?: return emptyList()
        val parsed = rawMessages.mapNotNull { message ->
            val text = message.getCharSequence("text")?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val timestamp = if (message.containsKey("time")) message.getLong("time") else null
            val sender = message.getCharSequence("sender")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            NotificationMessage(
                text = text,
                timestamp = timestamp,
                sender = sender,
                senderIcon = NotificationData.extractMessageSenderIcon(context, message),
            )
        }
        return inheritConsecutiveMessagingSenders(parsed)
    }

    private fun extractFromTextLines(extras: Bundle): List<NotificationMessage> {
        val lines = extras.getCharSequenceArray("android.textLines") ?: return emptyList()
        return lines.mapNotNull { line ->
            val text = line?.toString()?.trim().orEmpty()
            if (text.isBlank()) null else NotificationMessage(text = text)
        }
    }
}

private fun NotificationMessage.hasSameContentAs(other: NotificationMessage): Boolean =
    text == other.text && sender == other.sender
