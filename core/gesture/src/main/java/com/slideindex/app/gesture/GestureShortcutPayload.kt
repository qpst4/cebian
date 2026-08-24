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

    fun encodeIntent(intentUri: String, label: String, hostPackage: String? = null): String {
        val body = "$INTENT_PREFIX${encodeIntentBody(intentUri, hostPackage)}"
        return if (label.isBlank()) body else "$body$LABEL_SEP$label"
    }

    fun encodeIntents(intentUris: List<String>, label: String, hostPackage: String? = null): String {
        val body = "$INTENTS_PREFIX${
            intentUris.joinToString(INTENT_LIST_SEP) { encodeIntentBody(it, hostPackage) }
        }"
        return if (label.isBlank()) body else "$body$LABEL_SEP$label"
    }

    /** 与 [com.slideindex.app.launcher.QuickLauncherItemCodec.shortcutItemKey] 的 intent 键一致。 */
    fun shortcutToggleKey(payloadKey: String): String? {
        val decoded = decode(payloadKey) ?: return null
        return when (decoded) {
            is Decoded.Dynamic -> "${decoded.packageName}$SHORTCUT_PAYLOAD_SEP${decoded.shortcutId}"
            is Decoded.Component -> decoded.componentFlat
            is Decoded.IntentShortcut -> "intent:${decoded.intentUri}"
            is Decoded.IntentsShortcut ->
                if (decoded.intentUris.size == 1) {
                    "intent:${decoded.intentUris[0]}"
                } else {
                    "intents:${decoded.intentUris.joinToString(INTENT_LIST_SEP)}"
                }
        }
    }

    fun intentHostPackage(payloadKey: String): String? {
        val labelSep = payloadKey.lastIndexOf(LABEL_SEP)
        val body = if (labelSep >= 0) payloadKey.substring(0, labelSep) else payloadKey
        return when {
            body.startsWith(INTENTS_PREFIX) -> {
                body.removePrefix(INTENTS_PREFIX)
                    .split(INTENT_LIST_SEP)
                    .firstOrNull { it.isNotBlank() }
                    ?.let(::parseIntentHostPackageBody)
            }
            body.startsWith(INTENT_PREFIX) ->
                parseIntentHostPackageBody(body.removePrefix(INTENT_PREFIX))
            else -> null
        }
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
                    .mapNotNull { part ->
                        part.takeIf { it.isNotBlank() }?.let(::extractIntentUriFromBody)
                    }
                if (intentUris.isEmpty()) return null
                return Decoded.IntentsShortcut(intentUris, label)
            }
            body.startsWith(INTENT_PREFIX) -> {
                val intentBody = body.removePrefix(INTENT_PREFIX)
                if (intentBody.isBlank()) return null
                return Decoded.IntentShortcut(extractIntentUriFromBody(intentBody), label)
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

    private fun encodeIntentBody(intentUri: String, hostPackage: String?): String {
        if (hostPackage.isNullOrBlank()) return intentUri
        return "$hostPackage$SHORTCUT_PAYLOAD_SEP$intentUri"
    }

    private fun extractIntentUriFromBody(body: String): String {
        val sep = body.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (sep <= 0) return body
        val prefix = body.substring(0, sep)
        if (prefix.contains('.') && !prefix.startsWith("#")) {
            return body.substring(sep + 1)
        }
        return body
    }

    private fun parseIntentHostPackageBody(body: String): String? {
        val sep = body.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (sep <= 0) return null
        val prefix = body.substring(0, sep)
        return prefix.takeIf { it.contains('.') && !it.startsWith("#") }
    }
}
