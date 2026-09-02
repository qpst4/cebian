package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItemCodec

enum class ShakeGestureType(val id: Int) {
    LEFT_FLIP(0),
    RIGHT_FLIP(1),
    FORWARD_FLIP(2),
    BACKWARD_FLIP(3),
    LEFT_FLICK(4),
    RIGHT_FLICK(5),
    ;

    companion object {
        fun fromId(id: Int): ShakeGestureType? = entries.firstOrNull { it.id == id }
    }
}

data class ShakeGestureSettings(
    val enabled: Boolean = false,
    val basicActions: Map<ShakeGestureType, GestureAction> = defaultBasicActions(),
    val lockScreenShakeEnabled: Boolean = false,
    val lockScreenActions: Map<ShakeGestureType, GestureAction> = defaultBasicActions(),
    val independentAppShakeEnabled: Boolean = false,
    val perAppActions: Map<String, Map<ShakeGestureType, GestureAction>> = emptyMap(),
    val globalSensitivity: Float = ShakeSensitivityScale.DEFAULT_UI,
    val independentSensitivityEnabled: Boolean = false,
    val perDirectionSensitivity: Map<ShakeGestureType, Float> = emptyMap(),
    val vibrationFeedbackEnabled: Boolean = true,
    val animationFeedbackEnabled: Boolean = false,
    val animationColorArgb: Int = 0xFF424242.toInt(),
    val disableInLandscape: Boolean = false,
    val blacklistedPackages: Set<String> = emptySet(),
) {
    fun actionFor(type: ShakeGestureType): GestureAction =
        basicActions[type] ?: GestureAction.None

    fun sensitivityFor(type: ShakeGestureType): Float =
        if (independentSensitivityEnabled) {
            perDirectionSensitivity[type] ?: globalSensitivity
        } else {
            globalSensitivity
        }

    companion object {
        fun defaultBasicActions(): Map<ShakeGestureType, GestureAction> = mapOf(
            ShakeGestureType.LEFT_FLIP to GestureAction.Back,
            ShakeGestureType.RIGHT_FLIP to GestureAction.Recents,
            ShakeGestureType.FORWARD_FLIP to GestureAction.None,
            ShakeGestureType.BACKWARD_FLIP to GestureAction.None,
            ShakeGestureType.LEFT_FLICK to GestureAction.None,
            ShakeGestureType.RIGHT_FLICK to GestureAction.None,
        )
    }
}

object ShakeGestureCodec {
    private const val SEP = "\u001E"
    private const val APP_SEP = "\u001F"

    fun encodeAction(type: ShakeGestureType, action: GestureAction): String =
        "${type.id}$SEP${QuickLauncherItemCodec.encodeActionPayload(action)}"

    fun decodeAction(raw: String): Pair<ShakeGestureType, GestureAction>? {
        val index = raw.indexOf(SEP)
        if (index <= 0) return null
        val type = ShakeGestureType.fromId(raw.substring(0, index).toIntOrNull() ?: return null)
            ?: return null
        val action = QuickLauncherItemCodec.parseActionPayload(raw.substring(index + 1))
            ?: return null
        return type to action
    }

    fun encodeAllActions(actions: Map<ShakeGestureType, GestureAction>): Set<String> =
        actions.map { (type, action) -> encodeAction(type, action) }.toSet()

    fun decodeAllActions(raw: Set<String>): Map<ShakeGestureType, GestureAction> {
        val decoded = raw.mapNotNull { decodeAction(it) }.toMap()
        return ShakeGestureSettings.defaultBasicActions() + decoded
    }

    fun encodePerAppActions(perApp: Map<String, Map<ShakeGestureType, GestureAction>>): Set<String> =
        perApp.map { (packageName, actions) ->
            "$packageName$APP_SEP${encodeAllActions(actions).joinToString("\u001D")}"
        }.toSet()

    fun decodePerAppActions(raw: Set<String>): Map<String, Map<ShakeGestureType, GestureAction>> =
        raw.mapNotNull { entry ->
            val index = entry.indexOf(APP_SEP)
            if (index <= 0) return@mapNotNull null
            val packageName = entry.substring(0, index)
            val actionsRaw = entry.substring(index + 1)
            if (actionsRaw.isEmpty()) return@mapNotNull packageName to emptyMap()
            val actions = actionsRaw.split("\u001D").mapNotNull { decodeAction(it) }.toMap()
            packageName to actions
        }.toMap()

    fun encodePerDirectionSensitivity(values: Map<ShakeGestureType, Float>): Set<String> =
        values.map { (type, value) -> "${type.id}$SEP$value" }.toSet()

    fun decodePerDirectionSensitivity(raw: Set<String>): Map<ShakeGestureType, Float> =
        raw.mapNotNull { entry ->
            val index = entry.indexOf(SEP)
            if (index <= 0) return@mapNotNull null
            val type = ShakeGestureType.fromId(entry.substring(0, index).toIntOrNull() ?: return@mapNotNull null)
                ?: return@mapNotNull null
            val value = entry.substring(index + 1).toFloatOrNull() ?: return@mapNotNull null
            type to ShakeSensitivityScale.clampUi(value)
        }.toMap()
}
