package com.slideindex.app.xposed.bridge

import com.slideindex.app.otp.SmsBlacklistRuleSet
import com.slideindex.app.otp.SmsBlacklistRuleSetCodec
import org.json.JSONObject

/**
 * 下发到电话进程的验证码短信策略（配置快照的 `otp` 段）。
 *
 * 与 system_server 侧的能力（手势接管 / 剪贴板白名单）无关：这些策略只在 `com.android.phone`
 * 与 `com.android.providers.telephony` 里执行，因此由电话进程自己的配置读取器消费。
 *
 * 语义默认值一律"放行"：快照缺失（模块先于 App 启动、或读到旧版快照）时不能因为读不到策略
 * 就停止短信转发，所以 [captureEnabled] 默认 true。
 */
data class ModuleHookOtpPolicy(
    val captureEnabled: Boolean = true,
    val blockCodeSmsEnabled: Boolean = false,
    val markAsReadEnabled: Boolean = false,
    val deleteSmsEnabled: Boolean = false,
    val keywordsRegex: String = "",
    val blacklist: SmsBlacklistRuleSet = SmsBlacklistRuleSet.DISABLED,
) {
    fun toJson(): JSONObject = JSONObject()
        .put(KEY_CAPTURE, captureEnabled)
        .put(KEY_BLOCK, blockCodeSmsEnabled)
        .put(KEY_MARK_READ, markAsReadEnabled)
        .put(KEY_DELETE, deleteSmsEnabled)
        .put(KEY_KEYWORDS, keywordsRegex)
        .put(KEY_BLACKLIST, SmsBlacklistRuleSetCodec.encode(blacklist))

    companion object {
        private const val KEY_CAPTURE = "capture"
        private const val KEY_BLOCK = "block"
        private const val KEY_MARK_READ = "mark_read"
        private const val KEY_DELETE = "delete"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_BLACKLIST = "blacklist"

        fun fromJson(json: JSONObject?): ModuleHookOtpPolicy {
            if (json == null) return ModuleHookOtpPolicy()
            return ModuleHookOtpPolicy(
                captureEnabled = json.optBoolean(KEY_CAPTURE, true),
                blockCodeSmsEnabled = json.optBoolean(KEY_BLOCK, false),
                markAsReadEnabled = json.optBoolean(KEY_MARK_READ, false),
                deleteSmsEnabled = json.optBoolean(KEY_DELETE, false),
                keywordsRegex = json.optString(KEY_KEYWORDS).orEmpty(),
                blacklist = SmsBlacklistRuleSetCodec.decode(json.optString(KEY_BLACKLIST)),
            )
        }
    }
}
