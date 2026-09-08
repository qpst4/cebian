package com.slideindex.app.ocr.formula

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 ONNX Runtime 的手写公式与中文混合识别引擎
 */
class FormulaRecognizer(
    private val context: Context,
    private val tokenizer: FormulaTokenizer,
    private val session: OrtSession,
    private val env: OrtEnvironment
) : AutoCloseable {

    companion object {
        private const val TAG = "FormulaRecognizer"
        private const val MAX_PREDICT_TOKENS = 512

        /**
         * 从外部文件或内置 assets 创建识别器
         */
        fun create(
            context: Context,
            modelFile: File,
            vocabFile: File
        ): FormulaRecognizer {
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val session = env.createSession(modelFile.absolutePath, sessionOptions)
            val tokenizer = FormulaTokenizer(vocabFile.readLines(Charsets.UTF_8))
            return FormulaRecognizer(context, tokenizer, session, env)
        }

        /**
         * 从 assets 目录快捷加载
         */
        fun createFromAssets(
            context: Context,
            assetModelPath: String = "formula/pp_formulanet_plus_m.onnx",
            assetVocabPath: String = "formula/vocab.txt"
        ): FormulaRecognizer {
            val modelFile = copyAssetToCache(context, assetModelPath)
            val vocabFile = copyAssetToCache(context, assetVocabPath)
            return create(context, modelFile, vocabFile)
        }

        private fun copyAssetToCache(context: Context, assetPath: String): File {
            val cacheFile = File(context.cacheDir, File(assetPath).name)
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return cacheFile
        }
    }

    /**
     * 识别图片中的手写公式，返回标准 LaTeX 字符串
     */
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        try {
            val preprocessed = FormulaPreprocessor.preprocessToFloatBuffer(bitmap)
            val shape = longArrayOf(
                preprocessed.batchSize,
                preprocessed.channels,
                preprocessed.height,
                preprocessed.width
            )

            val inputTensor = OnnxTensor.createTensor(env, preprocessed.floatBuffer, shape)
            val inputName = session.inputNames.first()

            val output = session.run(mapOf(inputName to inputTensor))
            val rawOutput = output[0].value

            // 解析模型预测的 Token 序列
            val tokenIds = extractTokenIds(rawOutput)
            inputTensor.close()
            output.close()

            tokenizer.decode(tokenIds)
        } catch (e: Exception) {
            Log.e(TAG, "Formula recognition error", e)
            ""
        }
    }

    private fun extractTokenIds(outputValue: Any): List<Long> {
        val result = mutableListOf<Long>()
        when (outputValue) {
            is Array<*> -> {
                // 处理二维数组 [1, SeqLen]
                val firstBatch = outputValue[0]
                if (firstBatch is LongArray) {
                    result.addAll(firstBatch.toList())
                } else if (firstBatch is IntArray) {
                    result.addAll(firstBatch.map { it.toLong() })
                }
            }
            is LongArray -> {
                result.addAll(outputValue.toList())
            }
            is IntArray -> {
                result.addAll(outputValue.map { it.toLong() })
            }
        }
        return result
    }

    override fun close() {
        session.close()
        env.close()
    }
}
