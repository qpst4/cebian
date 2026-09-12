package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.settings.R

object SearchEngineCatalog {
    private fun safeString(context: Context, resId: Int, fallback: String): String =
        runCatching { context.getString(resId) }.getOrDefault(fallback)

    fun defaultEngines(context: Context): List<SearchEngineConfig> = listOf(
        SearchEngineConfig(
            id = "default-google",
            name = "Google",
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "https://www.google.com/search?q=%s",
            sortOrder = 0,
        ),
        SearchEngineConfig(
            id = "default-bilibili",
            name = safeString(context, R.string.default_engine_bilibili, "Bilibili"),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "bilibili://search?keyword=%q",
            targetPackage = "com.example.piliplus",
            sortOrder = 1,
        ),
        SearchEngineConfig(
            id = "default-taobao",
            name = safeString(context, R.string.default_engine_taobao, "淘宝"),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "tbopen://m.taobao.com/tbopen/index.html?h5Url=https://s.taobao.com/search?q=%s",
            targetPackage = "com.taobao.taobao",
            sortOrder = 2,
        ),
        SearchEngineConfig(
            id = "default-weibo",
            name = safeString(context, R.string.default_engine_weibo, "微博"),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "sinaweibo://searchall?q=%s",
            targetPackage = "com.sina.weibo",
            sortOrder = 3,
        ),
        SearchEngineConfig(
            id = "default-zhihu",
            name = safeString(context, R.string.default_engine_zhihu, "知乎"),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "zhihu://search?q=%s",
            targetPackage = "com.zhihu.android",
            sortOrder = 4,
        ),
        SearchEngineConfig(
            id = "default-douyin",
            name = safeString(context, R.string.default_engine_douyin, "抖音"),
            engineType = SearchEngineType.JUMP_TO_ACTIVITY,
            searchLink = "snssdk1128://search/result?keyword=%s",
            targetPackage = "com.ss.android.ugc.aweme",
            targetActivity = "com.ss.android.ugc.aweme.search.activity.SearchResultActivity",
            sortOrder = 5,
        ),
        SearchEngineConfig(
            id = "default-qq",
            name = "QQ",
            engineType = SearchEngineType.JUMP_TO_ACTIVITY,
            targetPackage = "com.tencent.mobileqq",
            targetActivity = "com.tencent.mobileqq.search.activity.UniteSearchActivity",
            sortOrder = 6,
        ),
        SearchEngineConfig(
            id = "default-wechat",
            name = safeString(context, R.string.default_engine_wechat, "微信"),
            engineType = SearchEngineType.JUMP_TO_ACTIVITY,
            targetPackage = "com.tencent.mm",
            targetActivity = "com.tencent.mm.plugin.fts.ui.FTSMainUI",
            sortOrder = 7,
        ),
        SearchEngineConfig(
            id = "default-meituan",
            name = safeString(context, R.string.default_engine_meituan, "美团"),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "imeituan://www.meituan.com/search?q=%s",
            targetPackage = "com.sankuai.meituan",
            sortOrder = 8,
        ),
        SearchEngineConfig(
            id = "default-xhs",
            name = safeString(context, R.string.default_engine_xhs, "小红书"),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "xhsdiscover://search/result?keyword=%s",
            targetPackage = "com.xingin.xhs",
            sortOrder = 9,
        ),
    )
}
