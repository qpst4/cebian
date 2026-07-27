package com.slideindex.app.ui.messagestyle

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.SettingsRadioPickerScreen

@Composable
fun SideBubbleCountPickerScreen(
    selectedCount: Int,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    SettingsRadioPickerScreen(
        title = stringResource(R.string.message_style_side_count),
        onBack = onBack,
    ) {
        (9 downTo 1).forEach { count ->
            SettingRadioRow(
                title = stringResource(R.string.message_style_side_count_option, count),
                selected = selectedCount == count,
                onClick = { onSelect(count) },
            )
        }
    }
}
