@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.SideBubbleFontSize
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsSectionTitle

@Composable
internal fun SideBubbleFontSizeSettings(
    settings: MessageSettings,
    enabled: Boolean,
    onFontSizeLevelChange: (Int) -> Unit,
) {
    SettingsSectionTitle(stringResource(R.string.message_style_side_font_size))
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.message_style_side_font_size_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MessageStyleChip(
                    label = stringResource(R.string.message_style_side_font_size_small),
                    selected = settings.sideBubbleFontSizeLevel == SideBubbleFontSize.SMALL,
                    enabled = enabled,
                    onClick = { onFontSizeLevelChange(SideBubbleFontSize.SMALL) },
                    modifier = Modifier.weight(1f),
                )
                MessageStyleChip(
                    label = stringResource(R.string.message_style_side_font_size_normal),
                    selected = settings.sideBubbleFontSizeLevel == SideBubbleFontSize.NORMAL,
                    enabled = enabled,
                    onClick = { onFontSizeLevelChange(SideBubbleFontSize.NORMAL) },
                    modifier = Modifier.weight(1f),
                )
                MessageStyleChip(
                    label = stringResource(R.string.message_style_side_font_size_large),
                    selected = settings.sideBubbleFontSizeLevel == SideBubbleFontSize.LARGE,
                    enabled = enabled,
                    onClick = { onFontSizeLevelChange(SideBubbleFontSize.LARGE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
