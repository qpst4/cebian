package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.otp.SmsBlacklistRuleSet
import com.slideindex.app.otp.SmsBlacklistTextCodec
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsLabeledTextFieldRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard

/** 短信黑名单：号码 / 前缀 / 内容 / 正则 + 拦截 / 删除动作（电话进程执行，改动后需重启）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpSmsBlacklistScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onBlacklistChange: (SmsBlacklistRuleSet) -> Unit,
) {
    val blacklist = settings.otpSmsBlacklist
    var numbersText by rememberSaveable { mutableStateOf(SmsBlacklistTextCodec.formatLines(blacklist.numbers)) }
    var prefixesText by rememberSaveable { mutableStateOf(SmsBlacklistTextCodec.formatLines(blacklist.prefixes)) }
    var contentText by rememberSaveable { mutableStateOf(SmsBlacklistTextCodec.formatLines(blacklist.content)) }
    var regexText by rememberSaveable { mutableStateOf(SmsBlacklistTextCodec.formatLines(blacklist.regex)) }

    val items = listOf(
        settingsCardScopeItem("blacklist-enabled") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_sms_blacklist_enabled_title),
                subtitle = stringResource(R.string.otp_sms_blacklist_enabled_desc),
                checked = blacklist.enabled,
                enabled = true,
                onCheckedChange = { onBlacklistChange(blacklist.copy(enabled = it)) },
            )
        },
        settingsCardScopeItem("blacklist-action-block") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_sms_blacklist_action_block_title),
                subtitle = stringResource(R.string.otp_sms_blacklist_action_block_desc),
                checked = blacklist.actionBlock,
                enabled = blacklist.enabled,
                onCheckedChange = { onBlacklistChange(blacklist.copy(actionBlock = it)) },
            )
        },
        settingsCardScopeItem("blacklist-action-delete") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_sms_blacklist_action_delete_title),
                subtitle = stringResource(R.string.otp_sms_blacklist_action_delete_desc),
                checked = blacklist.actionDelete,
                enabled = blacklist.enabled,
                onCheckedChange = { onBlacklistChange(blacklist.copy(actionDelete = it)) },
            )
        },
        settingsCardScopeItem("blacklist-numbers") {
            SettingsLabeledTextFieldRow(
                key = "blacklist-numbers-field",
                label = stringResource(R.string.otp_sms_blacklist_numbers_hint),
                value = numbersText,
                onValueChange = { text ->
                    numbersText = text
                    onBlacklistChange(blacklist.copy(numbers = SmsBlacklistTextCodec.parseLines(text)))
                },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
            )
        },
        settingsCardScopeItem("blacklist-prefixes") {
            SettingsLabeledTextFieldRow(
                key = "blacklist-prefixes-field",
                label = stringResource(R.string.otp_sms_blacklist_prefixes_hint),
                value = prefixesText,
                onValueChange = { text ->
                    prefixesText = text
                    onBlacklistChange(blacklist.copy(prefixes = SmsBlacklistTextCodec.parseLines(text)))
                },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
            )
        },
        settingsCardScopeItem("blacklist-content") {
            SettingsLabeledTextFieldRow(
                key = "blacklist-content-field",
                label = stringResource(R.string.otp_sms_blacklist_content_hint),
                value = contentText,
                onValueChange = { text ->
                    contentText = text
                    onBlacklistChange(blacklist.copy(content = SmsBlacklistTextCodec.parseLines(text)))
                },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
            )
        },
        settingsCardScopeItem("blacklist-regex") {
            SettingsLabeledTextFieldRow(
                key = "blacklist-regex-field",
                label = stringResource(R.string.otp_sms_blacklist_regex_hint),
                value = regexText,
                onValueChange = { text ->
                    regexText = text
                    onBlacklistChange(blacklist.copy(regex = SmsBlacklistTextCodec.parseLines(text)))
                },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
            )
        },
    )
    val restartHint = stringResource(R.string.otp_intercept_tip)
    val sectionTitle = stringResource(R.string.otp_sms_blacklist_section)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_sms_blacklist_section),
        subtitle = stringResource(R.string.otp_sms_blacklist_entry_desc),
        onBack = onBack,
    ) {
        settingsLazyTipCard(key = "otp_sms_blacklist_tip", text = restartHint)
        settingsLazySmallTitle(key = "otp_sms_blacklist_section", title = sectionTitle)
        groupedCardItems(keyPrefix = "otp-sms-blacklist", items = items)
    }
}
