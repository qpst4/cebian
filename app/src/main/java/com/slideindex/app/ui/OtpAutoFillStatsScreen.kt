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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpAutoFillStatsScreen(
    stats: OtpAutoFillStats,
    onBack: () -> Unit,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    SettingsScreenScaffold(
        title = stringResource(R.string.otp_autofill_stats_title),
        subtitle = stringResource(R.string.otp_autofill_stats_screen_desc),
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsSectionTitle(stringResource(R.string.otp_autofill_stats_overview_section))
        SettingsCard {
            if (stats.totalAttempts <= 0) {
                SettingsHintText(stringResource(R.string.otp_autofill_stats_empty))
            } else {
                OtpStatRow(
                    label = stringResource(R.string.otp_autofill_stats_total_attempts),
                    value = stats.totalAttempts.toString(),
                )
                OtpStatRow(
                    label = stringResource(R.string.otp_autofill_stats_success_count),
                    value = stats.successCount.toString(),
                )
                OtpStatRow(
                    label = stringResource(R.string.otp_autofill_stats_failure_count),
                    value = stats.failureCount.toString(),
                )
                OtpStatRow(
                    label = stringResource(R.string.otp_autofill_stats_success_rate),
                    value = "${stats.successRatePercent}%",
                )
            }
        }

        if (stats.lastAttemptAtEpochMs != null) {
            SettingsSectionTitle(stringResource(R.string.otp_autofill_stats_last_section))
            SettingsCard {
                val resultLabel = if (stats.lastSuccess == true) {
                    stringResource(R.string.otp_autofill_stats_last_success)
                } else {
                    stringResource(R.string.otp_autofill_stats_last_failure)
                }
                OtpStatRow(
                    label = stringResource(R.string.otp_autofill_stats_last_result),
                    value = resultLabel,
                )
                OtpAutoFillUiLabels.formatLastAttemptTime(context, stats.lastAttemptAtEpochMs)?.let { time ->
                    OtpStatRow(
                        label = stringResource(R.string.otp_autofill_stats_last_time),
                        value = time,
                    )
                }
                stats.lastStrategy?.let { strategy ->
                    OtpStatRow(
                        label = stringResource(R.string.otp_autofill_stats_last_strategy),
                        value = OtpAutoFillUiLabels.formatStrategy(context, strategy),
                    )
                }
                stats.lastReason?.let { reason ->
                    OtpStatRow(
                        label = stringResource(R.string.otp_autofill_stats_last_reason),
                        value = OtpAutoFillUiLabels.formatReason(context, reason),
                    )
                }
            }
        }

        SettingsSectionTitle(stringResource(R.string.otp_autofill_stats_help_section))
        SettingsCard {
            SettingsHintText(stringResource(R.string.otp_autofill_stats_help_pipeline))
            SettingsHintText(stringResource(R.string.otp_autofill_stats_help_strategies))
            SettingsHintText(stringResource(R.string.otp_autofill_stats_help_failures))
        }

        if (stats.totalAttempts > 0) {
            SettingsCard {
                SettingLinkRow(
                    title = stringResource(R.string.otp_autofill_stats_reset),
                    onClick = onResetStats,
                )
            }
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
