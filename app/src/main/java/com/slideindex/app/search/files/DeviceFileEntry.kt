package com.slideindex.app.search.files

import android.net.Uri

data class DeviceFileEntry(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val lastModified: Long,
    val isDirectory: Boolean,
    val relativePath: String? = null,
)
