package com.slideindex.app.ui



import com.slideindex.app.ui.miuix.MiuixSmallTitle
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.Rule

import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Password

import com.slideindex.app.ui.miuix.MiuixFormDialog

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.slideindex.app.R


import com.slideindex.app.otp.OtpExtractionConfig

import com.slideindex.app.otp.OtpMatchRule

import com.slideindex.app.otp.VerificationCodeExtractor

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.viewmodel.OtpSettingsViewModel
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope



@OptIn(ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun OtpCopyToClipboardSection(

    copyToClipboard: Boolean,

    onCopyToClipboardChange: (Boolean) -> Unit,

) {

    SettingsCard {

        SettingSwitchRow(

            title = stringResource(R.string.otp_copy_to_clipboard_title),

            subtitle = stringResource(R.string.otp_copy_to_clipboard_desc),

            icon = { label -> Icon(Icons.Outlined.ContentCopy, contentDescription = label) },

            checked = copyToClipboard,

            enabled = true,

            onCheckedChange = onCopyToClipboardChange,

        )

    }

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

    MiuixSmallTitle(sectionTitle)

    SettingsHintText(stringResource(R.string.otp_keywords_fallback_hint))

    SettingsCard {

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

}



@OptIn(ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun OtpTestLinkSection(onOpenTest: () -> Unit) {

    MiuixSmallTitle(stringResource(R.string.otp_test_section))

    SettingsCard {

        SettingLinkRow(

            title = stringResource(R.string.otp_test_title),

            subtitle = stringResource(R.string.otp_test_desc),

            onClick = onOpenTest,

        )

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



    SettingsScreenScaffold(

        title = stringResource(R.string.otp_settings_title),

        subtitle = stringResource(R.string.otp_settings_desc),

        onBack = onBack,
        modifier = modifier,
    ) {

        MiuixSmallTitle(stringResource(R.string.otp_hub_section_more))

        SettingsCard {

            onOpenRecords?.let { openRecords ->

                SettingNavigationRow(

                    icon = { label -> Icon(Icons.Outlined.History, contentDescription = label) },

                    title = stringResource(R.string.otp_records_entry_title),

                    subtitle = stringResource(R.string.otp_records_entry_desc),

                    onClick = openRecords,

                )

            }

            onOpenAutoInput?.let { openAutoInput ->

                SettingNavigationRow(

                    icon = { label -> Icon(Icons.Outlined.TouchApp, contentDescription = label) },

                    title = stringResource(R.string.otp_auto_input_entry_title),

                    subtitle = stringResource(R.string.otp_auto_input_entry_desc),

                    onClick = openAutoInput,

                )

            }

            onOpenMatchRules?.let { openMatchRules ->

                SettingNavigationRow(

                    icon = { label -> Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = label) },

                    title = stringResource(R.string.otp_match_rules_entry_title),

                    subtitle = stringResource(R.string.otp_match_rules_entry_desc),

                    onClick = openMatchRules,

                )

            }

        }



        OtpKeywordsEditorSection(

            keywordsText = keywordsText,

            onKeywordsTextChange = { keywordsText = it },

            onSave = { onKeywordsRegexChange(keywordsText) },

            onReset = {

                keywordsText = VerificationCodeExtractor.DEFAULT_KEYWORDS_REGEX

                onKeywordsRegexChange(keywordsText)

            },

        )

        OtpTestLinkSection(onOpenTest = { showTestDialog = true })

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


