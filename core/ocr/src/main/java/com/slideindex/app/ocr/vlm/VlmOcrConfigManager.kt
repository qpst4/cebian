package com.slideindex.app.ocr.vlm

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.slideindex.app.ocr.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云端视觉大模型服务商定义
 */
enum class VlmProvider(
    val id: String,
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val presetModels: List<String>,
    @StringRes val websiteHintRes: Int,
) {
    DASHSCOPE(
        id = "dashscope",
        displayNameRes = R.string.vlm_provider_dashscope_name,
        descriptionRes = R.string.vlm_provider_dashscope_desc,
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-vl-plus",
        presetModels = listOf("qwen-vl-plus", "qwen-vl-max", "qwen-vl-ocr"),
        websiteHintRes = R.string.vlm_provider_dashscope_hint,
    ),
    ZHIPU(
        id = "zhipu",
        displayNameRes = R.string.vlm_provider_zhipu_name,
        descriptionRes = R.string.vlm_provider_zhipu_desc,
        defaultBaseUrl = "https://open.bigmodel.cn/api/paas/v4/",
        defaultModel = "glm-4v-plus",
        presetModels = listOf("glm-4v-plus", "glm-4v", "glm-4v-flash"),
        websiteHintRes = R.string.vlm_provider_zhipu_hint,
    ),
    SILICONFLOW(
        id = "siliconflow",
        displayNameRes = R.string.vlm_provider_siliconflow_name,
        descriptionRes = R.string.vlm_provider_siliconflow_desc,
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "Qwen/Qwen2.5-VL-72B-Instruct",
        presetModels = listOf(
            "Qwen/Qwen2.5-VL-72B-Instruct",
            "Qwen/Qwen2-VL-72B-Instruct",
            "Qwen/Qwen2-VL-7B-Instruct",
            "THUDM/glm-4v-9b",
        ),
        websiteHintRes = R.string.vlm_provider_siliconflow_hint,
    ),
    CUSTOM(
        id = "custom",
        displayNameRes = R.string.vlm_provider_custom_name,
        descriptionRes = R.string.vlm_provider_custom_desc,
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        presetModels = listOf("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet"),
        websiteHintRes = R.string.vlm_provider_custom_hint,
    );

    fun displayName(context: Context): String = context.getString(displayNameRes)

    fun description(context: Context): String = context.getString(descriptionRes)

    fun websiteHint(context: Context): String = context.getString(websiteHintRes)

    companion object {
        fun fromId(id: String): VlmProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DASHSCOPE
    }
}

/**
 * 多模态视觉 OCR 引擎配置管理（支持服务商隔离与独立存储）
 */
@Singleton
class VlmOcrConfigManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREF_NAME = "vlm_ocr_config"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_PROMPT = "system_prompt"

        private const val PREFIX_API_KEY = "provider_api_key_"
        private const val PREFIX_BASE_URL = "provider_base_url_"
        private const val PREFIX_MODEL = "provider_model_"
        private const val PREFIX_CUSTOM_MODELS = "provider_custom_models_"
        private const val PREFIX_PROMPT = "provider_prompt_"
        private const val PREFIX_PROMPT_ENABLED = "provider_prompt_enabled_"

        // 遗留旧配置 key（用于平滑迁移）
        private const val LEGACY_KEY_API_KEY = "api_key"
        private const val LEGACY_KEY_BASE_URL = "base_url"
        private const val LEGACY_KEY_MODEL = "model"
        private const val LEGACY_PROMPT_PREFIX = "你是一个专业的学术与数学公式 OCR 识别工具"
    }

    private fun defaultPrompt(): String = VlmFormulaOcrEngine.defaultSystemPrompt(context)

    init {
        migrateLegacyIfNeeded()
        migrateProviderPromptFlagsIfNeeded()
    }

    private fun migrateLegacyIfNeeded() {
        val legacyKey = prefs.getString(LEGACY_KEY_API_KEY, null)
        val dashscopeKey = prefs.getString(PREFIX_API_KEY + VlmProvider.DASHSCOPE.id, null)
        if (!legacyKey.isNullOrBlank() && dashscopeKey.isNullOrBlank()) {
            val legacyUrl = prefs.getString(LEGACY_KEY_BASE_URL, VlmProvider.DASHSCOPE.defaultBaseUrl)
            val legacyModel = prefs.getString(LEGACY_KEY_MODEL, VlmProvider.DASHSCOPE.defaultModel)
            prefs.edit()
                .putString(PREFIX_API_KEY + VlmProvider.DASHSCOPE.id, legacyKey)
                .putString(PREFIX_BASE_URL + VlmProvider.DASHSCOPE.id, legacyUrl)
                .putString(PREFIX_MODEL + VlmProvider.DASHSCOPE.id, legacyModel)
                .apply()
        }
    }

    // --- 当前激活服务商 ---
    var activeProviderId: String
        get() = prefs.getString(KEY_ACTIVE_PROVIDER, VlmProvider.DASHSCOPE.id) ?: VlmProvider.DASHSCOPE.id
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROVIDER, value).apply()

    val activeProvider: VlmProvider
        get() = VlmProvider.fromId(activeProviderId)

    fun setActiveProvider(provider: VlmProvider) {
        activeProviderId = provider.id
    }

    // --- 单个厂商配置存取 ---
    fun getApiKey(provider: VlmProvider): String =
        prefs.getString(PREFIX_API_KEY + provider.id, "") ?: ""

    fun setApiKey(provider: VlmProvider, key: String) {
        prefs.edit().putString(PREFIX_API_KEY + provider.id, key.trim()).apply()
    }

    fun getBaseUrl(provider: VlmProvider): String =
        prefs.getString(PREFIX_BASE_URL + provider.id, provider.defaultBaseUrl)?.ifBlank { null }
            ?: provider.defaultBaseUrl

    fun setBaseUrl(provider: VlmProvider, url: String) {
        prefs.edit().putString(PREFIX_BASE_URL + provider.id, url.trim().ifBlank { provider.defaultBaseUrl }).apply()
    }

    fun getModel(provider: VlmProvider): String =
        prefs.getString(PREFIX_MODEL + provider.id, provider.defaultModel)?.ifBlank { null }
            ?: provider.defaultModel

    fun setModel(provider: VlmProvider, model: String) {
        val trimmed = model.trim().ifBlank { provider.defaultModel }
        prefs.edit().putString(PREFIX_MODEL + provider.id, trimmed).apply()
        if (trimmed !in provider.presetModels) {
            addCustomModel(provider, trimmed)
        }
    }

    fun getCustomModels(provider: VlmProvider): Set<String> =
        prefs.getStringSet(PREFIX_CUSTOM_MODELS + provider.id, emptySet()) ?: emptySet()

    fun addCustomModel(provider: VlmProvider, model: String) {
        val trimmed = model.trim()
        if (trimmed.isNotBlank() && trimmed !in provider.presetModels) {
            val updated = getCustomModels(provider).toMutableSet()
            updated.add(trimmed)
            prefs.edit().putStringSet(PREFIX_CUSTOM_MODELS + provider.id, updated).apply()
        }
    }

    private fun migrateProviderPromptFlagsIfNeeded() {
        VlmProvider.entries.forEach { provider ->
            val enabledKey = PREFIX_PROMPT_ENABLED + provider.id
            if (prefs.contains(enabledKey)) return@forEach
            val hasDraft = !prefs.getString(PREFIX_PROMPT + provider.id, null).isNullOrBlank()
            if (hasDraft) {
                prefs.edit().putBoolean(enabledKey, true).apply()
            }
        }
    }

    // --- 服务商专属提示词：enabled 与 draft 分离存储 ---
    fun isProviderPromptEnabled(provider: VlmProvider): Boolean =
        prefs.getBoolean(PREFIX_PROMPT_ENABLED + provider.id, false)

    fun setProviderPromptEnabled(provider: VlmProvider, enabled: Boolean) {
        prefs.edit().putBoolean(PREFIX_PROMPT_ENABLED + provider.id, enabled).apply()
    }

    fun getProviderPromptDraft(provider: VlmProvider): String =
        prefs.getString(PREFIX_PROMPT + provider.id, null).orEmpty()

    fun setProviderPromptDraft(provider: VlmProvider, draft: String) {
        val trimmed = draft.trim()
        if (trimmed.isBlank()) {
            prefs.edit().remove(PREFIX_PROMPT + provider.id).apply()
        } else {
            prefs.edit().putString(PREFIX_PROMPT + provider.id, trimmed).apply()
        }
    }

    fun setProviderPromptConfig(provider: VlmProvider, enabled: Boolean, draft: String) {
        setProviderPromptEnabled(provider, enabled)
        setProviderPromptDraft(provider, draft)
    }

    /** 兼容旧调用：非空 draft 视为启用专属。 */
    fun getProviderPrompt(provider: VlmProvider): String? =
        getProviderPromptDraft(provider).ifBlank { null }

    /** 兼容旧调用。 */
    fun setProviderPrompt(provider: VlmProvider, customPrompt: String?) {
        if (customPrompt.isNullOrBlank()) {
            setProviderPromptEnabled(provider, false)
        } else {
            setProviderPromptConfig(provider, enabled = true, draft = customPrompt)
        }
    }

    fun getEffectivePrompt(provider: VlmProvider): String {
        if (isProviderPromptEnabled(provider)) {
            getProviderPromptDraft(provider).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return commonPrompt
    }

    fun isCommonPromptCustomized(): Boolean =
        commonPrompt.trim() != defaultPrompt().trim()

    fun isProviderConfigured(provider: VlmProvider): Boolean =
        getApiKey(provider).isNotBlank()

    // --- 向后兼容：直接代理当前激活的服务商 ---
    var apiKey: String
        get() = getApiKey(activeProvider)
        set(value) = setApiKey(activeProvider, value)

    var baseUrl: String
        get() = getBaseUrl(activeProvider)
        set(value) = setBaseUrl(activeProvider, value)

    var model: String
        get() = getModel(activeProvider)
        set(value) = setModel(activeProvider, value)

    val isConfigured: Boolean
        get() = isProviderConfigured(activeProvider)

    // --- 全局通用提示词 Common System Prompt ---
    var commonPrompt: String
        get() {
            val stored = prefs.getString(KEY_PROMPT, null)
            if (stored.isNullOrBlank() || stored.startsWith(LEGACY_PROMPT_PREFIX)) {
                return defaultPrompt()
            }
            return stored
        }
        set(value) = prefs.edit().putString(KEY_PROMPT, value.trim().ifBlank { defaultPrompt() }).apply()

    // --- 当前激活服务商的生效提示词（支持读写兼容） ---
    var prompt: String
        get() = getEffectivePrompt(activeProvider)
        set(value) {
            commonPrompt = value
        }

    fun resetPromptToDefault() {
        commonPrompt = defaultPrompt()
    }
}

