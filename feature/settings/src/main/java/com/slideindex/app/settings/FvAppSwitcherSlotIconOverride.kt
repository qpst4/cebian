package com.slideindex.app.settings

data class FvAppSwitcherSlotIconOverride(
    val iconPath: String? = null,
    val textIcon: String? = null,
) {
    fun isConfigured(): Boolean = !iconPath.isNullOrBlank() || !textIcon.isNullOrBlank()
}

object FvAppSwitcherSlotIconOverrideCodec {
    private const val INDEX_SEP = "\u001D"
    private const val FIELD_SEP = "\u001E"

    fun encode(index: Int, override: FvAppSwitcherSlotIconOverride): String? {
        if (!override.isConfigured()) return null
        val iconPath = override.iconPath.orEmpty()
        val textIcon = override.textIcon.orEmpty()
        return "$index$INDEX_SEP$iconPath$FIELD_SEP$textIcon"
    }

    fun decode(raw: String): Pair<Int, FvAppSwitcherSlotIconOverride>? {
        val indexSep = raw.indexOf(INDEX_SEP)
        if (indexSep <= 0) return null
        val index = raw.substring(0, indexSep).toIntOrNull() ?: return null
        val body = raw.substring(indexSep + 1)
        val fieldSep = body.lastIndexOf(FIELD_SEP)
        if (fieldSep < 0) return null
        val iconPath = body.substring(0, fieldSep).takeIf { it.isNotBlank() }
        val textIcon = body.substring(fieldSep + 1).takeIf { it.isNotBlank() }
        val override = FvAppSwitcherSlotIconOverride(iconPath = iconPath, textIcon = textIcon)
        return if (override.isConfigured()) index to override else null
    }

    fun encodeAll(overrides: Map<Int, FvAppSwitcherSlotIconOverride>): Set<String> =
        overrides.mapNotNull { (index, override) -> encode(index, override) }.toSet()

    fun decodeAll(raw: Set<String>): Map<Int, FvAppSwitcherSlotIconOverride> =
        raw.mapNotNull { decode(it) }.toMap()
}
