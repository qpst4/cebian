package com.slideindex.app.stash

import kotlinx.serialization.Serializable

@Serializable
enum class StashEntryType {
    TEXT,
    IMAGE,
}

@Serializable
data class StashEntry(
    val id: String,
    val type: StashEntryType,
    val text: String? = null,
    val imageFileName: String? = null,
    val createdAtEpochMs: Long,
    val starred: Boolean = false,
    /** 钉图在屏幕上的显示宽高（逻辑像素），用于从暂存夹恢复时保持原尺寸。 */
    val pinDisplayWidthPx: Int? = null,
    val pinDisplayHeightPx: Int? = null,
)
