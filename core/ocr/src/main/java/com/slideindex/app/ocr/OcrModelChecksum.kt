package com.slideindex.app.ocr

import java.io.File
import java.security.MessageDigest

internal object OcrModelChecksum {
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun matches(file: File, expectedSha256: String?): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (expectedSha256.isNullOrBlank()) return true
        return sha256Hex(file).equals(expectedSha256, ignoreCase = true)
    }
}
