package com.slideindex.app.clipboard.monitor

/**
 * Based on [ClipboardListener](https://github.com/aa2013/ClipboardListener) (MIT).
 */
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

internal fun copyAssetToExternalPrivateDir(
    context: Context,
    assetFileName: String,
): Boolean {
    var inputStream: InputStream? = null
    var outputStream: FileOutputStream? = null
    return try {
        inputStream = context.resources.assets.open(assetFileName)
        val outFile = File(context.getExternalFilesDir(null), assetFileName)
        outputStream = FileOutputStream(outFile)
        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            outputStream.write(buffer, 0, length)
        }
        outputStream.flush()
        true
    } catch (e: IOException) {
        Log.e("ClipboardMonitorAssets", "Failed to copy asset file: $assetFileName", e)
        false
    } finally {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
    }
}

/** DEX entry class packaged in [LISTENER_ZIP_ASSET]. */
internal const val LISTENER_MAIN_CLASS = "top.coclyun.clipshare.clipboard_listener.ClipboardListener"

internal const val LISTENER_ZIP_ASSET = "listener.zip"
