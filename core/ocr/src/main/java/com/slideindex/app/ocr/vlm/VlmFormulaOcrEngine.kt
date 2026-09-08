package com.slideindex.app.ocr.vlm

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
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
import com.slideindex.app.ocr.OcrRecognizeResult

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

    val DEFAULT_SYSTEM_PROMPT: String = """你是一个专业的全能 OCR 文字与公式识别助手。请严格按照图片原样提取文字内容，遵循以下排版规则：

1. 普通文本与段落：
   - 所有的中文、普通英文单词、标点符号、日常数字（如序号、日期、时间、金额、编号等）必须直接输出为纯文本，严禁包裹任何 ${'$'} 符号。
   - 严禁将普通的孤立英文字母、选项序号（如 A、B、C 或 (1)、(2)）、纯数字用 ${'$'} 包裹。

2. 数学与科学公式：
   - 仅当遇到真正的数学表达式（包含算术运算符号、分数、根号、求和、微积分、希腊字母或明显上下标的公式）时，才使用 LaTeX 语法。
   - 行内公式使用单个 ${'$'} 包裹（例如 ${'$'}E = mc^2${'$'}）。
   - 独立公式块使用 ${'$'}${'$'} 包裹（例如 ${'$'}${'$'}\frac{a}{b}${'$'}${'$'}）。
   - 如果公式结构内部含有中文（如下标、注释等），请在 LaTeX 中使用 \text{中文} 格式包裹。

3. 输出要求：
   - 保持原始自然段落换行与阅读顺序。
   - 直接输出提取结果，严禁输出任何开场白、解释说明或外部 markdown 代码块标记。"""

    /**
     * 识别 Bitmap 图片
     */
    suspend fun recognize(
        bitmap: Bitmap,
        apiKey: String,
        baseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        model: String = "qwen-vl-plus",
        prompt: String = DEFAULT_SYSTEM_PROMPT
    ): OcrRecognizeResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "recognize skipped: apiKey is empty")
            return@withContext OcrRecognizeResult.Failure("未配置 API Key，请在 OCR 模型管理 → 云端配置")
        }

        try {
            val base64Data = compressBitmapToBase64(bitmap)
            val cleanBaseUrl = baseUrl.trimEnd('/')
            val endpoint = if (cleanBaseUrl.endsWith("/chat/completions")) {
                cleanBaseUrl
            } else {
                "$cleanBaseUrl/chat/completions"
            }

            val systemPromptText = prompt.ifBlank { DEFAULT_SYSTEM_PROMPT }

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
                                put("text", "请严格按照系统设定的排版规则识别这张图片中的文字和公式：")
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
                    val detail = errorBody.take(120).ifBlank { "无详情" }
                    return@withContext OcrRecognizeResult.Failure("云端识别失败 (HTTP ${response.code})：$detail")
                }

                val responseStr = response.body.string()
                val rootObj = jsonParser.parseToJsonElement(responseStr).jsonObject
                val choices = rootObj["choices"]?.jsonArray
                if (choices.isNullOrEmpty()) {
                    Log.w(TAG, "No choices in response: $responseStr")
                    return@withContext OcrRecognizeResult.Failure("云端返回异常：未包含识别结果")
                }

                val content = choices[0].jsonObject["message"]
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content
                    ?.trim()

                val sanitized = content?.let { sanitizeOcrOutput(it) }
                if (sanitized.isNullOrBlank()) {
                    OcrRecognizeResult.Failure("未识别到文字")
                } else {
                    OcrRecognizeResult.Success(sanitized)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VLM formula OCR recognition error", e)
            OcrRecognizeResult.Failure("网络或接口异常：${e.localizedMessage ?: e.message ?: "未知错误"}")
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
        apiKey: String,
        baseUrl: String,
        model: String,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Pair(false, "API Key 不能为空")
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
                    Pair(true, "连接成功！接口鉴权正常")
                } else {
                    val code = response.code
                    val err = response.body.string().take(200)
                    Pair(false, "连接失败(HTTP $code): $err")
                }
            }
        } catch (e: Exception) {
            Pair(false, "网络或地址异常: ${e.localizedMessage}")
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
