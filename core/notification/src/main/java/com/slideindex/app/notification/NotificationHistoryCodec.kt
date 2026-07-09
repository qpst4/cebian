package com.slideindex.app.notification

import org.json.JSONArray
import org.json.JSONObject

object NotificationHistoryCodec {
    fun encode(items: List<NotificationHistoryItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("packageName", item.packageName)
                    .put("title", item.title)
                    .put("text", item.text)
                    .put("postedAtMs", item.postedAtMs)
                    .put("intentUri", item.intentUri)
                    .put("intentParcelBase64", item.intentParcelBase64)
                    .put("intentExtrasBase64", item.intentExtrasBase64)
                    .put("pendingIntentBase64", item.pendingIntentBase64)
                    .put("extrasBase64", item.extrasBase64)
                    .put("notificationKey", item.notificationKey)
                    .put("hidden", item.hidden)
                    .put("extractedCode", item.extractedCode)
                    .put("extractionAttempted", item.extractionAttempted),
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<NotificationHistoryItem> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val packageName = obj.optString("packageName")
                    if (packageName.isBlank()) continue
                    add(
                        NotificationHistoryItem(
                            id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            packageName = packageName,
                            title = obj.optString("title"),
                            text = obj.optString("text"),
                            postedAtMs = obj.optLong("postedAtMs"),
                            intentUri = obj.optString("intentUri").takeIf { it.isNotBlank() },
                            intentParcelBase64 = obj.optString("intentParcelBase64").takeIf { it.isNotBlank() },
                            intentExtrasBase64 = obj.optString("intentExtrasBase64").takeIf { it.isNotBlank() },
                            pendingIntentBase64 = obj.optString("pendingIntentBase64").takeIf { it.isNotBlank() },
                            extrasBase64 = obj.optString("extrasBase64").takeIf { it.isNotBlank() },
                            notificationKey = obj.optString("notificationKey").takeIf { it.isNotBlank() },
                            hidden = obj.optBoolean("hidden", false),
                            extractedCode = obj.optString("extractedCode").takeIf { it.isNotBlank() },
                            extractionAttempted = obj.optBoolean("extractionAttempted", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
