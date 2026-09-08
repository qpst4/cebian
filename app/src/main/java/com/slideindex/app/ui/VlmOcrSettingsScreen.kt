package com.slideindex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.ocr.vlm.VlmFormulaOcrEngine
import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VlmOcrSettingsScreen(
    vlmConfigManager: VlmOcrConfigManager,
    providerId: String? = null,
    onBack: () -> Unit,
    onUpdateVlmConfig: (
        apiKey: String,
        baseUrl: String,
        model: String,
        promptEnabled: Boolean,
        promptDraft: String,
    ) -> Unit,
    onUpdateProviderConfig: ((
        provider: com.slideindex.app.ocr.vlm.VlmProvider,
        apiKey: String,
        baseUrl: String,
        model: String,
        promptEnabled: Boolean,
        promptDraft: String,
    ) -> Unit)? = null,
    onNotifyWarning: ((String) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val targetProvider = remember(providerId) {
        com.slideindex.app.ocr.vlm.VlmProvider.fromId(providerId ?: vlmConfigManager.activeProviderId)
    }

    var apiKey by remember(targetProvider) { mutableStateOf(vlmConfigManager.getApiKey(targetProvider)) }
    var baseUrl by remember(targetProvider) { mutableStateOf(vlmConfigManager.getBaseUrl(targetProvider)) }
    var model by remember(targetProvider) { mutableStateOf(vlmConfigManager.getModel(targetProvider)) }
    var useCustomPrompt by remember(targetProvider) {
        mutableStateOf(vlmConfigManager.isProviderPromptEnabled(targetProvider))
    }
    var customPrompt by remember(targetProvider) {
        mutableStateOf(vlmConfigManager.getProviderPromptDraft(targetProvider))
    }
    val savedCustomModels = remember(targetProvider) {
        vlmConfigManager.getCustomModels(targetProvider)
            .filter { it !in targetProvider.presetModels }
            .sorted()
    }

    var isTesting by remember { mutableStateOf(false) }
    var testStatusText by remember { mutableStateOf<String?>(null) }
    var lastTestSucceeded by remember { mutableStateOf<Boolean?>(null) }

    val onSaveAndBack = {
        if (lastTestSucceeded == false) {
            onNotifyWarning?.invoke("连接测试未通过，配置已保存，请检查接口设置")
        }
        val promptDraft = customPrompt.trim()
        if (onUpdateProviderConfig != null) {
            onUpdateProviderConfig(
                targetProvider,
                apiKey.trim(),
                baseUrl.trim(),
                model.trim(),
                useCustomPrompt,
                promptDraft,
            )
        } else {
            vlmConfigManager.setApiKey(targetProvider, apiKey.trim())
            vlmConfigManager.setBaseUrl(targetProvider, baseUrl.trim())
            vlmConfigManager.setModel(targetProvider, model.trim())
            vlmConfigManager.setProviderPromptConfig(targetProvider, useCustomPrompt, promptDraft)
            vlmConfigManager.setActiveProvider(targetProvider)
            onUpdateVlmConfig(apiKey.trim(), baseUrl.trim(), model.trim(), useCustomPrompt, promptDraft)
        }
        onBack()
    }

    SettingsScreenScaffold(
        title = "${targetProvider.displayName} 配置",
        subtitle = targetProvider.description,
        onBack = onSaveAndBack,
        actions = {
            IconButton(onClick = onSaveAndBack) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "保存",
                )
            }
        },
    ) {
        settingsLazySmallTitle(key = "section-api", title = "接口连接", sectionTop = false)

        LazySettingsItem(key = "api-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = targetProvider.websiteHint,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )

                    MiuixLabeledTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            testStatusText = null
                            lastTestSucceeded = null
                        },
                        label = "API Key (密钥)",
                        singleLine = true,
                    )

                    MiuixLabeledTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            testStatusText = null
                            lastTestSucceeded = null
                        },
                        label = "Base URL (端点地址)",
                        singleLine = true,
                    )
                }
            }
        }

        settingsLazyHint(
            key = "api-hint",
            text = "默认端点为：${targetProvider.defaultBaseUrl}",
        )

        settingsLazySmallTitle(key = "section-model", title = "模型选择与测试", sectionTop = true)

        LazySettingsItem(key = "model-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixLabeledTextField(
                        value = model,
                        onValueChange = {
                            model = it
                            testStatusText = null
                            lastTestSucceeded = null
                        },
                        label = "Model 模型名称",
                        singleLine = true,
                    )

                    Text(
                        text = "快捷选择该服务商推荐型号：",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        targetProvider.presetModels.forEach { presetModelName ->
                            val isSelected = model.trim() == presetModelName
                            Button(
                                onClick = {
                                    model = presetModelName
                                    testStatusText = null
                                    lastTestSucceeded = null
                                },
                                colors = if (isSelected) {
                                    ButtonDefaults.buttonColorsPrimary()
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                            ) {
                                Text(presetModelName, style = MiuixTheme.textStyles.body2)
                            }
                        }
                    }

                    if (savedCustomModels.isNotEmpty()) {
                        Text(
                            text = "已保存的自定义型号：",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            savedCustomModels.forEach { customModelName ->
                                val isSelected = model.trim() == customModelName
                                Button(
                                    onClick = {
                                        model = customModelName
                                        testStatusText = null
                                        lastTestSucceeded = null
                                    },
                                    colors = if (isSelected) {
                                        ButtonDefaults.buttonColorsPrimary()
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    },
                                ) {
                                    Text(customModelName, style = MiuixTheme.textStyles.body2)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                isTesting = true
                                testStatusText = "正在测试连接..."
                                coroutineScope.launch {
                                    val (success, msg) = VlmFormulaOcrEngine.testConnection(
                                        apiKey = apiKey.trim(),
                                        baseUrl = baseUrl.trim(),
                                        model = model.trim(),
                                    )
                                    testStatusText = msg
                                    lastTestSucceeded = success
                                    isTesting = false
                                }
                            },
                            enabled = !isTesting && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("测试连接")
                        }

                        testStatusText?.let { status ->
                            Text(
                                text = status,
                                style = MiuixTheme.textStyles.body2,
                                color = if (status.startsWith("连接成功")) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.error
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        settingsLazySmallTitle(key = "section-prompt", title = "识别提示词 (System Prompt)", sectionTop = true)

        LazySettingsItem(key = "prompt-card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Column {
                    SwitchPreference(
                        title = "自定义专属提示词",
                        summary = if (useCustomPrompt) {
                            "已启用专属覆盖，优先使用下方自定义内容"
                        } else {
                            "已暂停专属覆盖，草稿仍保留在本地"
                        },
                        checked = useCustomPrompt,
                        onCheckedChange = { useCustomPrompt = it },
                    )

                    AnimatedVisibility(
                        visible = useCustomPrompt,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MiuixLabeledTextField(
                                value = customPrompt,
                                onValueChange = { customPrompt = it },
                                label = "专属 System Prompt",
                                singleLine = false,
                                minLines = 5,
                                maxLines = 12,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    text = "载入通用模板",
                                    onClick = {
                                        customPrompt = vlmConfigManager.commonPrompt
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        settingsLazyHint(
            key = "prompt-hint",
            text = "关闭开关时暂停专属覆盖；开启后可为 ${targetProvider.displayName} 独立微调。只有点击「载入通用模板」才会写入通用内容。",
        )
    }
}
