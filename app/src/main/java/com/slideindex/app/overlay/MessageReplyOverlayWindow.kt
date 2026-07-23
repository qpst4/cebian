package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.NotificationData
import com.slideindex.app.notification.NotificationRemoteReply
import com.slideindex.app.ui.theme.SlideIndexTheme

object MessageReplyOverlayWindow {
    private const val TAG = "MessageReplyOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var appContext: Context? = null

    fun show(
        context: Context,
        data: NotificationData,
        onSent: () -> Unit,
        onCancelled: () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, data, onSent, onCancelled) }
            return
        }

        dismiss()

        val hostContext = MessageOverlayHost.resolveHostContext(context)
            ?: run {
                Log.w(TAG, "overlay permission not granted")
                onCancelled()
                return
            }

        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            onCancelled()
            return
        }
        val dialogOwner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner)
        val completeSend = {
            hideIme(view)
            dismiss()
            onSent()
        }
        val completeCancel = {
            hideIme(view)
            dismiss()
            onCancelled()
        }
        view.setContent {
            MessageReplyContent(
                data = data,
                onSend = { text ->
                    val sent = NotificationRemoteReply.sendReply(hostContext, data.key, data.postTime, text)
                    completeSend()
                    if (!sent) {
                        Log.w(TAG, "quick reply failed for ${data.key}")
                    }
                },
                onCancel = completeCancel,
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(hostContext),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            onCancelled()
            return
        }

        windowManager = wm
        composeView = view
        owner = dialogOwner
        appContext = hostContext
        backHandler = OverlayViewBackHandler(view, completeCancel).also { it.attach() }
        view.requestFocus()
        view.post { FloatBallOverlay.bringChromeAbovePanels() }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        backHandler?.detach()
        backHandler = null
        val wm = windowManager
        val view = composeView
        hideIme(view)
        if (wm != null && view != null) {
            runCatching { wm.removeView(view) }
        }
        OverlayCompose.disposeComposeView(composeView)
        owner?.destroy()
        owner = null
        composeView = null
        windowManager = null
        appContext = null
    }

    private fun hideIme(view: View?) {
        view ?: return
        val imm = view.context.getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

@Composable
private fun MessageReplyContent(
    data: NotificationData,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var text by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val scrimInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    SlideIndexTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        indication = null,
                        interactionSource = scrimInteractionSource,
                        onClick = onCancel,
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.message_action_quick_reply_to, data.title.ifBlank { data.packageName }),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (data.content.isNotBlank()) {
                    QuickReplyMessagePreview(content = data.content)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.message_action_quick_reply_hint)) },
                    singleLine = false,
                    maxLines = 4,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = { onSend(text.text) },
                        enabled = text.text.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.message_action_quick_reply_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickReplyMessagePreview(content: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
        )
        Text(
            text = content,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
