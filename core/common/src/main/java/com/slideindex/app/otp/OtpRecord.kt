package com.slideindex.app.otp

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OtpRecord(
    val id: String = UUID.randomUUID().toString(),
    val code: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestampMs: Long,
    val ruleName: String? = null,
    val isTest: Boolean = false,
)

object OtpRecordCodec {
    fun encode(items: List<OtpRecord>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("code", item.code)
                    .put("packageName", item.packageName)
                    .put("title", item.title)
                    .put("text", item.text)
                    .put("timestampMs", item.timestampMs)
                    .put("ruleName", item.ruleName)
                    .put("isTest", item.isTest),
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<OtpRecord> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val code = obj.optString("code")
                    val packageName = obj.optString("packageName")
                    if (code.isBlank() || packageName.isBlank()) continue
                    add(
                        OtpRecord(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            code = code,
                            packageName = packageName,
                            title = obj.optString("title"),
                            text = obj.optString("text"),
                            timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                            ruleName = obj.optString("ruleName").takeIf { it.isNotBlank() },
                            isTest = obj.optBoolean("isTest", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
