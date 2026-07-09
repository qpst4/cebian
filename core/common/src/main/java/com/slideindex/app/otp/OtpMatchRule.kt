package com.slideindex.app.otp

import java.util.UUID

data class OtpMatchRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val keyword: String,
    val regex: String,
    val packageName: String? = null,
    val isOfficial: Boolean = false,
    val enabled: Boolean = true,
)

object OtpMatchRuleCodec {
    private const val SEP = "\u001E"
    private const val LIST_SEP = "\u001F"

    fun encode(item: OtpMatchRule): String = buildString {
        append(item.id)
        append(SEP)
        append(item.name)
        append(SEP)
        append(item.keyword)
        append(SEP)
        append(item.regex)
        append(SEP)
        append(item.packageName.orEmpty())
        append(SEP)
        append(if (item.enabled) "1" else "0")
    }

    fun decode(raw: String): OtpMatchRule? {
        val parts = raw.split(SEP, limit = 6)
        val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val keyword = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return null
        val regex = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: return null
        val packageName = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
        val enabled = parts.getOrNull(5) != "0"
        return OtpMatchRule(
            id = id,
            name = name,
            keyword = keyword,
            regex = regex,
            packageName = packageName,
            isOfficial = false,
            enabled = enabled,
        )
    }

    fun encodeAll(items: List<OtpMatchRule>): Set<String> =
        if (items.isEmpty()) {
            emptySet()
        } else {
            setOf(items.joinToString(LIST_SEP) { encode(it) })
        }

    fun decodeAll(raw: Set<String>): List<OtpMatchRule> {
        if (raw.isEmpty()) return emptyList()
        val decoded = if (raw.size == 1) {
            val only = raw.first()
            if (LIST_SEP in only) {
                only.split(LIST_SEP).mapNotNull { decode(it) }
            } else {
                listOfNotNull(decode(only))
            }
        } else {
            raw.mapNotNull { decode(it) }
        }
        return decoded
    }
}
