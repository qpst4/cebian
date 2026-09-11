package com.slideindex.app.message

import android.content.Context
import android.os.Bundle
import com.slideindex.app.notification.NotificationHistoryIntentCapture
import com.slideindex.app.notification.NotificationHistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CNoticeHistoryLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyRepository: NotificationHistoryRepository,
) {
    suspend fun loadOlderMessages(
        data: NotificationData,
        currentMessages: List<NotificationMessage>,
        historyScanOffset: Int,
        pageSize: Int = HISTORY_PAGE_SIZE,
    ): CNoticeHistoryLoadResult = withContext(Dispatchers.IO) {
        val conversationKey = data.conversationSourceKey.ifBlank {
            NotificationData.resolveConversationSourceKey(data.packageName, data.title, Bundle.EMPTY)
        }
        val oldestTimestamp = currentMessages.mapNotNull { it.timestamp }.minOrNull()
            ?: data.postTime.takeIf { it > 0L }
            ?: Long.MAX_VALUE
        val items = historyRepository.queryFullItemsForCNotice(
            packageName = data.packageName,
            postedBeforeMs = oldestTimestamp,
            offset = historyScanOffset,
            limit = pageSize * 4,
        )
        if (items.isEmpty()) {
            return@withContext CNoticeHistoryLoadResult(emptyList(), historyScanOffset, exhausted = true)
        }
        val extracted = ArrayList<NotificationMessage>()
        var scanned = historyScanOffset
        var exhausted = false
        for (item in items) {
            scanned++
            val extras = NotificationHistoryIntentCapture.deserializeBundle(item.extrasBase64)
                ?: Bundle.EMPTY
            val itemKey = NotificationData.resolveConversationSourceKey(
                packageName = item.packageName,
                title = item.title,
                extras = extras,
            )
            if (itemKey != conversationKey) continue
            val messages = NotificationMessagingHistory.extractMessages(context, extras)
            if (messages.isNotEmpty()) {
                extracted.addAll(messages)
            } else if (item.text.isNotBlank()) {
                extracted.add(
                    NotificationMessage(
                        text = item.text,
                        timestamp = item.postedAtMs.takeIf { it > 0L },
                    ),
                )
            }
            if (extracted.size >= pageSize) break
        }
        if (items.size < pageSize * 4) {
            exhausted = true
        }
        val merged = mergeOlderMessages(currentMessages, extracted)
        CNoticeHistoryLoadResult(
            newMessages = merged,
            nextScanOffset = scanned,
            exhausted = exhausted && merged.isEmpty(),
        )
    }

    private fun mergeOlderMessages(
        current: List<NotificationMessage>,
        olderCandidates: List<NotificationMessage>,
    ): List<NotificationMessage> {
        if (olderCandidates.isEmpty()) return emptyList()
        val chronologicalCurrent = NotificationMessagingHistory.messagesOldestFirst(current)
        val oldestKnown = chronologicalCurrent.firstOrNull()?.timestamp ?: Long.MAX_VALUE
        val older = NotificationMessagingHistory.messagesOldestFirst(olderCandidates)
            .filter { message ->
                val timestamp = message.timestamp
                timestamp == null || timestamp < oldestKnown
            }
            .filter { candidate ->
                chronologicalCurrent.none { existing ->
                    existing.text == candidate.text &&
                        existing.sender == candidate.sender &&
                        existing.timestamp == candidate.timestamp
                }
            }
        return older
    }

    companion object {
        const val HISTORY_PAGE_SIZE = 20
    }
}

data class CNoticeHistoryLoadResult(
    val newMessages: List<NotificationMessage>,
    val nextScanOffset: Int,
    val exhausted: Boolean,
)
