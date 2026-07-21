package com.slideindex.app.data

data class AppInfo(
    val packageName: String,
    val label: String,
    val letter: Char,
    val pinyinKey: String,
)

data class AppGroup(
    val letter: Char,
    val apps: List<AppInfo>,
)

sealed class AppListItem {
    data class Header(val letter: Char) : AppListItem()
    data class App(val info: AppInfo) : AppListItem()
}
