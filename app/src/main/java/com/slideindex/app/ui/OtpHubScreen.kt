package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.otp.OtpAutoFillStats
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

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
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var showTestDialog by rememberSaveable { mutableStateOf(false) }

    SettingsScreenScaffold(
        title = stringResource(R.string.otp_hub_entry_title),
        subtitle = stringResource(R.string.otp_hub_entry_desc),
        onBack = onExit,
        scrollContent = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == OtpHubTab.Rules,
                    onClick = { selectedTab = OtpHubTab.Rules },
                    text = { Text(stringResource(R.string.otp_hub_tab_rules)) },
                )
                Tab(
                    selected = selectedTab == OtpHubTab.Records,
                    onClick = { selectedTab = OtpHubTab.Records },
                    text = { Text(stringResource(R.string.otp_hub_tab_records)) },
                )
                Tab(
                    selected = selectedTab == OtpHubTab.Extensions,
                    onClick = { selectedTab = OtpHubTab.Extensions },
                    text = { Text(stringResource(R.string.otp_hub_tab_extensions)) },
                )
            }

            when (selectedTab) {
                OtpHubTab.Rules -> OtpRulesListScreen(
                    officialRules = officialRules,
                    userRules = settings.otpUserMatchRules,
                    disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
                    settings = settings,
                    onBack = null,
                    onRefreshOfficialRules = onRefreshOfficialRules,
                    onOfficialRuleEnabledChange = onOfficialRuleEnabledChange,
                    onUserRulesChange = onUserRulesChange,
                    onKeywordsRegexChange = onKeywordsRegexChange,
                    showTestDialog = showTestDialog,
                    onShowTestDialog = { showTestDialog = true },
                    onDismissTestDialog = { showTestDialog = false },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                OtpHubTab.Records -> OtpRecordsScreen(
                    onBack = null,
                    onOpenTestFlow = {
                        selectedTab = OtpHubTab.Rules
                        showTestDialog = true
                    },
                    contentPadding = PaddingValues(0.dp),
                )

                OtpHubTab.Extensions -> OtpAutoInputSettingsScreen(
                    settings = settings,
                    accessibilityGranted = accessibilityGranted,
                    onBack = null,
                    onAutoInputChange = onAutoInputChange,
                    onAutoConfirmChange = onAutoConfirmChange,
                    onDelayChange = onDelayChange,
                    onIntervalChange = onIntervalChange,
                    onRequestAccessibility = onRequestAccessibility,
                    onLsposedSmsChange = onLsposedSmsChange,
                    onLsposedSystemInjectChange = onLsposedSystemInjectChange,
                    onCopyToClipboardChange = onCopyToClipboardChange,
                    stats = stats,
                    onOpenStats = onOpenStats,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}
