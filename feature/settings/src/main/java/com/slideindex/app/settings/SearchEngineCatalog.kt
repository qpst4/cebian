package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.settings.R

object SearchEngineCatalog {
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
            name = context.getString(R.string.default_engine_bilibili),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "bilibili://search?keyword=%q",
            targetPackage = "com.example.piliplus",
            sortOrder = 1,
        ),
        SearchEngineConfig(
            id = "default-taobao",
            name = context.getString(R.string.default_engine_taobao),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "tbopen://m.taobao.com/tbopen/index.html?h5Url=https://s.taobao.com/search?q=%s",
            targetPackage = "com.taobao.taobao",
            sortOrder = 2,
        ),
        SearchEngineConfig(
            id = "default-weibo",
            name = context.getString(R.string.default_engine_weibo),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "sinaweibo://searchall?q=%s",
            targetPackage = "com.sina.weibo",
            sortOrder = 3,
        ),
        SearchEngineConfig(
            id = "default-zhihu",
            name = context.getString(R.string.default_engine_zhihu),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "zhihu://search?q=%s",
            targetPackage = "com.zhihu.android",
            sortOrder = 4,
        ),
        SearchEngineConfig(
            id = "default-douyin",
            name = context.getString(R.string.default_engine_douyin),
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
            name = context.getString(R.string.default_engine_wechat),
            engineType = SearchEngineType.JUMP_TO_ACTIVITY,
            targetPackage = "com.tencent.mm",
            targetActivity = "com.tencent.mm.plugin.fts.ui.FTSMainUI",
            sortOrder = 7,
        ),
        SearchEngineConfig(
            id = "default-meituan",
            name = context.getString(R.string.default_engine_meituan),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "imeituan://www.meituan.com/search?q=%s",
            targetPackage = "com.sankuai.meituan",
            sortOrder = 8,
        ),
        SearchEngineConfig(
            id = "default-xhs",
            name = context.getString(R.string.default_engine_xhs),
            engineType = SearchEngineType.DIRECT_LINK,
            searchLink = "xhsdiscover://search/result?keyword=%s",
            targetPackage = "com.xingin.xhs",
            sortOrder = 9,
        ),
    )
}
