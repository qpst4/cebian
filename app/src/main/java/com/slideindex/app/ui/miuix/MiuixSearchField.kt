package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.slideindex.app.R

/**
 * Miuix 风格搜索输入框，与外部 [query] 双向同步。
 */
@Composable
fun MiuixSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hintResId: Int = R.string.search_hint,
    focusRequester: FocusRequester? = null,
) {
    val state = rememberTextFieldState(initialText = query)
    val hint = stringResource(hintResId)

    LaunchedEffect(query) {
        val current = state.text.toString()
        if (current != query) {
            state.edit {
                replace(0, length, query)
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != query) {
                    onQueryChange(text)
                }
            }
    }

    TextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            ),
        label = hint,
        useLabelAsPlaceholder = true,
        lineLimits = TextFieldLineLimits.SingleLine,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = hint,
                modifier = Modifier.padding(horizontal = 12.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        },
    )
}
