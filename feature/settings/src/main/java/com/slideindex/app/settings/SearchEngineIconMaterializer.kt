package com.slideindex.app.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Ensures search engine icons exist under [filesDir]/search_icons before backup export.
 */
internal object SearchEngineIconMaterializer {
    private val defaultEngineAssetById = mapOf(
        "default-google" to "preset_search_icons/google.png",
        "default-bilibili" to "preset_search_icons/piliplus.png",
        "default-taobao" to "preset_search_icons/taobao.png",
        "default-weibo" to "preset_search_icons/weibo.png",
        "default-zhihu" to "preset_search_icons/zhihu.png",
        "default-douyin" to "preset_search_icons/douyin.png",
        "default-xhs" to "preset_search_icons/xhs.png",
    )

    fun materialize(context: Context, engines: List<SearchEngineConfig>): List<SearchEngineConfig> =
        engines.map { materializeOne(context, it) }

    fun needsPersist(before: List<SearchEngineConfig>, after: List<SearchEngineConfig>): Boolean =
        before != after

    private fun materializeOne(context: Context, engine: SearchEngineConfig): SearchEngineConfig {
        if (engine.iconType == SearchIconType.TEXT) return engine
        if (engine.iconType == SearchIconType.URI && iconFileExists(context, engine.iconPath)) {
            return engine
        }

        val assetCandidates = buildList {
            engine.iconPath
                ?.takeIf { it.startsWith("preset_search_icons/") }
                ?.let { add(it) }
            defaultEngineAssetById[engine.id]?.let { add(it) }
        }.distinct()

        for (assetPath in assetCandidates) {
            saveIconFromAsset(context, assetPath)?.let { path ->
                return engine.copy(iconType = SearchIconType.URI, iconPath = path, textIcon = null)
            }
        }

        val packageName = engine.targetPackage?.takeIf { it.isNotBlank() }
            ?: engine.externJumpPackage?.takeIf { it.isNotBlank() }
        if (packageName != null) {
            saveIconFromPackage(context, packageName)?.let { path ->
                return engine.copy(iconType = SearchIconType.URI, iconPath = path, textIcon = null)
            }
        }

        return engine
    }

    private fun iconFileExists(context: Context, iconPath: String?): Boolean {
        val relative = iconPath?.takeIf { it.isNotBlank() } ?: return false
        return File(context.filesDir, relative).isFile
    }

    private fun saveIconFromAsset(context: Context, assetRelativePath: String): String? {
        val bytes = runCatching {
            context.assets.open(assetRelativePath).use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) return null
        return saveIconFromBytes(context, bytes)
    }

    private fun saveIconFromPackage(context: Context, packageName: String): String? {
        val drawable = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        val bytes = ByteArrayOutputStream().use { stream ->
            val bitmap = drawable.toBitmap(128, 128)
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) return null
            stream.toByteArray()
        }
        return saveIconFromBytes(context, bytes)
    }

    private fun saveIconFromBytes(context: Context, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val dir = File(context.filesDir, "search_icons").apply { mkdirs() }
        val fileName = "custom-${UUID.randomUUID()}.png"
        File(dir, fileName).writeBytes(bytes)
        return "search_icons/$fileName"
    }
}
