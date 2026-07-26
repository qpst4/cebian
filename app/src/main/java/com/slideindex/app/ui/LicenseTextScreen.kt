package com.slideindex.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LicenseTextScreen(
    assetFileName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val body = remember(assetFileName) {
        runCatching {
            context.assets.open("licenses/$assetFileName")
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { "" }
    }
    val title = remember(assetFileName) {
        assetFileName.removeSuffix(".txt")
    }
    SettingsScreenScaffold(
        title = title,
        onBack = onBack,
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
