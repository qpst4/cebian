package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.otp.OtpAutoFillStats
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpAutoInputSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: (() -> Unit)?,
    onRequestAccessibility: () -> Unit,
    onAutoInputChange: (Boolean) -> Unit,
    onAutoConfirmChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLsposedSmsChange: (Boolean) -> Unit = {},
    onLsposedSystemInjectChange: (Boolean) -> Unit = {},
    onCopyToClipboardChange: (Boolean) -> Unit = {},
    stats: OtpAutoFillStats? = null,
    onOpenStats: (() -> Unit)? = null,
) {
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_auto_input_title),
        subtitle = stringResource(R.string.otp_auto_input_desc),
        onBack = onBack,
        modifier = modifier,
    ) {
        otpAutoInputSettingsItems(
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
    }
}
