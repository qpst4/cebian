package com.slideindex.app.data

import android.content.Context
import com.slideindex.app.util.PinyinHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStreamReader

data class PresetShortcutItem(
    val name: String,
    val packageName: String,
    val uploader: String,
    val introduction: String,
    val targetActionUrl: String,
    val evoScheme: String,
    val pinyinName: String = "",
    val initialName: String = "",
    val pinyinIntro: String = "",
)

data class PresetShortcutAppGroup(
    val packageName: String,
    val appLabel: String,
    val shortcuts: List<PresetShortcutItem>,
    val pinyinLabel: String = "",
)

object PresetShortcutRepository {
    private const val ASSET_PATH = "shortcuts/preset_shortcuts.json"
    private var cachedItems: List<PresetShortcutItem>? = null
    private var cachedGroups: List<PresetShortcutAppGroup>? = null

    suspend fun loadItems(context: Context): List<PresetShortcutItem> = withContext(Dispatchers.IO) {
        cachedItems?.let { return@withContext it }
        val appContext = context.applicationContext
        val list = mutableListOf<PresetShortcutItem>()
        val seenKeys = HashSet<String>()

        runCatching {
            val jsonString = appContext.assets.open(ASSET_PATH).use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).readText()
            }
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                var pkg = obj.optString("package", "").trim()
                val uploader = obj.optString("uploader", "").trim()
                val intro = obj.optString("introduction", "").trim()
                val targetUrl = obj.optString("target_action_url", "").trim()
                val scheme = obj.optString("evo_scheme", "").trim()

                if (name.isEmpty() || targetUrl.isEmpty()) continue

                if (pkg.isEmpty()) {
                    pkg = inferPackageName(targetUrl, scheme)
                }

                val dedupKey = "$pkg|$name|$targetUrl"
                if (!seenKeys.add(dedupKey)) {
                    continue
                }

                list.add(
                    PresetShortcutItem(
                        name = name,
                        packageName = pkg,
                        uploader = uploader,
                        introduction = intro,
                        targetActionUrl = targetUrl,
                        evoScheme = scheme,
                        pinyinName = PinyinHelper.sortKey(name),
                        initialName = PinyinHelper.initialKey(name),
                        pinyinIntro = PinyinHelper.sortKey(intro),
                    )
                )
            }
        }
        cachedItems = list
        list
    }

    suspend fun loadGroups(context: Context): List<PresetShortcutAppGroup> = withContext(Dispatchers.IO) {
        cachedGroups?.let { return@withContext it }
        val items = loadItems(context)
        val pm = context.applicationContext.packageManager

        val grouped = LinkedHashMap<String, MutableList<PresetShortcutItem>>()
        for (item in items) {
            grouped.getOrPut(item.packageName) { mutableListOf() }.add(item)
        }

        val groups = grouped.map { (pkg, shortcutList) ->
            val appLabel = if (pkg.isNotBlank()) {
                runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: resolveFallbackLabel(pkg)
            } else {
                "其他快捷指令"
            }

            val pinyinLabel = PinyinHelper.sortKey(appLabel)
            PresetShortcutAppGroup(
                packageName = pkg,
                appLabel = appLabel,
                shortcuts = shortcutList.sortedBy { it.pinyinName },
                pinyinLabel = pinyinLabel,
            )
        }.sortedBy { it.pinyinLabel }

        cachedGroups = groups
        groups
    }

    fun filterGroups(
        groups: List<PresetShortcutAppGroup>,
        query: String,
    ): List<PresetShortcutAppGroup> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return groups

        return groups.mapNotNull { group ->
            val appLabelMatches = group.appLabel.lowercase().contains(q) ||
                group.packageName.lowercase().contains(q) ||
                group.pinyinLabel.contains(q) ||
                PinyinHelper.initialKey(group.appLabel).contains(q)

            val filteredShortcuts = group.shortcuts.filter { item ->
                appLabelMatches ||
                    item.name.lowercase().contains(q) ||
                    item.introduction.lowercase().contains(q) ||
                    item.pinyinName.contains(q) ||
                    item.initialName.contains(q) ||
                    item.pinyinIntro.contains(q) ||
                    item.targetActionUrl.lowercase().contains(q)
            }

            if (filteredShortcuts.isEmpty()) null
            else group.copy(shortcuts = filteredShortcuts)
        }
    }

    private fun inferPackageName(targetActionUrl: String, evoScheme: String): String {
        val lowerUrl = targetActionUrl.lowercase()
        return when {
            lowerUrl.startsWith("tbopen://") || lowerUrl.contains("taobao.com") -> "com.taobao.taobao"
            lowerUrl.startsWith("alipays://") || lowerUrl.startsWith("alipayqr://") || lowerUrl.contains("alipay") -> "com.eg.android.AlipayGphone"
            lowerUrl.startsWith("weixin://") || lowerUrl.startsWith("wechat://") || lowerUrl.contains("tencent.mm") -> "com.tencent.mm"
            lowerUrl.startsWith("mqq://") || lowerUrl.startsWith("mqqapi://") || lowerUrl.contains("tencent.mobileqq") -> "com.tencent.mobileqq"
            lowerUrl.startsWith("bilibili://") -> "tv.danmaku.bili"
            lowerUrl.startsWith("imeituan://") -> "com.sankuai.meituan"
            lowerUrl.startsWith("pinduoduo://") -> "com.xunmeng.pinduoduo"
            lowerUrl.startsWith("openapp.jdmobile://") -> "com.jingdong.app.mall"
            lowerUrl.startsWith("snssdk1128://") -> "com.ss.android.ugc.aweme"
            lowerUrl.startsWith("coolmarket://") -> "com.coolapk.market"
            lowerUrl.startsWith("orpheus://") -> "com.netease.cloudmusic"
            lowerUrl.startsWith("qqmusic://") -> "com.tencent.qqmusic"
            lowerUrl.startsWith("upwallet://") -> "com.unionpay"
            lowerUrl.contains("component=") -> {
                val comp = lowerUrl.substringAfter("component=").substringBefore(';').substringBefore('"').substringBefore('&')
                comp.substringBefore('/')
            }
            lowerUrl.contains("package=") -> {
                lowerUrl.substringAfter("package=").substringBefore(';').substringBefore('"').substringBefore('&')
            }
            else -> ""
        }
    }

    private fun resolveFallbackLabel(packageName: String): String {
        return when {
            packageName.contains("AlipayGphone", ignoreCase = true) -> "支付宝"
            packageName.contains("coolapk", ignoreCase = true) -> "酷安"
            packageName.contains("tencent.mm", ignoreCase = true) -> "微信"
            packageName.contains("sankuai.meituan", ignoreCase = true) -> "美团"
            packageName.contains("taobao", ignoreCase = true) -> "淘宝"
            packageName.contains("bili", ignoreCase = true) -> "哔哩哔哩"
            packageName.contains("mobileqq", ignoreCase = true) -> "QQ"
            packageName.contains("qqmusic", ignoreCase = true) -> "QQ音乐"
            packageName.contains("netease.cloudmusic", ignoreCase = true) -> "网易云音乐"
            packageName.contains("unionpay", ignoreCase = true) -> "云闪付"
            packageName.contains("android.settings", ignoreCase = true) -> "系统设置"
            packageName.contains("miui.securitycenter", ignoreCase = true) -> "手机管家/安全中心"
            packageName.contains("pinduoduo", ignoreCase = true) -> "拼多多"
            packageName.contains("jd", ignoreCase = true) -> "京东"
            packageName.contains("aweme", ignoreCase = true) -> "抖音"
            else -> packageName.substringAfterLast('.')
        }
    }
}
