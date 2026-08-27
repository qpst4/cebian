package com.slideindex.app.settings

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SearchEngineType {
    DIRECT_LINK,
    SHARE_TO_APP,
    SHARE_IMAGE_TO_APP,
    JUMP_TO_ACTIVITY,
    EXTERN_JUMP_LINK,
}

@Serializable
enum class SearchIconType {
    URI,
    TEXT,
    OTHER,
}

@Serializable
data class SearchEngineConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val engineType: SearchEngineType = SearchEngineType.DIRECT_LINK,
    val iconType: SearchIconType = SearchIconType.OTHER,
    /** Relative path under app files dir, e.g. search_icons/saveIcon-xxx */
    val iconPath: String? = null,
    val textIcon: String? = null,
    /** Prefix shortcut for search panel, e.g. "g" → Google; triggers after a trailing space. */
    val aliasCode: String? = null,
    val searchLink: String? = null,
    val externJumpLink: String? = null,
    val externJumpPackage: String? = null,
    val targetPackage: String? = null,
    val targetActivity: String? = null,
    val autoInputEnter: Boolean = true,
    val showInPickPanel: Boolean = true,
    val sortOrder: Int = 0,
) {
    fun isTextSearchEngine(): Boolean = when (engineType) {
        SearchEngineType.DIRECT_LINK,
        SearchEngineType.JUMP_TO_ACTIVITY,
        SearchEngineType.EXTERN_JUMP_LINK,
        SearchEngineType.SHARE_TO_APP,
        -> showInPickPanel
        SearchEngineType.SHARE_IMAGE_TO_APP -> false
    }
}
