package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.TextField

/**
 * 与外部 [value] 双向同步的 Miuix 表单输入框，用于替代 Material [androidx.compose.material3.OutlinedTextField]。
 */
@Composable
fun MiuixLabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
) {
    val state = rememberTextFieldState(initialText = value)

    LaunchedEffect(value) {
        val current = state.text.toString()
        if (current != value) {
            state.edit {
                replace(0, length, value)
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != value) {
                    onValueChange(text)
                }
            }
    }

    val lineLimits = if (singleLine) {
        TextFieldLineLimits.SingleLine
    } else {
        TextFieldLineLimits.MultiLine(
            minHeightInLines = minLines.coerceAtLeast(1),
            maxHeightInLines = maxLines.coerceAtLeast(minLines),
        )
    }

    TextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = label,
        useLabelAsPlaceholder = true,
        lineLimits = lineLimits,
    )
}
