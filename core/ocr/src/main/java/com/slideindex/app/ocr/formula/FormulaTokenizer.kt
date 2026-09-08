package com.slideindex.app.ocr.formula

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 公式识别词表解析与反查器
 * 将模型输出的 Token ID 解码为对应的 LaTeX 数学公式和中文字符
 */
class FormulaTokenizer(private val vocab: List<String>) {

    companion object {
        const val PAD_TOKEN = "<pad>"
        const val EOS_TOKEN = "<eos>"
        const val BOS_TOKEN = "<bos>"
        const val UNK_TOKEN = "<unk>"

        fun fromAssets(context: Context, assetPath: String = "formula/vocab.txt"): FormulaTokenizer {
            val list = ArrayList<String>()
            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).forEachLine { line ->
                    val token = line.trim()
                    if (token.isNotEmpty()) {
                        list.add(token)
                    }
                }
            }
            return FormulaTokenizer(list)
        }
    }

    val eosId: Long = vocab.indexOf(EOS_TOKEN).toLong().takeIf { it >= 0 } ?: 2L
    val bosId: Long = vocab.indexOf(BOS_TOKEN).toLong().takeIf { it >= 0 } ?: 1L
    val padId: Long = vocab.indexOf(PAD_TOKEN).toLong().takeIf { it >= 0 } ?: 0L

    val vocabSize: Int
        get() = vocab.size

    /**
     * 将生成的 token id 序列解码为标准 LaTeX 字符串
     */
    fun decode(tokenIds: List<Long>): String {
        val tokens = mutableListOf<String>()
        for (id in tokenIds) {
            if (id == eosId) break
            if (id == bosId || id == padId) continue
            val idx = id.toInt()
            if (idx in vocab.indices) {
                val token = vocab[idx]
                if (token != UNK_TOKEN) {
                    tokens.add(token)
                }
            }
        }
        return tokens.joinToString(" ").trim()
    }
}
