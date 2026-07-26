package com.slideindex.app.otp

/*
 * Portions derived from XposedSmsCode (https://github.com/tianma8023/XposedSmsCode)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

object OtpKeywords {
    const val LEGACY_DEFAULT_KEYWORDS_REGEX =
        "(?i)\\b(验证码|otp|verification|verify|pin|passcode|password|code|secure\\s*code)\\b"

    const val DEFAULT_KEYWORDS_REGEX =
        "验证码|校验码|检验码|确认码|激活码|动态码|安全码|验证代码|校验代码|检验代码|激活代码|确认代码|动态代码|安全代码|登入码|认证码|识别码|短信口令|动态密码|交易码|上网密码|随机码|动态口令|驗證碼|校驗碼|檢驗碼|確認碼|激活碼|動態碼|驗證代碼|校驗代碼|檢驗代碼|確認代碼|激活代碼|動態代碼|登入碼|認證碼|識別碼|(?i)\\b(otp|verification|verify|pin|passcode|password|code|secure\\s*code)\\b|Код|код|КОД|Пароль|пароль|ПАРОЛЬ|Kod|kod|KOD|(?i)\\b(ma|mã|otp)\\b"
}
