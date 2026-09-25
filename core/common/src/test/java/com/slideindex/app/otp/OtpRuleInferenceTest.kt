package com.slideindex.app.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OtpRuleInferenceTest {
    @Test
    fun infersRuleFromChineseSample() {
        val result = OtpRuleInference.infer("【某银行】您的验证码是 384712，5分钟内有效。")
        assertNotNull(result)
        assertEquals("验证码", result!!.keyword)
        assertEquals("384712", result.code)
    }

    @Test
    fun infersRuleWithFullWidthColon() {
        val result = OtpRuleInference.infer("验证码：9527，请勿泄露")
        assertNotNull(result)
        assertEquals("验证码", result!!.keyword)
        assertEquals("9527", result.code)
    }

    @Test
    fun keepsSampleCasingForLatinKeyword() {
        val result = OtpRuleInference.infer("Your CODE is 402133, valid for 5 minutes.")
        assertNotNull(result)
        assertEquals("CODE", result!!.keyword)
        assertEquals("402133", result.code)
    }

    @Test
    fun returnsNullWhenNoCodePresent() {
        assertNull(OtpRuleInference.infer("您好，您的快递已到达。"))
        assertNull(OtpRuleInference.infer(""))
    }

    @Test
    fun returnsNullWhenCodeTooFarFromKeyword() {
        // 触发词与数字相距超过兜底词的邻近窗口（30 字符）时推断不出来，交给用户手填。
        val farApart = "验证码 " + "x".repeat(40) + " 384712"
        assertNull(OtpRuleInference.infer(farApart))
    }
}
