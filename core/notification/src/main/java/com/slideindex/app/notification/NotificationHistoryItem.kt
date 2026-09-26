package com.slideindex.app.notification

import org.json.JSONArray
import org.json.JSONObject
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
    val channelId: String? = null,
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
    val channelId: String? = null,
)

/**
 * 通知栏当前通知的跨进程快照。
 *
 * 监听服务（[android.service.notification.NotificationListenerService]）只在 `:overlay` 进程里存在，
 * UI 在主进程，拿不到监听实例。所以由 `:overlay` 把「此刻通知栏里有什么」序列化后广播出来，
 * 主进程据此渲染「实时」列表（跳转信息仍按 key 从本地历史里取，不跨进程传）。
 */
data class ActiveNotificationSnapshot(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val channelId: String? = null,
)

object ActiveNotificationSnapshotCodec {
    fun encode(items: List<ActiveNotificationSnapshot>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("key", item.key)
                    .put("packageName", item.packageName)
                    .put("title", item.title)
                    .put("text", item.text)
                    .put("postedAtMs", item.postedAtMs)
                    .put("channelId", item.channelId),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<ActiveNotificationSnapshot> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val key = obj.optString("key")
                    val packageName = obj.optString("packageName")
                    if (key.isBlank() || packageName.isBlank()) continue
                    add(
                        ActiveNotificationSnapshot(
                            key = key,
                            packageName = packageName,
                            title = obj.optString("title"),
                            text = obj.optString("text"),
                            postedAtMs = obj.optLong("postedAtMs"),
                            channelId = obj.optString("channelId").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
