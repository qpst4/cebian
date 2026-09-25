package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.slideindex.app.R
import com.slideindex.app.otp.OtpExtractionConfig
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.otp.VerificationCodeExtractor
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardItems
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.viewmodel.OtpSettingsViewModel
import top.yukonga.miuix.kmp.basic.SmallTitle

/** 规则 Tab 与验证码页共用的段落：关键词编辑、测试入口与测试弹窗、验证码入口卡片。 */

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
