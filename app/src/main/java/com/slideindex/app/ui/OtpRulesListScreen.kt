package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.slideindex.app.R
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.settings.AppSettings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val embeddedInHub = onBack == null
    val showExtractionExtras = embeddedInHub && settings != null

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hubContentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 88.dp)

    if (embeddedInHub) {
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(hubContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OtpRulesListBody(
                    embeddedInHub = true,
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
            FloatingActionButton(
                onClick = {
                    editingRule = null
                    showEditor = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.otp_rules_add))
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    MediumFlexibleTopAppBar(
                        title = { SettingsAppBarTitle(stringResource(R.string.otp_rules_list_title)) },
                        navigationIcon = {
                            onBack?.let { back ->
                                IconButton(onClick = back) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = onRefreshOfficialRules) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.otp_rules_refresh))
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            editingRule = null
                            showEditor = true
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.otp_rules_add))
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OtpRulesListBody(
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
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    if (showExtractionExtras && showTestDialog) {
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

@Composable
private fun OtpRulesListBody(
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.otp_rules_tab_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefreshOfficialRules) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.otp_rules_refresh))
            }
        }
        SettingsHintText(stringResource(R.string.otp_hub_rules_hint))
    }

    SettingsSectionTitle(stringResource(R.string.otp_rules_official_section))
    SettingsHintText(
        stringResource(R.string.otp_rules_official_hint, officialRules.size),
    )
    if (officialRules.isEmpty()) {
        Text(
            text = stringResource(R.string.otp_rules_official_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    } else {
        officialRules.forEach { rule ->
            val enabled = rule.id !in disabledOfficialRuleIds
            OtpRuleCard(
                rule = rule,
                enabled = enabled,
                showDelete = false,
                onEnabledChange = { onOfficialRuleEnabledChange(rule.id, it) },
                onEdit = null,
                onDelete = null,
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    SettingsSectionTitle(stringResource(R.string.otp_rules_user_section))
    if (userRules.isEmpty()) {
        Text(
            text = stringResource(R.string.otp_rules_user_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    } else {
        userRules.forEach { rule ->
            OtpRuleCard(
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
    }

    if (showExtractionExtras) {
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
        OtpTestLinkSection(onOpenTest = onShowTestDialog!!)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtpRuleCard(
    rule: OtpMatchRule,
    enabled: Boolean,
    showDelete: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

    if (onEdit != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            onClick = onEdit,
        ) {
            content()
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun OtpRuleEditorDialog(
    initialRule: OtpMatchRule?,
    onDismiss: () -> Unit,
    onSave: (OtpMatchRule) -> Unit,
) {
    var name by remember(initialRule) { mutableStateOf(initialRule?.name.orEmpty()) }
    var keyword by remember(initialRule) { mutableStateOf(initialRule?.keyword.orEmpty()) }
    var regex by remember(initialRule) { mutableStateOf(initialRule?.regex.orEmpty()) }
    var packageName by remember(initialRule) { mutableStateOf(initialRule?.packageName.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialRule == null) R.string.otp_rules_add else R.string.otp_rules_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.otp_rules_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text(stringResource(R.string.otp_rules_keyword_field_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = regex,
                    onValueChange = { regex = it },
                    label = { Text(stringResource(R.string.otp_rules_regex_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text(stringResource(R.string.otp_rules_package_label)) },
                    supportingText = { Text(stringResource(R.string.otp_rules_package_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
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
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
