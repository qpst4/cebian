package com.slideindex.app.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState

@Composable
fun UserMessageSnackbarHost(
    userMessageBus: UserMessageBus,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(userMessageBus) {
        userMessageBus.messages.collect { message ->
            val text = when (message) {
                is UserMessage.Error -> message.text
                is UserMessage.Success -> message.text
            }
            snackbarHostState.showSnackbar(text)
        }
    }
    SnackbarHost(
        state = snackbarHostState,
        modifier = modifier,
    )
}
