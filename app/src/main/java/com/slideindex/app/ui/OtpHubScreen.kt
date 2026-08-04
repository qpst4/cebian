package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.slideindex.app.R
import com.slideindex.app.otp.OtpAutoFillStats
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.MiuixScaffoldTabRowBottomContent
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.viewmodel.OtpRecordsViewModel

enum class OtpHubTab {
    Rules,
    Records,
    Extensions,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpHubScreen(
    settings: AppSettings,
    officialRules: List<OtpMatchRule>,
    initialTab: OtpHubTab = OtpHubTab.Rules,
    accessibilityGranted: Boolean,
    onExit: () -> Unit,
    onCopyToClipboardChange: (Boolean) -> Unit,
    onKeywordsRegexChange: (String) -> Unit,
    onRefreshOfficialRules: () -> Unit,
    onOfficialRuleEnabledChange: (String, Boolean) -> Unit,
    onUserRulesChange: (List<OtpMatchRule>) -> Unit,
    onAutoInputChange: (Boolean) -> Unit,
    onAutoConfirmChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onRequestAccessibility: () -> Unit,
    onLsposedSmsChange: (Boolean) -> Unit = {},
    onLsposedSystemInjectChange: (Boolean) -> Unit = {},
    stats: OtpAutoFillStats? = null,
    onOpenStats: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val recordsViewModel: OtpRecordsViewModel = hiltViewModel()

    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var showTestDialog by rememberSaveable { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<OtpMatchRule?>(null) }
    var keywordsText by rememberSaveable(settings.otpKeywordsRegex) {
        mutableStateOf(settings.otpKeywordsRegex)
    }

    val recordsUi = rememberOtpRecordsUi(
        embeddedInHub = true,
        onOpenTestFlow = {
            selectedTab = OtpHubTab.Rules
            showTestDialog = true
        },
        viewModel = recordsViewModel,
    )
    val extensionGroups = rememberOtpAutoInputSettingsLazyGroups(
        settings = settings,
        accessibilityGranted = accessibilityGranted,
        onRequestAccessibility = onRequestAccessibility,
        onAutoInputChange = onAutoInputChange,
        onAutoConfirmChange = onAutoConfirmChange,
        onDelayChange = onDelayChange,
        onIntervalChange = onIntervalChange,
        onLsposedSmsChange = onLsposedSmsChange,
        onLsposedSystemInjectChange = onLsposedSystemInjectChange,
        onCopyToClipboardChange = onCopyToClipboardChange,
        stats = stats,
        onOpenStats = onOpenStats,
    )
    val runtimeSectionTitle = stringResource(R.string.otp_runtime_status_section)
    val autoFillSectionTitle = stringResource(R.string.otp_auto_fill_section)
    val lsposedSectionTitle = stringResource(R.string.otp_lsposed_enhancements_section)
    val lsposedSectionDesc = stringResource(R.string.otp_lsposed_enhancements_desc)
    val diagnosticsSectionTitle = stringResource(R.string.otp_diagnostics_section)
    val timingSectionTitle = stringResource(R.string.otp_auto_input_timing_section)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_hub_entry_title),
        subtitle = stringResource(R.string.otp_hub_entry_desc),
        onBack = onExit,
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (selectedTab == OtpHubTab.Rules) {
                MiuixSettingsFab(
                    onClick = {
                        editingRule = null
                        showEditor = true
                    },
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(R.string.otp_rules_add),
                )
            }
        },
        bottomContent = {
            MiuixScaffoldTabRowBottomContent {
                MiuixTabRowWithContour(
                    tabs = listOf(
                        stringResource(R.string.otp_hub_tab_rules),
                        stringResource(R.string.otp_hub_tab_records),
                        stringResource(R.string.otp_hub_tab_extensions),
                    ),
                    selectedTabIndex = selectedTab.ordinal,
                    onTabSelected = { selectedTab = OtpHubTab.entries[it] },
                )
            }
        },
    ) {
        when (selectedTab) {
            OtpHubTab.Rules -> otpRulesListItems(
                embeddedInHub = true,
                officialRules = officialRules,
                userRules = settings.otpUserMatchRules,
                disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
                showExtractionExtras = true,
                settings = settings,
                keywordsText = keywordsText,
                onKeywordsTextChange = { keywordsText = it },
                onRefreshOfficialRules = onRefreshOfficialRules,
                onOfficialRuleEnabledChange = onOfficialRuleEnabledChange,
                onUserRulesChange = onUserRulesChange,
                onKeywordsRegexChange = onKeywordsRegexChange,
                onShowTestDialog = { showTestDialog = true },
                onEditRule = { rule ->
                    editingRule = rule
                    showEditor = true
                },
            )
            OtpHubTab.Records -> recordsUi.appendListItems(this)
            OtpHubTab.Extensions -> emitOtpAutoInputSettingsItems(
                groups = extensionGroups,
                runtimeSectionTitle = runtimeSectionTitle,
                autoFillSectionTitle = autoFillSectionTitle,
                lsposedSectionTitle = lsposedSectionTitle,
                lsposedSectionDesc = lsposedSectionDesc,
                diagnosticsSectionTitle = diagnosticsSectionTitle,
                timingSectionTitle = timingSectionTitle,
            )
        }
    }

    if (showTestDialog) {
        OtpTestDialogHost(
            settings = settings,
            officialRules = officialRules,
            keywordsRegex = keywordsText,
            onDismiss = { showTestDialog = false },
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
                    settings.otpUserMatchRules.map { if (it.id == saved.id) saved else it }
                } else {
                    settings.otpUserMatchRules + saved
                }
                onUserRulesChange(updated)
                showEditor = false
                editingRule = null
            },
        )
    }

    recordsUi.overlays()
}
