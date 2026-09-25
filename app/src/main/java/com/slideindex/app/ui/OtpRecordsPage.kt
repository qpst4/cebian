package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.viewmodel.OtpRecordsViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** 验证码记录子页。 */
@Composable
fun OtpRecordsPage(
    onBack: () -> Unit,
    onOpenTestFlow: (() -> Unit)? = null,
    viewModel: OtpRecordsViewModel = hiltViewModel(),
) {
    val recordsUi = rememberOtpRecordsUi(
        embeddedInHub = false,
        onOpenTestFlow = onOpenTestFlow,
        viewModel = viewModel,
    )
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_records_title),
        subtitle = stringResource(R.string.otp_records_entry_desc),
        onBack = onBack,
        actions = recordsUi.scaffoldActions,
    ) {
        recordsUi.appendListItems(this)
    }
    recordsUi.overlays()
}
