package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver
import com.slideindex.app.shell.ShellCommandIconType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ShellCommandIconCornerFraction = 0.24f

@Composable
fun ShellCommandIcon(
    command: ShellCommand,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    showDefaultCodeIcon: Boolean = true,
) {
    val context = LocalContext.current
    var bitmap by remember(command.id, command.iconType, command.iconPath, command.textIcon) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(command) {
        if (!command.hasCustomIcon()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            ShellCommandIconResolver.resolveBitmap(context, command, 128)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val shape = RoundedCornerShape(maxWidth * ShellCommandIconCornerFraction)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            when {
                image != null -> {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = command.label,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape),
                        contentScale = ContentScale.Crop,
                        alpha = alpha,
                    )
                }
                command.iconType == ShellCommandIconType.TEXT -> {
                    Text(
                        text = command.textIcon?.take(2) ?: command.label.take(1).ifBlank { "?" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    )
                }
                showDefaultCodeIcon -> {
                    Icon(
                        imageVector = ThinActionIcons.Code,
                        contentDescription = command.label,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    )
                }
            }
        }
    }
}
