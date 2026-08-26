package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.otp.OtpExtractionConfig
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.otp.VerificationCodeExtractor
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardItems
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.viewmodel.OtpSettingsViewModel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Recent

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberOtpCopyToClipboardCard(
    copyToClipboard: Boolean,
    onCopyToClipboardChange: (Boolean) -> Unit,
): SettingsCardItems = settingsCardItems {
    SettingSwitchRow(
        title = stringResource(R.string.otp_copy_to_clipboard_title),
        subtitle = stringResource(R.string.otp_copy_to_clipboard_desc),
        icon = { label -> Icon(MiuixIcons.Copy, contentDescription = label) },
        checked = copyToClipboard,
        enabled = true,
        onCheckedChange = onCopyToClipboardChange,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberOtpKeywordsEditorCard(
    keywordsText: String,
    onKeywordsTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
): SettingsCardItems = settingsCardItems {
    MiuixLabeledTextField(
        value = keywordsText,
        onValueChange = onKeywordsTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        label = stringResource(R.string.otp_keywords_regex_label),
        singleLine = false,
        minLines = 2,
        maxLines = 6,
    )
    Text(
        text = stringResource(R.string.otp_keywords_regex_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    SettingLinkRow(
        title = stringResource(R.string.otp_keywords_save),
        subtitle = stringResource(R.string.otp_keywords_save_desc),
        onClick = onSave,
    )
    SettingLinkRow(
        title = stringResource(R.string.otp_keywords_reset),
        subtitle = stringResource(R.string.otp_keywords_reset_desc),
        onClick = onReset,
    )
}

fun LazyListScope.emitOtpKeywordsEditorSection(
    sectionTitle: String,
    fallbackHint: String,
    keywordsText: String,
    onKeywordsTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    sectionTop: Boolean = false,
) {
    settingsLazySmallTitle(key = "otp_keywords_section", title = sectionTitle, sectionTop = sectionTop)
    settingsLazyHint(key = "otp_keywords_fallback_hint", text = fallbackHint)
    groupedCardItems(
        keyPrefix = "otp_keywords",
        items = buildList {
            add(
                settingsCardScopeItem("field") {
                    MiuixLabeledTextField(
                        value = keywordsText,
                        onValueChange = onKeywordsTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        label = stringResource(R.string.otp_keywords_regex_label),
                        singleLine = false,
                        minLines = 2,
                        maxLines = 6,
                    )
                },
            )
            add(
                settingsCardScopeItem("hint") {
                    Text(
                        text = stringResource(R.string.otp_keywords_regex_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                },
            )
            add(
                settingsCardScopeItem("save") {
                    SettingLinkRow(
                        title = stringResource(R.string.otp_keywords_save),
                        subtitle = stringResource(R.string.otp_keywords_save_desc),
                        onClick = onSave,
                    )
                },
            )
            add(
                settingsCardScopeItem("reset") {
                    SettingLinkRow(
                        title = stringResource(R.string.otp_keywords_reset),
                        subtitle = stringResource(R.string.otp_keywords_reset_desc),
                        onClick = onReset,
                    )
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpKeywordsEditorSection(
    keywordsText: String,
    onKeywordsTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    sectionTitle: String = stringResource(R.string.otp_keywords_section),
) {
    val card = rememberOtpKeywordsEditorCard(
        keywordsText = keywordsText,
        onKeywordsTextChange = onKeywordsTextChange,
        onSave = onSave,
        onReset = onReset,
    )
    val fallbackHint = stringResource(R.string.otp_keywords_fallback_hint)
    Column {
        MiuixSmallTitle(sectionTitle)
        MiuixHintText(fallbackHint)
        card.RenderRows()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberOtpTestLinkCard(onOpenTest: () -> Unit): SettingsCardItems = settingsCardItems {
    SettingLinkRow(
        title = stringResource(R.string.otp_test_title),
        subtitle = stringResource(R.string.otp_test_desc),
        onClick = onOpenTest,
    )
}

fun LazyListScope.emitOtpTestLinkSection(
    sectionTitle: String,
    onOpenTest: () -> Unit,
    sectionTop: Boolean = false,
) {
    settingsLazySmallTitle(key = "otp_test_section", title = sectionTitle, sectionTop = sectionTop)
    groupedCardItems(
        keyPrefix = "otp_test",
        items = listOf(
            settingsCardScopeItem("test-link") {
                SettingLinkRow(
                    title = stringResource(R.string.otp_test_title),
                    subtitle = stringResource(R.string.otp_test_desc),
                    onClick = onOpenTest,
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpTestLinkSection(onOpenTest: () -> Unit) {
    val card = rememberOtpTestLinkCard(onOpenTest)
    Column {
        MiuixSmallTitle(stringResource(R.string.otp_test_section))
        card.RenderRows()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpSettingsScreen(
    settings: AppSettings,
    officialRules: List<OtpMatchRule>,
    onBack: (() -> Unit)?,
    onKeywordsRegexChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAutoInput: (() -> Unit)? = null,
    onOpenMatchRules: (() -> Unit)? = null,
    onOpenRecords: (() -> Unit)? = null,
) {
    var keywordsText by remember(settings.otpKeywordsRegex) { mutableStateOf(settings.otpKeywordsRegex) }
    var showTestDialog by remember { mutableStateOf(false) }

    val moreSectionTitle = stringResource(R.string.otp_hub_section_more)
    val keywordsSectionTitle = stringResource(R.string.otp_keywords_section)
    val keywordsFallbackHint = stringResource(R.string.otp_keywords_fallback_hint)
    val testSectionTitle = stringResource(R.string.otp_test_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.otp_settings_title),
        subtitle = stringResource(R.string.otp_settings_desc),
        onBack = onBack,
        modifier = modifier,
    ) {
        settingsLazySmallTitle(key = "otp_more_section", title = moreSectionTitle)
        groupedCardItems(
            keyPrefix = "otp_more",
            items = buildList {
                onOpenRecords?.let { openRecords ->
                    add(
                        settingsCardScopeItem("records") {
                            SettingNavigationRow(
                                icon = { label -> Icon(MiuixIcons.Recent, contentDescription = label) },
                                title = stringResource(R.string.otp_records_entry_title),
                                subtitle = stringResource(R.string.otp_records_entry_desc),
                                onClick = openRecords,
                            )
                        },
                    )
                }
                onOpenAutoInput?.let { openAutoInput ->
                    add(
                        settingsCardScopeItem("auto-input") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.Outlined.TouchApp, contentDescription = label) },
                                title = stringResource(R.string.otp_auto_input_entry_title),
                                subtitle = stringResource(R.string.otp_auto_input_entry_desc),
                                onClick = openAutoInput,
                            )
                        },
                    )
                }
                onOpenMatchRules?.let { openMatchRules ->
                    add(
                        settingsCardScopeItem("match-rules") {
                            SettingNavigationRow(
                                icon = { label -> Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = label) },
                                title = stringResource(R.string.otp_match_rules_entry_title),
                                subtitle = stringResource(R.string.otp_match_rules_entry_desc),
                                onClick = openMatchRules,
                            )
                        },
                    )
                }
            },
        )

        emitOtpKeywordsEditorSection(
            sectionTitle = keywordsSectionTitle,
            fallbackHint = keywordsFallbackHint,
            keywordsText = keywordsText,
            onKeywordsTextChange = { keywordsText = it },
            onSave = { onKeywordsRegexChange(keywordsText) },
            onReset = {
                keywordsText = VerificationCodeExtractor.DEFAULT_KEYWORDS_REGEX
                onKeywordsRegexChange(keywordsText)
            },
            sectionTop = true,
        )

        emitOtpTestLinkSection(
            sectionTitle = testSectionTitle,
            onOpenTest = { showTestDialog = true },
            sectionTop = true,
        )
    }

    if (showTestDialog) {
        OtpTestDialogHost(
            settings = settings,
            officialRules = officialRules,
            keywordsRegex = keywordsText,
            onDismiss = { showTestDialog = false },
        )
    }
}

@Composable
fun OtpTestDialogHost(
    settings: AppSettings,
    officialRules: List<OtpMatchRule>,
    keywordsRegex: String = settings.otpKeywordsRegex,
    onDismiss: () -> Unit,
    viewModel: OtpSettingsViewModel = hiltViewModel(),
) {
    val extractionConfig = remember(settings, officialRules, keywordsRegex) {
        OtpExtractionConfig.build(
            keywordsRegex = keywordsRegex,
            officialRules = officialRules,
            userRules = settings.otpUserMatchRules,
            disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
        )
    }
    OtpTestDialog(
        config = extractionConfig,
        onDismiss = onDismiss,
        onRecord = { code, sampleText, ruleName ->
            viewModel.recordTestOtp(code, sampleText, ruleName)
        },
    )
}

@Composable
private fun OtpTestDialog(
    config: OtpExtractionConfig,
    onDismiss: () -> Unit,
    onRecord: (code: String, sampleText: String, ruleName: String?) -> Unit = { _, _, _ -> },
) {
    var sampleText by remember { mutableStateOf("") }
    val result = remember(sampleText, config) {
        if (sampleText.isBlank()) {
            null
        } else {
            VerificationCodeExtractor.extract(
                packageName = "com.test.sms",
                title = "",
                text = sampleText,
                config = config,
            )
        }
    }

    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.otp_test_title),
        confirmText = stringResource(R.string.shell_panel_close),
        dismissText = null,
        onConfirm = {
            result?.code?.let { code ->
                onRecord(code, sampleText, result.ruleName)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MiuixLabeledTextField(
                value = sampleText,
                onValueChange = { sampleText = it },
                label = stringResource(R.string.otp_test_input_label),
                singleLine = false,
                minLines = 4,
                maxLines = 8,
            )
            val extractedCode = result?.code
            when {
                sampleText.isBlank() -> Unit
                extractedCode != null -> {
                    Text(
                        text = stringResource(R.string.otp_test_result_success, extractedCode),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                result?.attempted == true -> {
                    Text(
                        text = stringResource(R.string.otp_test_result_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCardScope.OtpHubEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label -> Icon(HubLeadingIcons.otpHub(outlinedLeadingIcons), contentDescription = label) },
        title = stringResource(R.string.otp_hub_entry_title),
        subtitle = stringResource(R.string.otp_hub_entry_desc),
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.OtpSettingsEntryCard(onClick: () -> Unit) {
    OtpHubEntryCard(onClick = onClick)
}
