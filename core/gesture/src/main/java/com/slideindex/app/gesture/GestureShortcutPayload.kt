package com.slideindex.app.gesture

object GestureShortcutPayload {
    private const val COMPONENT_PREFIX = "c:"
    private const val INTENT_PREFIX = "i:"
    private const val INTENTS_PREFIX = "is:"
    private const val INTENT_LIST_SEP = "\u001F"
    private const val LABEL_SEP = "\u001D"
    private const val SHORTCUT_PAYLOAD_SEP = "\u001C"

    sealed class Decoded {
        abstract val label: String

        data class Dynamic(
            val packageName: String,
            val shortcutId: String,
            override val label: String,
        ) : Decoded()

        data class Component(
            val componentFlat: String,
            override val label: String,
        ) : Decoded()

        data class IntentShortcut(
            val intentUri: String,
            override val label: String,
        ) : Decoded()

        data class IntentsShortcut(
            val intentUris: List<String>,
            override val label: String,
        ) : Decoded()
    }

    fun encodeDynamic(packageName: String, shortcutId: String, label: String): String {
        val body = "$packageName$SHORTCUT_PAYLOAD_SEP$shortcutId"
        return if (label.isBlank()) body else "$body$LABEL_SEP$label"
    }

    fun encodeComponent(componentFlat: String, label: String): String {
        val body = "$COMPONENT_PREFIX$componentFlat"
        return if (label.isBlank()) body else "$body$LABEL_SEP$label"
    }

    fun encodeIntent(intentUri: String, label: String): String {
        val body = "$INTENT_PREFIX$intentUri"
        return if (label.isBlank()) body else "$body$LABEL_SEP$label"
    }

    fun encodeIntents(intentUris: List<String>, label: String): String {
        val body = "$INTENTS_PREFIX${intentUris.joinToString(INTENT_LIST_SEP)}"
        return if (label.isBlank()) body else "$body$LABEL_SEP$label"
    }

    fun decode(payload: String): Decoded? {
        if (payload.isBlank()) return null
        val labelSep = payload.lastIndexOf(LABEL_SEP)
        val (body, label) = if (labelSep >= 0) {
            payload.substring(0, labelSep) to payload.substring(labelSep + 1)
        } else {
            payload to ""
        }
        when {
            body.startsWith(INTENTS_PREFIX) -> {
                val intentUris = body.removePrefix(INTENTS_PREFIX)
                    .split(INTENT_LIST_SEP)
                    .filter { it.isNotBlank() }
                if (intentUris.isEmpty()) return null
                return Decoded.IntentsShortcut(intentUris, label)
            }
            body.startsWith(INTENT_PREFIX) -> {
                val intentUri = body.removePrefix(INTENT_PREFIX)
                if (intentUri.isBlank()) return null
                return Decoded.IntentShortcut(intentUri, label)
            }
            body.startsWith(COMPONENT_PREFIX) -> {
                val componentFlat = body.removePrefix(COMPONENT_PREFIX)
                if (componentFlat.isBlank()) return null
                return Decoded.Component(componentFlat, label)
            }
        }
        val dynamic = parseShortcutPayload(body) ?: return null
        return Decoded.Dynamic(dynamic.first, dynamic.second, label)
    }

    private fun parseShortcutPayload(payload: String): Pair<String, String>? {
        val index = payload.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (index <= 0 || index >= payload.lastIndex) return null
        val packageName = payload.substring(0, index)
        val shortcutId = payload.substring(index + 1)
        if (packageName.isBlank() || shortcutId.isBlank()) return null
        return packageName to shortcutId
    }
}
