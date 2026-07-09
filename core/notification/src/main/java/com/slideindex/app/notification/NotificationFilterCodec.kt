package com.slideindex.app.notification

import org.json.JSONArray
import org.json.JSONObject

object NotificationFilterCodec {
    private const val SCHEMA_VERSION = 2

    fun encode(rules: List<NotificationFilterRule>): String {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
        val array = JSONArray()
        rules.map { it.normalized() }.forEach { rule ->
            array.put(encodeRule(rule))
        }
        root.put("rules", array)
        return root.toString()
    }

    fun decode(raw: String): List<NotificationFilterRule> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                decodeLegacyArray(JSONArray(trimmed))
            } else {
                val root = JSONObject(trimmed)
                when (root.optInt("schemaVersion", 1)) {
                    SCHEMA_VERSION -> decodeV2(root)
                    else -> decodeLegacyArray(root.optJSONArray("rules") ?: JSONArray(trimmed))
                }
            }
        }.getOrDefault(emptyList()).map { it.normalized() }
    }

    private fun decodeV2(root: JSONObject): List<NotificationFilterRule> {
        val array = root.optJSONArray("rules") ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(decodeRuleV2(obj))
            }
        }
    }

    private fun decodeLegacyArray(array: JSONArray): List<NotificationFilterRule> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                add(decodeLegacyRule(obj))
            }
        }
    }

    private fun encodeRule(rule: NotificationFilterRule): JSONObject {
        val actions = JSONArray()
        rule.actionEntries.forEach { action ->
            actions.put(encodeAction(action))
        }
        val apps = JSONArray()
        rule.appTargets.forEach { target ->
            apps.put(
                JSONObject()
                    .put("packageName", target.packageName)
                    .put("user", target.userId),
            )
        }
        return JSONObject()
            .put("id", rule.id)
            .put("name", rule.name)
            .put("enabled", rule.enabled)
            .put("userCreated", rule.userCreated)
            .put("createdAtMs", rule.createdAtMs)
            .put("channelId", rule.channelId)
            .put("appMode", rule.appMode.name)
            .put("appTargets", apps)
            .put("textMode", rule.textMode.name)
            .put("keywords", JSONArray(rule.keywords))
            .put("keywordsExclude", JSONArray(rule.keywordsExclude))
            .put("regex", rule.regex)
            .put("advancedFilterJson", rule.advancedFilterJson)
            .put("ignoreCase", rule.ignoreCase)
            .put("timeStartMs", rule.timeStartMs)
            .put("timeEndMs", rule.timeEndMs)
            .put("weekDays", JSONArray(rule.weekDays.sorted()))
            .put("screenMode", rule.screenMode.name)
            .put("chargeMask", rule.chargeMask)
            .put("actionEntries", actions)
    }

    private fun encodeAction(action: RuleActionEntry): JSONObject {
        return JSONObject()
            .put("type", action.type.name)
            .put("delayTimeMs", action.delayTimeMs)
            .put("includeOngoing", action.includeOngoing)
            .put("laterTimesMs", JSONArray(action.laterTimesMs))
            .put("soundUri", action.soundUri)
            .put("replaceTitle", action.replaceTitle)
            .put("replaceMessage", action.replaceMessage)
            .put("buttonNames", JSONArray(action.buttonNames))
            .put("buttonSemantic", action.buttonSemantic)
            .put("ttsTemplate", action.ttsTemplate)
            .put("ttsBypassDnd", action.ttsBypassDnd)
            .put("webhookUrl", action.webhookUrl)
            .put("webhookMethod", action.webhookMethod)
            .put("webhookHeaders", action.webhookHeaders)
            .put("webhookBody", action.webhookBody)
            .put("webhookDistinct", action.webhookDistinct)
            .put("notifyScreenOn", action.notifyScreenOn)
            .put("notifyScreenOff", action.notifyScreenOff)
    }

    private fun decodeRuleV2(obj: JSONObject): NotificationFilterRule {
        val apps = parseAppTargets(obj.optJSONArray("appTargets"))
        val actions = parseActions(obj.optJSONArray("actionEntries"))
        val weekDays = parseIntSet(obj.optJSONArray("weekDays"), NotificationRuleWeekDays.ALL)
        return NotificationFilterRule(
            id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            name = obj.optString("name"),
            enabled = obj.optBoolean("enabled", true),
            userCreated = obj.optBoolean("userCreated", true),
            createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
            channelId = obj.optString("channelId").takeIf { it.isNotBlank() },
            appMode = runCatching { AppMatchMode.valueOf(obj.optString("appMode", AppMatchMode.ALL.name)) }
                .getOrDefault(AppMatchMode.ALL),
            appTargets = apps,
            textMode = runCatching { TextMatchMode.valueOf(obj.optString("textMode", TextMatchMode.ALL.name)) }
                .getOrDefault(TextMatchMode.ALL),
            keywords = parseStringList(obj.optJSONArray("keywords")),
            keywordsExclude = parseStringList(obj.optJSONArray("keywordsExclude")),
            regex = obj.optString("regex").takeIf { it.isNotBlank() },
            advancedFilterJson = obj.optString("advancedFilterJson").takeIf { it.isNotBlank() },
            ignoreCase = obj.optBoolean("ignoreCase", true),
            timeStartMs = obj.optInt("timeStartMs", 0),
            timeEndMs = obj.optInt("timeEndMs", 0),
            weekDays = weekDays,
            screenMode = runCatching { ScreenMode.valueOf(obj.optString("screenMode", ScreenMode.BOTH.name)) }
                .getOrDefault(ScreenMode.BOTH),
            chargeMask = obj.optInt("chargeMask", NotificationRuleChargeMask.ALL),
            actionEntries = actions,
        )
    }

    private fun decodeLegacyRule(obj: JSONObject): NotificationFilterRule {
        val legacyActions = parseLegacyActions(obj.optJSONArray("actions"))
        return NotificationFilterRule(
            id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            name = obj.optString("name"),
            packageName = obj.optString("packageName"),
            channelId = obj.optString("channelId").takeIf { it.isNotBlank() },
            titlePattern = obj.optString("titlePattern").takeIf { it.isNotBlank() },
            textPattern = obj.optString("textPattern").takeIf { it.isNotBlank() },
            useRegex = obj.optBoolean("useRegex", false),
            enabled = obj.optBoolean("enabled", true),
            actionEntries = legacyActions,
            userCreated = obj.optBoolean("userCreated", false),
            createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
        ).normalized()
    }

    private fun parseLegacyActions(array: JSONArray?): List<RuleActionEntry> {
        if (array == null || array.length() == 0) {
            return listOf(RuleActionEntry(NotificationRuleActionType.HIDE))
        }
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val name = array.optString(index)
                val legacy = runCatching { NotificationRuleAction.valueOf(name) }.getOrNull() ?: continue
                add(
                    when (legacy) {
                        NotificationRuleAction.HIDE -> RuleActionEntry(NotificationRuleActionType.HIDE)
                        NotificationRuleAction.TTS -> RuleActionEntry(NotificationRuleActionType.TTS)
                        NotificationRuleAction.RING -> RuleActionEntry(NotificationRuleActionType.CHANGE_SOUND)
                        NotificationRuleAction.OPEN -> RuleActionEntry(NotificationRuleActionType.OPEN)
                    },
                )
            }
        }.ifEmpty { listOf(RuleActionEntry(NotificationRuleActionType.HIDE)) }
    }

    private fun parseActions(array: JSONArray?): List<RuleActionEntry> {
        if (array == null || array.length() == 0) {
            return listOf(RuleActionEntry(NotificationRuleActionType.HIDE))
        }
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val type = runCatching {
                    NotificationRuleActionType.valueOf(obj.optString("type"))
                }.getOrNull() ?: continue
                add(
                    RuleActionEntry(
                        type = type,
                        delayTimeMs = obj.optLong("delayTimeMs", 0),
                        includeOngoing = obj.optBoolean("includeOngoing", false),
                        laterTimesMs = parseIntList(obj.optJSONArray("laterTimesMs")),
                        soundUri = obj.optString("soundUri").takeIf { it.isNotBlank() },
                        replaceTitle = obj.optString("replaceTitle").takeIf { it.isNotBlank() },
                        replaceMessage = obj.optString("replaceMessage").takeIf { it.isNotBlank() },
                        buttonNames = parseStringList(obj.optJSONArray("buttonNames")),
                        buttonSemantic = obj.optInt("buttonSemantic", 0),
                        ttsTemplate = obj.optString("ttsTemplate").takeIf { it.isNotBlank() },
                        ttsBypassDnd = obj.optBoolean("ttsBypassDnd", true),
                        webhookUrl = obj.optString("webhookUrl").takeIf { it.isNotBlank() },
                        webhookMethod = obj.optInt("webhookMethod", 1),
                        webhookHeaders = obj.optString("webhookHeaders").takeIf { it.isNotBlank() },
                        webhookBody = obj.optString("webhookBody").takeIf { it.isNotBlank() },
                        webhookDistinct = obj.optBoolean("webhookDistinct", true),
                        notifyScreenOn = obj.optInt("notifyScreenOn", 0),
                        notifyScreenOff = obj.optInt("notifyScreenOff", 0),
                    ),
                )
            }
        }.ifEmpty { listOf(RuleActionEntry(NotificationRuleActionType.HIDE)) }
    }

    private fun parseAppTargets(array: JSONArray?): List<AppTarget> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val packageName = obj.optString("packageName")
                if (packageName.isBlank()) continue
                add(AppTarget(packageName = packageName, userId = obj.optInt("user", 0)))
            }
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun parseIntList(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.optInt(index))
            }
        }
    }

    private fun parseIntSet(array: JSONArray?, fallback: Set<Int>): Set<Int> {
        if (array == null || array.length() == 0) return fallback
        return buildSet(array.length()) {
            for (index in 0 until array.length()) {
                add(array.optInt(index))
            }
        }
    }
}
