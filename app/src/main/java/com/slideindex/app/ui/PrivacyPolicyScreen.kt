package com.slideindex.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    SettingsScreenScaffold(
        title = stringResource(R.string.privacy_policy_title),
        subtitle = stringResource(R.string.privacy_policy_subtitle),
        onBack = onBack,
    ) {
        item(key = "privacy-policy-body") {
            Text(
                text = stringResource(R.string.privacy_policy_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
