package com.slideindex.app.launcher

import android.content.Intent
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType

enum class QuickLauncherItemType(val id: Int) {
    APP(0),
    SHORTCUT(1),
    WIDGET(2),
    ACTION(4),
    ;

    companion object {
        fun fromId(id: Int): QuickLauncherItemType =
            entries.firstOrNull { it.id == id } ?: APP
    }
}

data class QuickLauncherItem(
    val type: QuickLauncherItemType,
    val payload: String,
    val label: String = "",
) {
    companion object {
        fun app(packageName: String, label: String = "") =
            QuickLauncherItem(QuickLauncherItemType.APP, packageName, label)

        fun shortcut(componentFlat: String, label: String = "") =
            QuickLauncherItem(QuickLauncherItemType.SHORTCUT, componentFlat, label)

        fun intentShortcut(intentUri: String, label: String = "", hostPackage: String? = null) =
            QuickLauncherItem(
                QuickLauncherItemType.SHORTCUT,
                "${QuickLauncherItemCodec.INTENT_PAYLOAD_PREFIX}${
                    QuickLauncherItemCodec.encodeIntentPayloadBody(intentUri, hostPackage)
                }",
                label,
            )

        fun intentShortcuts(intentUris: List<String>, label: String = "", hostPackage: String? = null) =
            QuickLauncherItem(
                QuickLauncherItemType.SHORTCUT,
                "${QuickLauncherItemCodec.INTENT_LIST_PAYLOAD_PREFIX}${
                    intentUris.joinToString(QuickLauncherItemCodec.INTENT_LIST_SEP) {
                        QuickLauncherItemCodec.encodeIntentPayloadBody(it, hostPackage)
                    }
                }",
                label,
            )

        fun dynamicShortcut(packageName: String, shortcutId: String, label: String = "") =
            QuickLauncherItem(
                QuickLauncherItemType.SHORTCUT,
                "$packageName${QuickLauncherItemCodec.SHORTCUT_PAYLOAD_SEP}$shortcutId",
                label,
            )

        fun action(action: GestureAction, label: String = "") =
            QuickLauncherItem(
                QuickLauncherItemType.ACTION,
                QuickLauncherItemCodec.encodeActionPayload(action),
                label,
            )

        fun widget(appWidgetId: Int, label: String = "") =
            QuickLauncherItem(QuickLauncherItemType.WIDGET, appWidgetId.toString(), label)
    }
}

object QuickLauncherItemCodec {
    private const val SEP = "\u001E"
    private const val LEGACY_FOLDER_TYPE_ID = 3
    const val SHORTCUT_PAYLOAD_SEP = "\u001C"
    const val INTENT_PAYLOAD_PREFIX = "i:"
    const val INTENT_LIST_PAYLOAD_PREFIX = "is:"
    const val INTENT_LIST_SEP = "\u001F"

    fun encode(item: QuickLauncherItem): String =
        listOf(item.type.id, item.payload, item.label).joinToString(SEP)

    fun decode(raw: String): QuickLauncherItem? {
        val firstSep = raw.indexOf(SEP)
        if (firstSep <= 0) return null
        val typeId = raw.substring(0, firstSep).toIntOrNull() ?: return null
        if (typeId == LEGACY_FOLDER_TYPE_ID) return null
        val type = QuickLauncherItemType.fromId(typeId)
        val lastSep = raw.lastIndexOf(SEP)
        if (lastSep <= firstSep) return null
        val payload = raw.substring(firstSep + 1, lastSep)
        val label = raw.substring(lastSep + 1)
        return QuickLauncherItem(type, payload, label)
    }

    fun encodeActionPayload(action: GestureAction): String =
        "${action.type.id}$SHORTCUT_PAYLOAD_SEP${action.payload}"

    fun parseActionPayload(payload: String): GestureAction? {
        val index = payload.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (index < 0) return null
        val typeId = payload.substring(0, index).toIntOrNull() ?: return null
        val actionPayload = payload.substring(index + 1)
        return GestureAction.from(GestureActionType.fromId(typeId), actionPayload)
    }

    fun actionKey(action: GestureAction): String = encodeActionPayload(action)

    fun parseIntentPayload(payload: String): String? {
        if (!payload.startsWith(INTENT_PAYLOAD_PREFIX)) return null
        val body = payload.removePrefix(INTENT_PAYLOAD_PREFIX).takeIf { it.isNotBlank() } ?: return null
        return extractIntentUriFromBody(body)
    }

    fun parseIntentListPayload(payload: String): List<String>? {
        if (!payload.startsWith(INTENT_LIST_PAYLOAD_PREFIX)) return null
        return payload.removePrefix(INTENT_LIST_PAYLOAD_PREFIX)
            .split(INTENT_LIST_SEP)
            .mapNotNull { part -> part.takeIf { it.isNotBlank() }?.let { extractIntentUriFromBody(it) } }
            .takeIf { it.isNotEmpty() }
    }

    fun encodeIntentPayloadBody(intentUri: String, hostPackage: String? = null): String {
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

    private fun parseIntentHostPackage(payload: String): String? {
        if (!payload.startsWith(INTENT_PAYLOAD_PREFIX)) return null
        return parseIntentHostPackageBody(payload.removePrefix(INTENT_PAYLOAD_PREFIX))
    }

    private fun parseIntentHostPackageBody(body: String): String? {
        val sep = body.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (sep <= 0) return null
        val prefix = body.substring(0, sep)
        return prefix.takeIf { it.contains('.') && !it.startsWith("#") }
    }

    fun shortcutItemKey(item: QuickLauncherItem): String? {
        if (item.type != QuickLauncherItemType.SHORTCUT) return null
        parseIntentListPayload(item.payload)?.let { uris ->
            return "intents:${uris.joinToString(INTENT_LIST_SEP)}"
        }
        parseIntentPayload(item.payload)?.let { return "intent:$it" }
        parseShortcutPayload(item.payload)?.let { (pkg, id) ->
            return shortcutKey(pkg, id)
        }
        return item.payload.takeIf { it.isNotBlank() }
    }

    fun shortcutToggleKey(packageName: String, shortcutId: String, intentUris: List<String>? = null): String {
        intentUris?.let { uris ->
            return if (uris.size == 1) "intent:${uris[0]}" else "intents:${uris.joinToString(INTENT_LIST_SEP)}"
        }
        return shortcutKey(packageName, shortcutId)
    }

    fun parseShortcutPayload(payload: String): Pair<String, String>? {
        val index = payload.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (index <= 0 || index >= payload.lastIndex) return null
        val packageName = payload.substring(0, index)
        val shortcutId = payload.substring(index + 1)
        if (packageName.isBlank() || shortcutId.isBlank()) return null
        return packageName to shortcutId
    }

    fun resolveHostPackageName(
        payload: String,
        fallbackPackageForIntentUri: (String) -> String? = { null },
    ): String? {
        if (payload.startsWith(INTENT_PAYLOAD_PREFIX)) {
            parseIntentHostPackage(payload)?.let { return it }
            parseIntentPayload(payload)?.let { uri ->
                packageNameFromIntentUri(uri)?.let { return it }
                fallbackPackageForIntentUri(uri)?.let { return it }
            }
            return null
        }
        if (payload.startsWith(INTENT_LIST_PAYLOAD_PREFIX)) {
            val body = payload.removePrefix(INTENT_LIST_PAYLOAD_PREFIX)
            body.split(INTENT_LIST_SEP).firstOrNull { it.isNotBlank() }?.let { part ->
                parseIntentHostPackageBody(part)?.let { return it }
                extractIntentUriFromBody(part).let { uri ->
                    packageNameFromIntentUri(uri)?.let { return it }
                    fallbackPackageForIntentUri(uri)?.let { return it }
                }
            }
            return null
        }
        parseShortcutPayload(payload)?.first?.let { return it }
        if (payload.startsWith("c:")) {
            val componentFlat = payload.removePrefix("c:").substringBefore('\u001D')
            return packageNameFromComponentFlat(componentFlat)
        }
        if ('/' in payload) {
            return packageNameFromComponentFlat(payload.substringBefore('/'))
        }
        return null
    }

    private fun packageNameFromComponentFlat(componentFlat: String): String? =
        componentFlat.trim().takeIf { it.isNotBlank() }

    private fun packageNameFromIntentUri(uri: String): String? = runCatching {
        val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        intent.`package`?.takeIf { it.isNotBlank() }
            ?: intent.component?.packageName?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: packageNameFromIntentUriText(uri)

    private fun packageNameFromIntentUriText(uri: String): String? {
        Regex("""(?:^|;)package=([^;]+)""").find(uri)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        Regex("""(?:^|;)component=([^;/]+)""").find(uri)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    fun shortcutKey(packageName: String, shortcutId: String): String =
        "$packageName$SHORTCUT_PAYLOAD_SEP$shortcutId"

    private const val LIST_SEP = "\u001F"

    fun encodeAll(items: List<QuickLauncherItem>): Set<String> =
        if (items.isEmpty()) emptySet() else setOf(items.joinToString(LIST_SEP) { encode(it) })

    fun decodeAll(raw: Set<String>): List<QuickLauncherItem> {
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

/** 是否在图标右下角显示快捷方式角标（蜂窝启动器、快速启动器共用）。 */
fun QuickLauncherItem.showsShortcutBadge(): Boolean =
    type == QuickLauncherItemType.SHORTCUT ||
        (type == QuickLauncherItemType.ACTION && payload.startsWith("action:"))
