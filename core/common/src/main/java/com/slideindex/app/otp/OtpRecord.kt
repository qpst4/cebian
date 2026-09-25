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
    val category: OtpRecordCategory = if (isTest) OtpRecordCategory.TEST else OtpRecordCategory.CODE,
    /** 单卡设备或未知来源为 -1；0 = 卡1，1 = 卡2。 */
    val simSlot: Int = -1,
    val autoFillStatus: OtpRecordFillStatus = OtpRecordFillStatus.NONE,
    val autoFillReason: String? = null,
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
                    .put("isTest", item.isTest)
                    .put("category", item.category.storageKey)
                    .put("simSlot", item.simSlot)
                    .put("autoFillStatus", item.autoFillStatus.storageKey())
                    .put("autoFillReason", item.autoFillReason.orEmpty()),
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
                    val isTest = obj.optBoolean("isTest", false)
                    val category = obj.optString("category")
                        .takeIf { it.isNotBlank() }
                        ?.let(OtpRecordCategory::fromStorageKey)
                        ?: if (isTest) OtpRecordCategory.TEST else OtpRecordCategory.CODE
                    if (packageName.isBlank()) continue
                    // 普通短信记录没有验证码，code 允许为空；其余分类必须有码。
                    if (code.isBlank() && category != OtpRecordCategory.PLAIN_SMS) continue
                    add(
                        OtpRecord(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            code = code,
                            packageName = packageName,
                            title = obj.optString("title"),
                            text = obj.optString("text"),
                            timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                            ruleName = obj.optString("ruleName").takeIf { it.isNotBlank() },
                            isTest = isTest,
                            category = category,
                            simSlot = obj.optInt("simSlot", -1),
                            autoFillStatus = OtpRecordFillStatus.fromStorageKey(
                                obj.optString("autoFillStatus").takeIf { it.isNotBlank() },
                            ),
                            autoFillReason = obj.optString("autoFillReason").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
