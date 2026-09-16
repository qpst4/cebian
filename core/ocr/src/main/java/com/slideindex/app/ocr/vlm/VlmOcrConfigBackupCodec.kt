package com.slideindex.app.ocr.vlm

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class VlmOcrConfigBackupDocument(
    val entries: List<VlmOcrConfigBackupEntry>,
)

@Serializable
internal data class VlmOcrConfigBackupEntry(
    val key: String,
    val type: String,
    val value: String,
    val values: List<String> = emptyList(),
)

internal object VlmOcrConfigBackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val TYPE_STRING = "string"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_STRING_SET = "string_set"

    fun encode(prefs: SharedPreferences): String {
        val entries = prefs.all.mapNotNull { (key, value) -> encodeEntry(key, value) }
            .sortedBy { it.key }
        return json.encodeToString(VlmOcrConfigBackupDocument(entries))
    }

    fun decode(raw: String): VlmOcrConfigBackupDocument = json.decodeFromString(raw)

    fun apply(prefs: SharedPreferences, document: VlmOcrConfigBackupDocument, replaceExisting: Boolean) {
        prefs.edit().apply {
            if (replaceExisting) {
                clear()
            }
            document.entries.forEach { entry -> applyEntry(this, entry) }
        }.apply()
    }

    private fun encodeEntry(key: String, value: Any?): VlmOcrConfigBackupEntry? = when (value) {
        is String -> VlmOcrConfigBackupEntry(key = key, type = TYPE_STRING, value = value)
        is Boolean -> VlmOcrConfigBackupEntry(key = key, type = TYPE_BOOLEAN, value = value.toString())
        is Set<*> -> {
            val strings = value.filterIsInstance<String>()
            if (strings.size != value.size) return null
            VlmOcrConfigBackupEntry(
                key = key,
                type = TYPE_STRING_SET,
                value = "",
                values = strings.sorted(),
            )
        }
        else -> null
    }

    private fun applyEntry(editor: SharedPreferences.Editor, entry: VlmOcrConfigBackupEntry) {
        when (entry.type) {
            TYPE_STRING -> editor.putString(entry.key, entry.value)
            TYPE_BOOLEAN -> editor.putBoolean(entry.key, entry.value.toBooleanStrict())
            TYPE_STRING_SET -> editor.putStringSet(entry.key, entry.values.toSet())
        }
    }
}
