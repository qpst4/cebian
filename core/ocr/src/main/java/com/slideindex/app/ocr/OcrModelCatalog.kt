package com.slideindex.app.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OcrModelCatalog(
    val version: Int = 1,
    val models: List<OcrModelEntry> = emptyList(),
)

@Serializable
data class OcrModelEntry(
    val id: String,
    val sizeBytes: Long,
    val engine: String = OcrEngines.PPOCR,
    val builtin: Boolean = false,
    val files: List<OcrModelFileSpec> = emptyList(),
) {
    /** 下载/展示用总大小：有分文件 size 时求和，否则回退模型级 sizeBytes。 */
    val totalDownloadBytes: Long
        get() {
            if (files.isEmpty()) return sizeBytes
            val fileSum = files.sumOf { it.sizeBytes ?: 0L }
            return fileSum.takeIf { it > 0L } ?: sizeBytes
        }
}

object OcrEngines {
    const val PPOCR = "ppocr"
    const val MLKIT_CHINESE = "mlkit_chinese"
    const val TESSERACT = "tesseract"
}

@Serializable
data class OcrModelFileSpec(
    val relativePath: String,
    val url: String,
    val mirrorUrls: List<String> = emptyList(),
    val sha256: String? = null,
    val sizeBytes: Long? = null,
)

@Singleton
class OcrModelCatalogProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val catalog: OcrModelCatalog by lazy {
        context.assets.open("ocr_models.json").bufferedReader().use { reader ->
            json.decodeFromString<OcrModelCatalog>(reader.readText())
        }
    }

    fun findModel(modelId: String): OcrModelEntry? =
        catalog.models.firstOrNull { it.id == modelId }

    fun allModels(): List<OcrModelEntry> = catalog.models
}
