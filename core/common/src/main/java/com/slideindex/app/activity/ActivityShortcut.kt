package com.slideindex.app.activity

import java.util.UUID

data class ActivityShortcut(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val packageName: String,
    val activityClassName: String,
) {
    fun componentFlat(): String = "$packageName/$activityClassName"
}

data class ActivityShortcutPreset(
    val label: String,
    val packageName: String,
    val activityClassName: String,
) {
    fun toShortcut(): ActivityShortcut = ActivityShortcut(
        label = label,
        packageName = packageName,
        activityClassName = activityClassName,
    )
}

object ActivityShortcutCodec {
    private const val SEP = "\u001E"
    private const val LIST_SEP = "\u001F"

    fun encode(item: ActivityShortcut): String =
        listOf(item.id, item.label, item.packageName, item.activityClassName).joinToString(SEP)

    fun decode(raw: String): ActivityShortcut? {
        val parts = raw.split(SEP, limit = 4)
        if (parts.size < 4) return null
        val id = parts[0]
        val label = parts[1]
        val packageName = parts[2]
        val activityClassName = parts[3]
        if (label.isBlank() || packageName.isBlank() || activityClassName.isBlank()) return null
        return ActivityShortcut(
            id = id.ifBlank { UUID.randomUUID().toString() },
            label = label,
            packageName = packageName,
            activityClassName = activityClassName,
        )
    }

    fun encodeAll(items: List<ActivityShortcut>): Set<String> =
        if (items.isEmpty()) {
            emptySet()
        } else {
            setOf(items.joinToString(LIST_SEP) { encode(it) })
        }

    fun decodeAll(raw: Set<String>): List<ActivityShortcut> {
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

object ActivityShortcutCatalog {
    private const val WECHAT = "com.tencent.mm"

    fun presets(): List<ActivityShortcutPreset> = listOf(
        ActivityShortcutPreset(
            label = "优惠券",
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.card.ui.v4.CouponAndGiftCardListV4UI",
        ),
        ActivityShortcutPreset(
            label = "收藏",
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.fav.ui.FavoriteIndexUI",
        ),
        ActivityShortcutPreset(
            label = "搜索",
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.fts.ui.FTSMainUI",
        ),
        ActivityShortcutPreset(
            label = "扫一扫",
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.scanner.ui.BaseScanUI",
        ),
        ActivityShortcutPreset(
            label = "发票",
            packageName = WECHAT,
            activityClassName = "com.tencent.mm.plugin.appbrand.ui.AppBrandUI01",
        ),
    )
}
