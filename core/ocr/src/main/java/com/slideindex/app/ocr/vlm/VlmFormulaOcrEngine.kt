package com.slideindex.app.ocr.vlm

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.slideindex.app.ocr.OcrRecognizeResult
import com.slideindex.app.ocr.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 基于标准 OpenAI 兼容协议的多模态视觉 OCR 引擎
 * 支持通义千问 (Qwen-VL)、智谱 (GLM-4V)、OpenAI (GPT-4o) 等所有主流视觉大模型
 */
object VlmFormulaOcrEngine {

    private const val TAG = "VlmFormulaOcrEngine"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun defaultSystemPrompt(context: Context): String =
        context.getString(R.string.vlm_default_system_prompt)

    /**
     * 识别 Bitmap 图片
     */
    suspend fun recognize(
        context: Context,
        bitmap: Bitmap,
        apiKey: String,
        baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        model: String = "qwen-vl-plus",
        prompt: String? = null,
    ): OcrRecognizeResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "recognize skipped: apiKey is empty")
            return@withContext OcrRecognizeResult.Failure(
                context.getString(R.string.ocr_error_api_key_not_configured),
            )
        }

        try {
            val base64Data = compressBitmapToBase64(bitmap)
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val endpoint = if (cleanBaseUrl.endsWith("/chat/completions")) {
                cleanBaseUrl
            } else {
                "$cleanBaseUrl/chat/completions"
            }

            val defaultPrompt = defaultSystemPrompt(context)
            val systemPromptText = prompt?.trim()?.takeIf { it.isNotEmpty() } ?: defaultPrompt

            val requestBodyJson = buildJsonObject {
                put("model", model.ifBlank { "qwen-vl-plus" })
                put("temperature", 0.1)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPromptText)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", context.getString(R.string.vlm_user_recognition_prompt))
                            })
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64,$base64Data")
                                }
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    Log.e(TAG, "Request failed code=${response.code} body=$errorBody")
                    val detail = errorBody.take(120).ifBlank {
                        context.getString(R.string.vlm_error_no_details)
                    }
                    return@withContext OcrRecognizeResult.Failure(
                        context.getString(R.string.vlm_error_cloud_http, response.code, detail),
                    )
                }

                val responseStr = response.body.string()
                val rootObj = jsonParser.parseToJsonElement(responseStr).jsonObject
                val choices = rootObj["choices"]?.jsonArray
                if (choices.isNullOrEmpty()) {
                    Log.w(TAG, "No choices in response: $responseStr")
                    return@withContext OcrRecognizeResult.Failure(
                        context.getString(R.string.vlm_error_cloud_empty),
                    )
                }

                val content = choices[0].jsonObject["message"]
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    ?.trim()

                val sanitized = content?.let { sanitizeOcrOutput(it) }
                if (sanitized.isNullOrBlank()) {
                    OcrRecognizeResult.Failure(context.getString(R.string.ocr_error_no_text_recognized))
                } else {
                    OcrRecognizeResult.Success(sanitized)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VLM formula OCR recognition error", e)
            val message = e.localizedMessage ?: e.message ?: context.getString(R.string.vlm_error_unknown)
            OcrRecognizeResult.Failure(context.getString(R.string.vlm_error_network, message))
        }
    }

    /**
     * 对 OCR 输出进行轻量清洗保障
     */
    private fun sanitizeOcrOutput(raw: String): String {
        var text = raw.trim()
        // 去除模型可能包裹的外层代码块标记，例如 ```markdown ... ``` 或 ```latex ... ```
        if (text.startsWith("```") && text.endsWith("```")) {
            val lines = text.lines()
            if (lines.size >= 2 && lines.first().startsWith("```") && lines.last().startsWith("```")) {
                text = lines.subList(1, lines.size - 1).joinToString("\n").trim()
            }
        }
        // 清洗明显误加 $ 的纯数字、序号等，如 $A.$ -> A. , $(1)$ -> (1) , $2026$ -> 2026
        text = text.replace(Regex("""\$(?!\$)([0-9]+|[A-Za-z]\.|\([0-9A-Za-z]+\)|\[[0-9A-Za-z]+\])\$""")) { matchResult ->
            matchResult.groupValues[1]
        }
        return text
    }

    /**
     * 测试 API 连接与鉴权有效性
     * @return Pair(isSuccess, message)
     */
    suspend fun testConnection(
        context: Context,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Pair(false, context.getString(R.string.vlm_error_api_key_empty))
        }
        try {
            val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
            val requestBodyJson = buildJsonObject {
                put("model", model.trim())
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", "hi")
                    })
                })
                put("max_tokens", 5)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Pair(true, context.getString(R.string.vlm_test_success))
                } else {
                    val code = response.code
                    val err = response.body.string().take(200)
                    Pair(false, context.getString(R.string.vlm_test_failed, code, err))
                }
            }
        } catch (e: Exception) {
            Pair(false, context.getString(R.string.vlm_test_network_error, e.localizedMessage ?: ""))
        }
    }

    private fun compressBitmapToBase64(bitmap: Bitmap): String {
        val maxDimension = 1200
        val width = bitmap.width
        val height = bitmap.height
        val scale = if (width > maxDimension || height > maxDimension) {
            val maxSide = maxOf(width, height)
            maxDimension.toFloat() / maxSide
        } else {
            1.0f
        }

        val targetBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        targetBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
