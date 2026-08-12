package com.slideindex.app.nativeengine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NativeEnginePackCatalog(
    val version: Int = 1,
    val packs: List<NativeEnginePackEntry> = emptyList(),
)

@Serializable
data class NativeEnginePackEntry(
    val id: String,
    val sizeBytes: Long,
    val url: String,
    val mirrorUrls: List<String> = emptyList(),
    val sha256: String,
    val packRevision: Int = 1,
    val displayVersion: String? = null,
    val minAppVersionCode: Int = 1,
    val libraries: List<String> = emptyList(),
    val assetPaths: List<String> = emptyList(),
)

@Singleton
class NativeEnginePackCatalogProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val catalog: NativeEnginePackCatalog by lazy {
        context.assets.open("native_engine_packs.json").bufferedReader().use { reader ->
            json.decodeFromString<NativeEnginePackCatalog>(reader.readText())
        }
    }

    fun findPack(packId: String): NativeEnginePackEntry? =
        catalog.packs.firstOrNull { it.id == packId }

    fun allPacks(): List<NativeEnginePackEntry> = catalog.packs
}
