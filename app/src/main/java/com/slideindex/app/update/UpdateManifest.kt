package com.slideindex.app.update

import kotlinx.serialization.Serializable

/** 仓库根目录 [update.json]，发版时由脚本更新，App 通过 CDN/raw 拉取（不走 GitHub API）。 */
@Serializable
data class UpdateManifest(
    val version: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val apkSize: Long = 0L,
    val notes: String = "",
)
