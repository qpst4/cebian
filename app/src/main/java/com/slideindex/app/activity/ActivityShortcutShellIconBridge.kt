package com.slideindex.app.activity

import android.content.Context
import android.graphics.Bitmap
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.shell.ShellCommandIconResolver
import java.io.ByteArrayOutputStream

/**
 * 将 Shell 命令面板图标复制到应用内直达专用目录，避免 shell_icons 与 shortcut_icons 割裂。
 */
object ActivityShortcutShellIconBridge {
    fun withCopiedIcon(context: Context, cmd: ShellCommand): ActivityShortcut {
        val base = ActivityShortcutShellSupport.fromShellCommand(cmd)
        if (!cmd.hasCustomIcon()) return base
        val bitmap = ShellCommandIconResolver.resolveBitmap(context, cmd, 128) ?: return base
        val path = ShortcutIconStorage.saveIconFromBytes(context, bitmap.toPngBytes()) ?: return base
        return base.copy(iconPath = path)
    }

    private fun Bitmap.toPngBytes(): ByteArray =
        ByteArrayOutputStream().use { stream ->
            if (!compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                error("Failed to compress shell shortcut icon")
            }
            stream.toByteArray()
        }
}
