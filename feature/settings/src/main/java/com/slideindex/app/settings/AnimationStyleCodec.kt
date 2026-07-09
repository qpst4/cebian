package com.slideindex.app.settings

import org.json.JSONObject

object AnimationStyleCodec {
    fun encode(styles: AnimationStyles): String {
        val root = JSONObject()
        root.put("type", styles.type)
        root.put("json", styles.json)
        val map = JSONObject()
        styles.jsonMap.forEach { (key, value) -> map.put(key.toString(), value) }
        root.put("jsonMap", map)
        return root.toString()
    }

    fun decode(raw: String?): AnimationStyles {
        if (raw.isNullOrBlank()) return AnimationStyles()
        return runCatching {
            val root = JSONObject(raw)
            val type = root.optInt("type", AnimationStyleDefaults.TYPE_WAVE)
            val json = root.optString("json", "")
            val jsonMap = buildMap {
                val mapObj = root.optJSONObject("jsonMap") ?: return@buildMap
                mapObj.keys().forEach { key ->
                    put(key.toInt(), mapObj.optString(key, ""))
                }
            }
            AnimationStyles(type = type, json = json, jsonMap = jsonMap)
        }.getOrDefault(AnimationStyles())
    }

    fun encodeWave(style: WaveStyle): String = JSONObject().apply {
        put("backgroundColor", style.backgroundColor)
        put("strokeColor", style.strokeColor)
        put("strokeWidth", style.strokeWidth)
        put("width", style.width)
        put("bezierLengthHalfRatio", style.bezierLengthHalfRatio.toDouble())
        put("safeBounds", style.safeBounds)
        put("transformEnabled", style.transformEnabled)
        put("iconColor", style.iconColor)
        put("iconScale", style.iconScale.toDouble())
        put("iconType", style.iconType)
        put("stickySlideEnabled", style.stickySlideEnabled)
        put("stickySlidePx", style.stickySlidePx)
    }.toString()

    fun encodeCapsule(style: CapsuleStyle): String = JSONObject().apply {
        put("backgroundColor", style.backgroundColor)
        put("strokeColor", style.strokeColor)
        put("strokeWidth", style.strokeWidth)
        put("thickness", style.thickness)
        put("maxLength", style.maxLength)
        put("cornerRadius", style.cornerRadius)
        put("iconColor", style.iconColor)
        put("iconScale", style.iconScale.toDouble())
        put("iconType", style.iconType)
    }.toString()

    fun encodeBubble(style: BubbleStyle): String = JSONObject().apply {
        put("backgroundColor", style.backgroundColor)
        put("strokeColor", style.strokeColor)
        put("strokeWidth", style.strokeWidth)
        put("diameter", style.diameter)
        put("maxOffset", style.maxOffset)
        put("iconColor", style.iconColor)
        put("iconScale", style.iconScale.toDouble())
        put("iconType", style.iconType)
    }.toString()

    fun decode(raw: String, default: WaveStyle): WaveStyle = runCatching {
        val json = JSONObject(raw)
        WaveStyle(
            backgroundColor = json.optInt("backgroundColor", default.backgroundColor),
            strokeColor = json.optInt("strokeColor", default.strokeColor),
            strokeWidth = json.optInt("strokeWidth", default.strokeWidth),
            width = json.optInt("width", default.width),
            bezierLengthHalfRatio = json.optDouble(
                "bezierLengthHalfRatio",
                default.bezierLengthHalfRatio.toDouble(),
            ).toFloat(),
            safeBounds = json.optBoolean("safeBounds", default.safeBounds),
            transformEnabled = json.optBoolean("transformEnabled", default.transformEnabled),
            iconColor = json.optInt("iconColor", default.iconColor),
            iconScale = json.optDouble("iconScale", default.iconScale.toDouble()).toFloat(),
            iconType = json.optInt("iconType", default.iconType),
            stickySlideEnabled = json.optBoolean("stickySlideEnabled", default.stickySlideEnabled),
            stickySlidePx = json.optInt("stickySlidePx", default.stickySlidePx),
        )
    }.getOrDefault(default)

    fun decode(raw: String, default: CapsuleStyle): CapsuleStyle = runCatching {
        val json = JSONObject(raw)
        CapsuleStyle(
            backgroundColor = json.optInt("backgroundColor", default.backgroundColor),
            strokeColor = json.optInt("strokeColor", default.strokeColor),
            strokeWidth = json.optInt("strokeWidth", default.strokeWidth),
            thickness = json.optInt("thickness", default.thickness),
            maxLength = json.optInt("maxLength", default.maxLength),
            cornerRadius = json.optInt("cornerRadius", default.cornerRadius),
            iconColor = json.optInt("iconColor", default.iconColor),
            iconScale = json.optDouble("iconScale", default.iconScale.toDouble()).toFloat(),
            iconType = json.optInt("iconType", default.iconType),
        )
    }.getOrDefault(default)

    fun decode(raw: String, default: BubbleStyle): BubbleStyle = runCatching {
        val json = JSONObject(raw)
        BubbleStyle(
            backgroundColor = json.optInt("backgroundColor", default.backgroundColor),
            strokeColor = json.optInt("strokeColor", default.strokeColor),
            strokeWidth = json.optInt("strokeWidth", default.strokeWidth),
            diameter = json.optInt("diameter", default.diameter),
            maxOffset = json.optInt("maxOffset", default.maxOffset),
            iconColor = json.optInt("iconColor", default.iconColor),
            iconScale = json.optDouble("iconScale", default.iconScale.toDouble()).toFloat(),
            iconType = json.optInt("iconType", default.iconType),
        )
    }.getOrDefault(default)
}
