package com.slideindex.app.otp

/**
 * 从一条真实的验证码样例里反推规则（触发词 + 正则）。
 *
 * 目的：用户不必理解"触发词/正则"，只要贴一条样例，就能得到一条已验证可用的规则。
 * 生成的规则会**自校验**（用该规则重新提取样例，必须得到同一个验证码），不合格就返回 null，
 * 由界面提示用户手动填写。
 */
object OtpRuleInference {
    /** 常见触发词，长词优先（避免"码"先命中"验证码"）。 */
    private val KEYWORD_CANDIDATES = listOf(
        "验证码", "校验码", "检验码", "确认码", "激活码", "动态码", "安全码", "动态密码",
        "验证代码", "校验代码", "检验代码", "激活代码", "确认代码", "动态代码", "安全代码",
        "登入码", "认证码", "识别码", "短信口令", "交易码", "上网密码", "随机码", "动态口令",
        "驗證碼", "校驗碼", "檢驗碼", "確認碼", "激活碼", "動態碼", "驗證代碼", "校驗代碼",
        "檢驗代碼", "確認代碼", "激活代碼", "動態代碼", "登入碼", "認證碼", "識別碼",
        "verification code", "security code", "one-time code", "verify", "pin", "code", "otp", "password",
    )

    data class Result(
        val keyword: String,
        val regex: String,
        val code: String,
    )

    fun infer(sampleText: String, keywordsRegex: String = OtpKeywords.DEFAULT_KEYWORDS_REGEX): Result? {
        val text = sampleText.trim()
        if (text.isEmpty()) return null
        val code = VerificationCodeExtractor.extract(
            packageName = "",
            title = "",
            text = text,
            config = OtpExtractionConfig(keywordsRegex = keywordsRegex, matchRules = emptyList()),
        ).code ?: return null
        val codeIndex = text.indexOf(code)
        if (codeIndex < 0) return null
        val keyword = findKeyword(text, codeIndex, code) ?: return null
        val regex = buildRegex(keyword, code.length)
        return if (ruleReproducesCode(text, keyword, regex, code)) {
            Result(keyword = keyword, regex = regex, code = code)
        } else {
            null
        }
    }

    /** 取"离验证码最近"的触发词，并使用样例里的原始大小写。 */
    private fun findKeyword(text: String, codeIndex: Int, code: String): String? {
        var bestValue: String? = null
        var bestDistance = Int.MAX_VALUE
        KEYWORD_CANDIDATES.forEach { candidate ->
            val pattern = Regex(Regex.escape(candidate), RegexOption.IGNORE_CASE)
            pattern.findAll(text).forEach { match ->
                // 优先用码之前的触发词；码之后的给一个很大的距离，仅在没有任何前置触发词时兜底。
                val distance = if (match.range.last < codeIndex) {
                    codeIndex - match.range.last
                } else {
                    (match.range.first - codeIndex) + text.length + code.length
                }
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestValue = match.value
                }
            }
        }
        return bestValue
    }

    /** 触发词 + 最多 12 个非数字字符 + 与样例同长度的数字组。 */
    private fun buildRegex(keyword: String, codeLength: Int): String =
        "${Regex.escape(keyword)}\\D{0,12}(\\d{$codeLength})"

    private fun ruleReproducesCode(text: String, keyword: String, regex: String, code: String): Boolean {
        val rule = OtpMatchRule(name = "probe", keyword = keyword, regex = regex)
        val extracted = VerificationCodeExtractor.extract(
            packageName = "",
            title = "",
            text = text,
            config = OtpExtractionConfig(keywordsRegex = "", matchRules = listOf(rule)),
        ).code
        return extracted == code
    }
}
