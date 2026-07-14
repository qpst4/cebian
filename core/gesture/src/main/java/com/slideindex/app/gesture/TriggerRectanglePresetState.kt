package com.slideindex.app.gesture

data class TriggerRectanglePresetState(
    val activePreset: TriggerDesignPreset? = null,
    val savedDesigns: Map<TriggerDesignPreset, TriggerHandleDesign> = emptyMap(),
) {
    companion object {
        val Empty = TriggerRectanglePresetState()
    }
}

object TriggerRectanglePresetStateCodec {
    private const val ENTRY_SEP = "\u001D"
    private const val KV_SEP = "\u001C"

    fun encode(state: TriggerRectanglePresetState): String {
        if (state == TriggerRectanglePresetState.Empty) return ""
        val saved = state.savedDesigns.entries
            .sortedBy { it.key.ordinal }
            .joinToString(ENTRY_SEP) { (preset, design) ->
                "${preset.ordinal}$KV_SEP${TriggerHandleDesignCodec.encode(design)}"
            }
        val active = state.activePreset?.ordinal?.toString().orEmpty()
        return if (saved.isEmpty()) active else "$active$ENTRY_SEP$saved"
    }

    fun decode(raw: String?): TriggerRectanglePresetState {
        if (raw.isNullOrBlank()) return TriggerRectanglePresetState.Empty
        val parts = raw.split(ENTRY_SEP)
        val active = parts.firstOrNull()?.toIntOrNull()?.let { ordinal ->
            TriggerDesignPreset.entries.getOrNull(ordinal)
        }
        val saved = buildMap {
            parts.drop(1).forEach { entry ->
                val kv = entry.split(KV_SEP, limit = 2)
                if (kv.size != 2) return@forEach
                val preset = TriggerDesignPreset.entries.getOrNull(kv[0].toIntOrNull() ?: return@forEach)
                    ?: return@forEach
                put(preset, TriggerHandleDesignCodec.decode(kv[1]))
            }
        }
        return TriggerRectanglePresetState(activePreset = active, savedDesigns = saved)
    }
}

object TriggerRectanglePresetLogic {
    fun ensureMigrated(handle: TriggerHandle): TriggerHandle {
        if (handle.rectanglePresetState.activePreset != null ||
            handle.design.kind != TriggerDesignKind.CONFIGURABLE_RECTANGLE
        ) {
            return handle
        }
        val detected = TriggerDesignPresets.detectPreset(handle.design) ?: return handle
        return handle.copy(
            rectanglePresetState = TriggerRectanglePresetState(
                activePreset = detected,
                savedDesigns = mapOf(detected to handle.design),
            ),
        )
    }

    fun switchPreset(handle: TriggerHandle, target: TriggerDesignPreset): TriggerHandle {
        val migrated = ensureMigrated(handle)
        val current = migrated.design.coerceInLimits()
        var state = migrated.rectanglePresetState
        val active = state.activePreset ?: TriggerDesignPresets.detectPreset(current)
        var saved = state.savedDesigns
        if (active != null && current.kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE) {
            saved = saved + (active to current)
        }
        val newDesign = (saved[target] ?: TriggerDesignPresets.apply(target)).coerceInLimits()
        return migrated.copy(
            design = newDesign,
            rectanglePresetState = TriggerRectanglePresetState(
                activePreset = target,
                savedDesigns = saved,
            ),
        )
    }

    fun restoreRectangleDesign(handle: TriggerHandle): TriggerHandleDesign {
        val migrated = ensureMigrated(handle)
        val state = migrated.rectanglePresetState
        state.activePreset?.let { active ->
            state.savedDesigns[active]?.let { saved ->
                return saved.coerceInLimits().copy(kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE)
            }
        }
        val current = migrated.design.coerceInLimits()
        if (current.kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE &&
            (current.sizeDp > 0f || current.haloSizeDp > 0f || current.borderSizeDp > 0f)
        ) {
            return current
        }
        state.savedDesigns.values.firstOrNull()?.let { saved ->
            return saved.coerceInLimits().copy(kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE)
        }
        return TriggerDesignPresets.apply(TriggerDesignPreset.BAR)
    }

    private fun snapshotRectangleDesign(handle: TriggerHandle): TriggerHandle {
        val migrated = ensureMigrated(handle)
        val current = migrated.design.coerceInLimits()
        if (current.kind != TriggerDesignKind.CONFIGURABLE_RECTANGLE) return migrated
        val state = migrated.rectanglePresetState
        val active = state.activePreset ?: TriggerDesignPresets.detectPreset(current) ?: return migrated
        return migrated.copy(
            rectanglePresetState = state.copy(
                activePreset = active,
                savedDesigns = state.savedDesigns + (active to current),
            ),
        )
    }

    fun updateDesign(handle: TriggerHandle, design: TriggerHandleDesign): TriggerHandle {
        val coerced = design.coerceInLimits()
        if (coerced == TriggerHandleDesign()) {
            return resetDesign(handle)
        }
        if (coerced.kind != TriggerDesignKind.CONFIGURABLE_RECTANGLE) {
            val withSnapshot = if (handle.design.kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE) {
                snapshotRectangleDesign(handle)
            } else {
                handle
            }
            return withSnapshot.copy(design = coerced)
        }
        val migrated = ensureMigrated(handle.copy(design = coerced))
        var state = migrated.rectanglePresetState
        val active = state.activePreset ?: TriggerDesignPresets.detectPreset(coerced)
        val updatedState = if (active != null) {
            state.copy(
                activePreset = active,
                savedDesigns = state.savedDesigns + (active to coerced),
            )
        } else {
            state
        }
        return migrated.copy(
            design = coerced,
            rectanglePresetState = updatedState,
        )
    }

    fun resetDesign(handle: TriggerHandle): TriggerHandle = handle.copy(
        design = TriggerHandleDesign(),
        rectanglePresetState = TriggerRectanglePresetState.Empty,
    )
}
