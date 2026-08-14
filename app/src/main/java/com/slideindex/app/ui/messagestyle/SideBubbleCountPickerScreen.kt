package com.slideindex.app.ui.messagestyle

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.SettingsRadioPickerScreen
import com.slideindex.app.ui.settings.components.settingsCardScopeItem

@Composable
fun SideBubbleCountPickerScreen(
    selectedCount: Int,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val radioItems = buildList {
        (9 downTo 1).forEach { count ->
            add(
                settingsCardScopeItem("count-$count") {
                    SettingRadioRow(
                        title = stringResource(R.string.message_style_side_count_option, count),
                        selected = selectedCount == count,
                        onClick = { onSelect(count) },
                    )
                },
            )
        }
    }
    SettingsRadioPickerScreen(
        title = stringResource(R.string.message_style_side_count),
        onBack = onBack,
        items = radioItems,
    )
}
