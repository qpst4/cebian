package com.slideindex.app.gesture

enum class TriggerDesignKind(val id: Int) {
    HIDE(0),
    CONFIGURABLE_RECTANGLE(1),
    CUSTOM_IMAGE(2),
    ;

    companion object {
        fun fromId(id: Int): TriggerDesignKind =
            entries.firstOrNull { it.id == id } ?: HIDE
    }
}

enum class TriggerCornerMode(val id: Int) {
    ALL(0),
    OUTER(1),
    ;

    companion object {
        fun fromId(id: Int): TriggerCornerMode =
            entries.firstOrNull { it.id == id } ?: ALL
    }
}

enum class TriggerDesignPreset {
    BAR,
    LINE,
    ROUNDED_RECT,
    HALO,
    LINE_AND_HALO,
}

data class TriggerHandleDesign(
    val kind: TriggerDesignKind = TriggerDesignKind.HIDE,
    val sizeDp: Float = 0f,
    val marginDp: Float = 0f,
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    val cornerRadiusDp: Float = 0f,
    val cornerMode: TriggerCornerMode = TriggerCornerMode.ALL,
    val borderSizeDp: Float = 0f,
    val borderColor: Int = DEFAULT_BORDER_COLOR,
    val haloSizeDp: Float = 0f,
    val haloColor: Int = DEFAULT_HALO_COLOR,
    val customImageUri: String? = null,
) {
    val isVisible: Boolean
        get() = kind != TriggerDesignKind.HIDE &&
            (kind == TriggerDesignKind.CUSTOM_IMAGE && !customImageUri.isNullOrBlank() ||
                kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE &&
                (sizeDp > 0f || haloSizeDp > 0f || borderSizeDp > 0f))

    companion object {
        const val DEFAULT_BACKGROUND_COLOR = 0xA6FD746C.toInt()
        const val DEFAULT_BORDER_COLOR = 0xA6FD746C.toInt()
        const val DEFAULT_HALO_COLOR = 0xCCFD746C.toInt()
    }
}

object TriggerDesignPresets {
    fun apply(preset: TriggerDesignPreset): TriggerHandleDesign = when (preset) {
        TriggerDesignPreset.BAR -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 6f,
            marginDp = 0f,
            cornerRadiusDp = 3f,
            cornerMode = TriggerCornerMode.ALL,
            haloSizeDp = 0f,
        )
        TriggerDesignPreset.LINE -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 1f,
            marginDp = 0f,
            cornerRadiusDp = 0f,
            cornerMode = TriggerCornerMode.ALL,
            haloSizeDp = 0f,
        )
        TriggerDesignPreset.ROUNDED_RECT -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 10f,
            marginDp = 0f,
            cornerRadiusDp = 5f,
            cornerMode = TriggerCornerMode.OUTER,
            haloSizeDp = 0f,
        )
        TriggerDesignPreset.HALO -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 0f,
            marginDp = 0f,
            cornerRadiusDp = 0f,
            cornerMode = TriggerCornerMode.ALL,
            haloSizeDp = 10f,
        )
        TriggerDesignPreset.LINE_AND_HALO -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 1f,
            marginDp = 0f,
            cornerRadiusDp = 0f,
            cornerMode = TriggerCornerMode.ALL,
            haloSizeDp = 10f,
        )
    }
}

object TriggerHandleDesignCodec {
    private const val FIELD_SEP = "\u001F"

    fun encode(design: TriggerHandleDesign): String = listOf(
        design.kind.id.toString(),
        design.sizeDp.toString(),
        design.marginDp.toString(),
        design.backgroundColor.toString(),
        design.cornerRadiusDp.toString(),
        design.cornerMode.id.toString(),
        design.borderSizeDp.toString(),
        design.borderColor.toString(),
        design.haloSizeDp.toString(),
        design.haloColor.toString(),
        design.customImageUri.orEmpty(),
    ).joinToString(FIELD_SEP)

    fun decode(raw: String?): TriggerHandleDesign {
        if (raw.isNullOrBlank()) return TriggerHandleDesign()
        val parts = raw.split(FIELD_SEP)
        if (parts.size < 10) return TriggerHandleDesign()
        return TriggerHandleDesign(
            kind = TriggerDesignKind.fromId(parts[0].toIntOrNull() ?: 0),
            sizeDp = parts[1].toFloatOrNull() ?: 0f,
            marginDp = parts[2].toFloatOrNull() ?: 0f,
            backgroundColor = parts[3].toIntOrNull() ?: TriggerHandleDesign.DEFAULT_BACKGROUND_COLOR,
            cornerRadiusDp = parts[4].toFloatOrNull() ?: 0f,
            cornerMode = TriggerCornerMode.fromId(parts[5].toIntOrNull() ?: 0),
            borderSizeDp = parts[6].toFloatOrNull() ?: 0f,
            borderColor = parts[7].toIntOrNull() ?: TriggerHandleDesign.DEFAULT_BORDER_COLOR,
            haloSizeDp = parts[8].toFloatOrNull() ?: 0f,
            haloColor = parts[9].toIntOrNull() ?: TriggerHandleDesign.DEFAULT_HALO_COLOR,
            customImageUri = parts.getOrNull(10)?.takeIf { it.isNotBlank() },
        )
    }
}

fun TriggerHandleDesign.coerceInLimits(): TriggerHandleDesign = copy(
    sizeDp = sizeDp.coerceIn(0f, 48f),
    marginDp = marginDp.coerceIn(0f, 24f),
    cornerRadiusDp = cornerRadiusDp.coerceIn(0f, 32f),
    borderSizeDp = borderSizeDp.coerceIn(0f, 8f),
    haloSizeDp = haloSizeDp.coerceIn(0f, 48f),
)
