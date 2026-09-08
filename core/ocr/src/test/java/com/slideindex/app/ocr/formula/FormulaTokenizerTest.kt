package com.slideindex.app.ocr.formula

import org.junit.Assert.assertEquals
import org.junit.Test

class FormulaTokenizerTest {

    @Test
    fun testDecodeStandardFormula() {
        val vocab = listOf(
            "<pad>", // 0
            "<bos>", // 1
            "<eos>", // 2
            "<unk>", // 3
            "\\lim", // 4
            "_",     // 5
            "{",     // 6
            "x",     // 7
            "\\to",  // 8
            "0",     // 9
            "}",     // 10
            "\\frac",// 11
            "1",     // 12
            "+",     // 13
            "设",    // 14
            "为",    // 15
            "正",    // 16
            "实",    // 17
            "数"     // 18
        )
        val tokenizer = FormulaTokenizer(vocab)

        // 模拟预测 Token 序列: <bos> 设 x 为 正 实 数 <eos>
        val tokenIds = listOf(1L, 14L, 7L, 15L, 16L, 17L, 18L, 2L)
        val result = tokenizer.decode(tokenIds)

        assertEquals("设 x 为 正 实 数", result)
    }
}
