package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.otp.OtpAutoFillStats
import com.slideindex.app.otp.OtpAutoFillUiLabels
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpAutoFillStatsScreen(
    stats: OtpAutoFillStats,
    onBack: () -> Unit,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val overviewSectionTitle = stringResource(R.string.otp_autofill_stats_overview_section)
    val emptyHint = stringResource(R.string.otp_autofill_stats_empty)
    val lastSectionTitle = stringResource(R.string.otp_autofill_stats_last_section)
    val helpSectionTitle = stringResource(R.string.otp_autofill_stats_help_section)
    val helpPipeline = stringResource(R.string.otp_autofill_stats_help_pipeline)
    val helpStrategies = stringResource(R.string.otp_autofill_stats_help_strategies)
    val helpFailures = stringResource(R.string.otp_autofill_stats_help_failures)

    SettingsScreenScaffold(
        title = stringResource(R.string.otp_autofill_stats_title),
        subtitle = stringResource(R.string.otp_autofill_stats_screen_desc),
        onBack = onBack,
        modifier = modifier,
    ) {
        settingsLazySmallTitle(key = "otp-stats-overview", title = overviewSectionTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "otp-stats-overview",
            items = buildList {
                if (stats.totalAttempts <= 0) {
                    add(
                        settingsCardScopeItem("empty") {
                            SettingsHintText(emptyHint)
                        },
                    )
                } else {
                    add(
                        settingsCardScopeItem("total-attempts") {
                            OtpStatRow(
                                label = stringResource(R.string.otp_autofill_stats_total_attempts),
                                value = stats.totalAttempts.toString(),
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("success-count") {
                            OtpStatRow(
                                label = stringResource(R.string.otp_autofill_stats_success_count),
                                value = stats.successCount.toString(),
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("failure-count") {
                            OtpStatRow(
                                label = stringResource(R.string.otp_autofill_stats_failure_count),
                                value = stats.failureCount.toString(),
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("success-rate") {
                            OtpStatRow(
                                label = stringResource(R.string.otp_autofill_stats_success_rate),
                                value = "${stats.successRatePercent}%",
                            )
                        },
                    )
                }
            },
        )

        if (stats.lastAttemptAtEpochMs != null) {
            settingsLazySmallTitle(
                key = "otp-stats-last",
                title = lastSectionTitle,
                sectionTop = true,
            )
            groupedCardItems(
                keyPrefix = "otp-stats-last",
                items = buildList {
                    add(
                        settingsCardScopeItem("last-result") {
                            val resultLabel = if (stats.lastSuccess == true) {
                                stringResource(R.string.otp_autofill_stats_last_success)
                            } else {
                                stringResource(R.string.otp_autofill_stats_last_failure)
                            }
                            OtpStatRow(
                                label = stringResource(R.string.otp_autofill_stats_last_result),
                                value = resultLabel,
                            )
                        },
                    )
                    OtpAutoFillUiLabels.formatLastAttemptTime(context, stats.lastAttemptAtEpochMs)?.let { time ->
                        add(
                            settingsCardScopeItem("last-time") {
                                OtpStatRow(
                                    label = stringResource(R.string.otp_autofill_stats_last_time),
                                    value = time,
                                )
                            },
                        )
                    }
                    stats.lastStrategy?.let { strategy ->
                        add(
                            settingsCardScopeItem("last-strategy") {
                                OtpStatRow(
                                    label = stringResource(R.string.otp_autofill_stats_last_strategy),
                                    value = OtpAutoFillUiLabels.formatStrategy(context, strategy),
                                )
                            },
                        )
                    }
                    stats.lastReason?.let { reason ->
                        add(
                            settingsCardScopeItem("last-reason") {
                                OtpStatRow(
                                    label = stringResource(R.string.otp_autofill_stats_last_reason),
                                    value = OtpAutoFillUiLabels.formatReason(context, reason),
                                )
                            },
                        )
                    }
                },
            )
        }

        settingsLazySmallTitle(
            key = "otp-stats-help",
            title = helpSectionTitle,
            sectionTop = true,
        )
        settingsLazyHint(key = "otp-stats-help-pipeline", text = helpPipeline)
        settingsLazyHint(key = "otp-stats-help-strategies", text = helpStrategies)
        settingsLazyHint(key = "otp-stats-help-failures", text = helpFailures)

        if (stats.totalAttempts > 0) {
            groupedCardItems(
                keyPrefix = "otp-stats-reset",
                items = listOf(
                    settingsCardScopeItem("reset") {
                        SettingLinkRow(
                            title = stringResource(R.string.otp_autofill_stats_reset),
                            onClick = onResetStats,
                        )
                    },
                ),
            )
        }
    }
}

@Composable
private fun OtpStatRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
