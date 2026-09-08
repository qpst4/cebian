package com.slideindex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.nativeengine.NativeEnginePackVersionState
import com.slideindex.app.ocr.OcrModelDownloadPhase
import com.slideindex.app.ocr.OcrModelDownloadState
import com.slideindex.app.ocr.OcrModelDownloadStep
import com.slideindex.app.ocr.OcrModelEntry
import com.slideindex.app.ocr.vlm.VlmFormulaOcrEngine
import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.ocr.vlm.VlmProvider
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrModelSettingsScreen(
    settings: AppSettings,
    catalogModels: List<OcrModelEntry>,
    installedModelIds: Set<String>,
    downloadState: OcrModelDownloadState?,
    ocrEngineInstalled: Boolean,
    ocrEngineSizeBytes: Long,
    ocrEngineVersionState: NativeEnginePackVersionState? = null,
    onBack: () -> Unit,
    onSelectModel: (String) -> Unit,
    onClearSelectedModel: () -> Unit,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onDeleteOcrEngine: () -> Unit,
    onOpenEngineManagement: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    vlmConfigManager: VlmOcrConfigManager? = null,
    onNavigateToVlmSettings: (providerId: String?) -> Unit = {},
    onSelectProviderAndModel: (provider: VlmProvider, model: String) -> Unit = { _, _ -> },
) {
    val clearSelectionText = stringResource(R.string.ocr_models_clear_selection)
    val localSectionTitle = stringResource(R.string.ocr_models_section_available)
    val localHint = stringResource(R.string.ocr_models_hint)
    val cloudSectionTitle = stringResource(R.string.ocr_models_section_cloud)
    val cloudHint = stringResource(R.string.ocr_models_cloud_hint)
    val isVlmSelected = settings.floatBallOcrModelId == "vlm-formula-qwen"

    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(if (isVlmSelected) 1 else 0)
    }

    var showPromptDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember(showPromptDialog) {
        mutableStateOf(vlmConfigManager?.commonPrompt ?: VlmFormulaOcrEngine.DEFAULT_SYSTEM_PROMPT)
    }

    val tabs = listOf(
        stringResource(R.string.ocr_tab_local),
        stringResource(R.string.ocr_tab_cloud),
    )

    val localModels = remember(catalogModels) {
        catalogModels.filter { it.engine != "vlm_formula" && it.id != "vlm-formula-qwen" }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.ocr_models_title),
        subtitle = stringResource(R.string.ocr_models_subtitle),
        onBack = onBack,
    ) {
        LazySettingsItem(key = "ocr-tab-row") {
            MiuixTabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                contentHorizontalPadding = 12.dp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        if (selectedTabIndex == 0) {
            // ==================== 本地 (离线) TAB ====================
            LazySettingsItem(key = "ocr-download-header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 合并为标准的 Miuix 分组卡片：下载网络控制 + 引擎状态
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        Column {
                            SwitchPreference(
                                title = stringResource(R.string.ocr_download_wifi_only),
                                summary = stringResource(R.string.ocr_download_wifi_only_desc),
                                checked = settings.ocrDownloadWifiOnly,
                                enabled = true,
                                onCheckedChange = onWifiOnlyChange,
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEngineManagement() }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.native_engine_pack_ocr),
                                            style = MiuixTheme.textStyles.title4,
                                        )
                                        val statusText = if (ocrEngineInstalled) "已就绪" else "未安装"
                                        val statusColor = if (ocrEngineInstalled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error
                                        Text(
                                            text = statusText,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = statusColor,
                                            modifier = Modifier
                                                .background(
                                                    color = statusColor.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val revisionStr = ocrEngineVersionState?.installedRevision?.let { " (v$it)" } ?: ""
                                    Text(
                                        text = if (ocrEngineInstalled) {
                                            "底层 C++ 推理库 · ${formatMegabytes(ocrEngineSizeBytes)}$revisionStr"
                                        } else {
                                            "未安装底层运行库 (${formatMegabytes(ocrEngineSizeBytes)})，离线识别不可用"
                                        },
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    )
                                }
                                Button(
                                    onClick = onOpenEngineManagement,
                                    colors = if (ocrEngineInstalled) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text(if (ocrEngineInstalled) "管理" else "安装")
                                }
                            }
                        }
                    }

                    downloadState?.let { state ->
                        if (state.phase != OcrModelDownloadPhase.READY && state.modelId != "vlm-formula-qwen") {
                            OcrModelDownloadProgressCard(state = state)
                        }
                    }
                }
            }

            settingsLazySmallTitle(
                key = "ocr-local-models-section",
                title = localSectionTitle,
                sectionTop = true,
            )

            groupedCardItems(
                keyPrefix = "ocr-local-models",
                items = localModels.map { model ->
                    CardItem("local-model-${model.id}") {
                        OcrModelRow(
                            model = model,
                            installed = model.id in installedModelIds,
                            selected = settings.floatBallOcrModelId == model.id,
                            downloading = downloadState?.modelId == model.id &&
                                downloadState.phase != OcrModelDownloadPhase.READY &&
                                downloadState.phase != OcrModelDownloadPhase.FAILED &&
                                downloadState.phase != OcrModelDownloadPhase.CANCELLED,
                            onSelect = { onSelectModel(model.id) },
                            onDownload = { onDownloadModel(model.id) },
                            onDelete = { onDeleteModel(model.id) },
                        )
                    }
                },
            )

            if (settings.floatBallOcrModelId.isNotBlank() && !isVlmSelected) {
                LazySettingsItem(key = "ocr-local-clear-selection") {
                    TextButton(
                        text = clearSelectionText,
                        onClick = onClearSelectedModel,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            settingsLazyHint(
                key = "ocr-local-models-hint",
                text = localHint,
            )
        } else {
            // ==================== 云端 (多厂商大模型) TAB ====================
            settingsLazySmallTitle(
                key = "ocr-cloud-providers-section",
                title = cloudSectionTitle,
                sectionTop = false,
            )

            groupedCardItems(
                keyPrefix = "ocr-cloud-providers",
                items = VlmProvider.entries.map { provider ->
                    val isProviderConfigured = vlmConfigManager?.isProviderConfigured(provider) == true
                    val isProviderActive = isVlmSelected && vlmConfigManager?.activeProviderId == provider.id
                    val currentProviderModel = vlmConfigManager?.getModel(provider) ?: provider.defaultModel
                    val isCustomPromptEnabled = vlmConfigManager?.isProviderPromptEnabled(provider) == true

                    CardItem("cloud-provider-${provider.id}") {
                        OcrCloudProviderRow(
                            provider = provider,
                            isConfigured = isProviderConfigured,
                            isActive = isProviderActive,
                            currentModel = currentProviderModel,
                            isCustomPromptEnabled = isCustomPromptEnabled,
                            onSelect = { onSelectProviderAndModel(provider, currentProviderModel) },
                            onOpenSettings = { onNavigateToVlmSettings(provider.id) },
                        )
                    }
                },
            )

            if (isVlmSelected) {
                LazySettingsItem(key = "ocr-cloud-clear-selection") {
                    TextButton(
                        text = clearSelectionText,
                        onClick = onClearSelectedModel,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            settingsLazySmallTitle(
                key = "ocr-cloud-prompt-section",
                title = "通用配置",
                sectionTop = true,
            )

            // 通用提示词设置卡片
            LazySettingsItem(key = "ocr-cloud-prompt-card") {
                val commonPromptSummary = if (vlmConfigManager?.isCommonPromptCustomized() == true) {
                    "已自定义全局通用内容"
                } else {
                    "当前使用出厂默认"
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingPrompt = vlmConfigManager?.commonPrompt ?: VlmFormulaOcrEngine.DEFAULT_SYSTEM_PROMPT
                                showPromptDialog = true
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "全局通用提示词 (System Prompt)",
                                style = MiuixTheme.textStyles.title4,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = commonPromptSummary,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                        Button(
                            onClick = {
                                editingPrompt = vlmConfigManager?.commonPrompt ?: VlmFormulaOcrEngine.DEFAULT_SYSTEM_PROMPT
                                showPromptDialog = true
                            },
                        ) {
                            Text("编辑")
                        }
                    }
                }
            }

            settingsLazyHint(
                key = "ocr-cloud-models-hint",
                text = cloudHint,
            )
        }
    }

    if (showPromptDialog) {
        val isDefault = editingPrompt.trim() == VlmFormulaOcrEngine.DEFAULT_SYSTEM_PROMPT.trim()
        MiuixFormDialog(
            show = true,
            onDismissRequest = { showPromptDialog = false },
            title = "全局通用提示词 (System Prompt)",
            confirmText = "保存",
            onConfirm = {
                vlmConfigManager?.commonPrompt = editingPrompt.trim()
            },
            dismissText = "取消",
            secondaryConfirmText = "恢复默认",
            onSecondaryConfirm = {
                editingPrompt = VlmFormulaOcrEngine.DEFAULT_SYSTEM_PROMPT
            },
            secondaryConfirmEnabled = !isDefault,
            secondaryDismissOnConfirm = false,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixLabeledTextField(
                    value = editingPrompt,
                    onValueChange = { editingPrompt = it },
                    label = "全局通用 System Prompt",
                    singleLine = false,
                    minLines = 6,
                    maxLines = 14,
                )
            }
        }
    }
}

@Composable
private fun OcrModelDownloadProgressCard(state: OcrModelDownloadState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val fraction = state.progress
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = ocrDownloadProgressLabel(state),
                style = MiuixTheme.textStyles.body1,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OcrModelRow(
    model: OcrModelEntry,
    installed: Boolean,
    selected: Boolean,
    downloading: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowClickableModifier = if (installed) {
        Modifier.clickable { onSelect() }
    } else if (!downloading) {
        Modifier.clickable { onDownload() }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowClickableModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = installed,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ocrModelDisplayName(model.id),
                style = MiuixTheme.textStyles.title4,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.ocr_model_meta,
                    formatMegabytes(model.totalDownloadBytes),
                    ocrModelDisplayDescription(model.id),
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    selected -> stringResource(R.string.ocr_model_status_selected)
                    installed -> stringResource(R.string.ocr_model_status_installed)
                    downloading -> stringResource(R.string.ocr_model_status_downloading)
                    else -> stringResource(R.string.ocr_model_status_not_installed)
                },
                style = MiuixTheme.textStyles.body2,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        if (installed) {
            Button(
                onClick = onDelete,
            ) {
                Text(stringResource(R.string.ocr_model_delete))
            }
        } else {
            Button(
                onClick = onDownload,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.ocr_model_download))
            }
        }
    }
}

@Composable
private fun OcrCloudProviderRow(
    provider: VlmProvider,
    isConfigured: Boolean,
    isActive: Boolean,
    currentModel: String,
    isCustomPromptEnabled: Boolean,
    onSelect: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val rowClickableModifier = if (isConfigured) {
        Modifier.clickable { onSelect() }
    } else {
        Modifier.clickable { onOpenSettings() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowClickableModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = isActive,
            onClick = null,
            enabled = isConfigured,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = provider.displayName,
                    style = MiuixTheme.textStyles.title4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val (badgeText, badgeColor) = when {
                    isActive -> "使用中" to MiuixTheme.colorScheme.primary
                    isConfigured -> "已就绪" to MiuixTheme.colorScheme.primary
                    else -> "未配置" to MiuixTheme.colorScheme.error
                }
                Text(
                    text = badgeText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = badgeColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .background(
                            color = badgeColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    isConfigured && isCustomPromptEnabled -> "当前模型: $currentModel · 专属提示词"
                    isConfigured -> "当前模型: $currentModel · 继承全局"
                    else -> provider.description
                },
                style = MiuixTheme.textStyles.body2,
                color = if (isActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isConfigured) {
            Button(
                onClick = onOpenSettings,
            ) {
                Text("设置")
            }
        } else {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("配置")
            }
        }
    }
}

@Composable
private fun ocrModelDisplayName(modelId: String): String = when (modelId) {
    "mlkit-chinese" -> stringResource(R.string.ocr_model_mlkit_chinese)
    "tesseract-chi-sim-eng" -> stringResource(R.string.ocr_model_tesseract_chi_sim_eng)
    "ppocrv6-tiny" -> stringResource(R.string.ocr_model_ppocrv6_tiny)
    "ppocrv6-small" -> stringResource(R.string.ocr_model_ppocrv6_small)
    "ppocrv6-medium" -> stringResource(R.string.ocr_model_ppocrv6_medium)
    else -> modelId
}

@Composable
private fun ocrModelDisplayDescription(modelId: String): String = when (modelId) {
    "mlkit-chinese" -> stringResource(R.string.ocr_model_mlkit_chinese_desc)
    "tesseract-chi-sim-eng" -> stringResource(R.string.ocr_model_tesseract_chi_sim_eng_desc)
    "ppocrv6-tiny" -> stringResource(R.string.ocr_model_ppocrv6_tiny_desc)
    "ppocrv6-small" -> stringResource(R.string.ocr_model_ppocrv6_small_desc)
    "ppocrv6-medium" -> stringResource(R.string.ocr_model_ppocrv6_medium_desc)
    else -> ""
}

@Composable
private fun ocrDownloadProgressLabel(state: OcrModelDownloadState): String {
    val modelName = ocrModelDisplayName(state.modelId)
    val stepPrefix = when (state.step) {
        OcrModelDownloadStep.ENGINE ->
            stringResource(R.string.ocr_download_step_engine, state.stepIndex, state.stepCount)
        OcrModelDownloadStep.MODEL ->
            stringResource(R.string.ocr_download_step_model, state.stepIndex, state.stepCount)
    }
    return when (state.phase) {
        OcrModelDownloadPhase.DOWNLOADING -> {
            val downloaded = formatMegabytes(state.bytesDownloaded)
            val total = state.totalBytes?.let(::formatMegabytes)
            val detail = if (total != null) {
                stringResource(R.string.ocr_download_progress_bytes, modelName, downloaded, total)
            } else {
                stringResource(R.string.ocr_download_progress_indeterminate, modelName, downloaded)
            }
            if (state.stepCount > 1) "$stepPrefix $detail" else detail
        }
        OcrModelDownloadPhase.VERIFYING ->
            stringResource(R.string.ocr_download_verifying, modelName)
        OcrModelDownloadPhase.FINALIZING ->
            stringResource(R.string.ocr_download_finalizing, modelName)
        OcrModelDownloadPhase.FAILED ->
            stringResource(R.string.ocr_download_failed, state.errorMessage.orEmpty())
        OcrModelDownloadPhase.CANCELLED ->
            stringResource(R.string.ocr_download_cancelled)
        OcrModelDownloadPhase.READY ->
            stringResource(R.string.ocr_download_ready, modelName)
    }
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb < 10.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        "${mb.roundToInt()} MB"
    }
}

