package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import com.slideindex.app.ui.miuix.MiuixFormDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpRulesListScreen(
    officialRules: List<OtpMatchRule>,
    userRules: List<OtpMatchRule>,
    disabledOfficialRuleIds: Set<String>,
    onBack: (() -> Unit)?,
    onRefreshOfficialRules: () -> Unit,
    onOfficialRuleEnabledChange: (String, Boolean) -> Unit,
    onUserRulesChange: (List<OtpMatchRule>) -> Unit,
    modifier: Modifier = Modifier,
    settings: AppSettings? = null,
    onKeywordsRegexChange: ((String) -> Unit)? = null,
    showTestDialog: Boolean = false,
    onShowTestDialog: (() -> Unit)? = null,
    onDismissTestDialog: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<OtpMatchRule?>(null) }
    var keywordsText by remember(settings?.otpKeywordsRegex) {
        mutableStateOf(settings?.otpKeywordsRegex.orEmpty())
    }

    val showExtractionExtras = settings != null

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_rules_list_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onRefreshOfficialRules) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.otp_rules_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = {
                    editingRule = null
                    showEditor = true
                },
                icon = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.otp_rules_add),
            )
        },
    ) {
        otpRulesListItems(
            embeddedInHub = false,
            officialRules = officialRules,
            userRules = userRules,
            disabledOfficialRuleIds = disabledOfficialRuleIds,
            showExtractionExtras = showExtractionExtras,
            settings = settings,
            keywordsText = keywordsText,
            onKeywordsTextChange = { keywordsText = it },
            onRefreshOfficialRules = onRefreshOfficialRules,
            onOfficialRuleEnabledChange = onOfficialRuleEnabledChange,
            onUserRulesChange = onUserRulesChange,
            onKeywordsRegexChange = onKeywordsRegexChange,
            onShowTestDialog = onShowTestDialog,
            onEditRule = { rule ->
                editingRule = rule
                showEditor = true
            },
        )
    }

    if (showExtractionExtras && showTestDialog) {
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        OtpTestDialogHost(
            settings = settings!!,
            officialRules = officialRules,
            keywordsRegex = keywordsText,
            onDismiss = onDismissTestDialog!!,
        )
    }

    if (showEditor) {
        OtpRuleEditorDialog(
            initialRule = editingRule,
            onDismiss = {
                showEditor = false
                editingRule = null
            },
            onSave = { saved ->
                if (saved.name.isBlank() || saved.keyword.isBlank() || saved.regex.isBlank()) {
                    Toast.makeText(context, R.string.otp_rules_invalid, Toast.LENGTH_SHORT).show()
                    return@OtpRuleEditorDialog
                }
                val updated = if (editingRule != null) {
                    userRules.map { if (it.id == saved.id) saved else it }
                } else {
                    userRules + saved
                }
                onUserRulesChange(updated)
                showEditor = false
                editingRule = null
            },
        )
    }
}

internal fun LazyListScope.otpRulesListItems(
    embeddedInHub: Boolean,
    officialRules: List<OtpMatchRule>,
    userRules: List<OtpMatchRule>,
    disabledOfficialRuleIds: Set<String>,
    showExtractionExtras: Boolean,
    settings: AppSettings?,
    keywordsText: String,
    onKeywordsTextChange: (String) -> Unit,
    onRefreshOfficialRules: () -> Unit,
    onOfficialRuleEnabledChange: (String, Boolean) -> Unit,
    onUserRulesChange: (List<OtpMatchRule>) -> Unit,
    onKeywordsRegexChange: ((String) -> Unit)?,
    onShowTestDialog: (() -> Unit)?,
    onEditRule: (OtpMatchRule) -> Unit,
) {
    if (embeddedInHub) {
        item(key = "hub_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.otp_rules_tab_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefreshOfficialRules) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.otp_rules_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "hub_hint") {
            MiuixHintText(stringResource(R.string.otp_hub_rules_hint))
        }
    }

    item(key = "official_section_title") {
        MiuixSmallTitle(stringResource(R.string.otp_rules_official_section))
    }
    item(key = "official_section_hint") {
        MiuixHintText(
            stringResource(R.string.otp_rules_official_hint, officialRules.size),
        )
    }
    if (officialRules.isEmpty()) {
        item(key = "official_empty") {
            Text(
                text = stringResource(R.string.otp_rules_official_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
    } else {
        groupedCardItems(
            keyPrefix = "otp-official-rules",
            items = officialRules.map { rule ->
                val enabled = rule.id !in disabledOfficialRuleIds
                CardItem(key = rule.id) {
                    OtpRuleRowContent(
                        rule = rule,
                        enabled = enabled,
                        showDelete = false,
                        onEnabledChange = { onOfficialRuleEnabledChange(rule.id, it) },
                        onDelete = null,
                        onEdit = null,
                    )
                }
            },
        )
    }

    item(key = "user_section_title") {
        MiuixSmallTitle(stringResource(R.string.otp_rules_user_section))
    }
    if (userRules.isEmpty()) {
        item(key = "user_empty") {
            Text(
                text = stringResource(R.string.otp_rules_user_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
    } else {
        groupedCardItems(
            keyPrefix = "otp-user-rules",
            items = userRules.map { rule ->
                CardItem(key = rule.id) {
                    OtpRuleRowContent(
                        rule = rule,
                        enabled = rule.enabled,
                        showDelete = true,
                        onEnabledChange = { enabled ->
                            onUserRulesChange(
                                userRules.map {
                                    if (it.id == rule.id) it.copy(enabled = enabled) else it
                                },
                            )
                        },
                        onEdit = { onEditRule(rule) },
                        onDelete = {
                            onUserRulesChange(userRules.filterNot { it.id == rule.id })
                        },
                    )
                }
            },
        )
    }

    if (showExtractionExtras) {
        item(key = "keywords_section") {
            OtpKeywordsEditorSection(
                keywordsText = keywordsText,
                onKeywordsTextChange = onKeywordsTextChange,
                onSave = { onKeywordsRegexChange!!(keywordsText) },
                onReset = {
                    val defaultRegex = com.slideindex.app.otp.VerificationCodeExtractor.DEFAULT_KEYWORDS_REGEX
                    onKeywordsTextChange(defaultRegex)
                    onKeywordsRegexChange!!(defaultRegex)
                },
                sectionTitle = stringResource(R.string.otp_keywords_fallback_section),
            )
        }
        item(key = "test_link") {
            OtpTestLinkSection(onOpenTest = onShowTestDialog!!)
        }
    }
}

@Composable
private fun OtpRuleRowContent(
    rule: OtpMatchRule,
    enabled: Boolean,
    showDelete: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onEdit != null) {
                    Modifier.clickable(onClick = onEdit)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showDelete && onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.otp_rules_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
        Text(
            text = stringResource(R.string.otp_rules_keyword_label, rule.keyword),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = rule.regex,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun OtpRuleEditorDialog(
    initialRule: OtpMatchRule?,
    onDismiss: () -> Unit,
    onSave: (OtpMatchRule) -> Unit,
) {
    var name by remember(initialRule) { mutableStateOf(initialRule?.name.orEmpty()) }
    var keyword by remember(initialRule) { mutableStateOf(initialRule?.keyword.orEmpty()) }
    var regex by remember(initialRule) { mutableStateOf(initialRule?.regex.orEmpty()) }
    var packageName by remember(initialRule) { mutableStateOf(initialRule?.packageName.orEmpty()) }

    val nameLabel = stringResource(R.string.otp_rules_name_label)
    val keywordLabel = stringResource(R.string.otp_rules_keyword_field_label)
    val regexLabel = stringResource(R.string.otp_rules_regex_label)
    val packageLabel = stringResource(R.string.otp_rules_package_label)

    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(
            if (initialRule == null) R.string.otp_rules_add else R.string.otp_rules_edit,
        ),
        onConfirm = {
            onSave(
                OtpMatchRule(
                    id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    keyword = keyword.trim(),
                    regex = regex.trim(),
                    packageName = packageName.trim().takeIf { it.isNotBlank() },
                    isOfficial = false,
                    enabled = initialRule?.enabled ?: true,
                ),
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiuixLabeledTextField(
                value = name,
                onValueChange = { name = it },
                label = nameLabel,
            )
            MiuixLabeledTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = keywordLabel,
            )
            MiuixLabeledTextField(
                value = regex,
                onValueChange = { regex = it },
                label = regexLabel,
                singleLine = false,
                minLines = 2,
                maxLines = 4,
            )
            MiuixLabeledTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = packageLabel,
            )
            Text(
                text = stringResource(R.string.otp_rules_package_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
