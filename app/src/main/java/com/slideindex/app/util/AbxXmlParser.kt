package com.slideindex.app.util

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Android ShortcutService persists state as ABX (binary XML) on modern releases.
 * Parsing requires framework binary XML support (not available on all OEM ROMs).
 */
internal object AbxXmlParser {
    private const val TAG = "AbxXmlParser"

    @Volatile
    private var binaryXmlProbeDone = false

    @Volatile
    private var binaryXmlSupported = false

    fun isAbxMagic(bytes: ByteArray): Boolean =
        bytes.size >= 3 &&
            bytes[0] == 'A'.code.toByte() &&
            bytes[1] == 'B'.code.toByte() &&
            bytes[2] == 'X'.code.toByte()

    fun looksLikeTextXml(content: String): Boolean {
        val trimmed = content.trimStart()
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<")
    }

    /** Framework ABX APIs or shell [abx2xml] (ShortCuts-style on Flyme/OEM). */
    fun canReadShortcutServiceXml(): Boolean =
        isBinaryXmlSupported() || ShortcutSystemFileReader.isAbx2XmlShellAvailable()

    /** Whether this device can parse ABX via hidden framework APIs (false on many Flyme/OEM builds). */
    fun isBinaryXmlSupported(): Boolean {
        if (binaryXmlProbeDone) return binaryXmlSupported
        synchronized(this) {
            if (binaryXmlProbeDone) return binaryXmlSupported
            binaryXmlSupported = probeBinaryXmlSupport()
            binaryXmlProbeDone = true
            Log.i(TAG, "binaryXmlSupported=$binaryXmlSupported")
        }
        return binaryXmlSupported
    }

    fun openPullParser(bytes: ByteArray): XmlPullParser? {
        if (bytes.isEmpty()) return null
        if (isAbxMagic(bytes) && !isBinaryXmlSupported()) return null
        openViaResolvePullParser(ByteArrayInputStream(bytes))?.let { return it }
        if (isAbxMagic(bytes)) {
            openBinaryPullParser(bytes)?.let { return it }
        }
        if (looksLikeTextXml(bytes.decodeToString())) {
            return openTextPullParser(bytes.decodeToString())
        }
        return null
    }

    private fun probeBinaryXmlSupport(): Boolean {
        val sample = byteArrayOf(
            'A'.code.toByte(),
            'B'.code.toByte(),
            'X'.code.toByte(),
            0x01,
        )
        return openViaResolvePullParser(ByteArrayInputStream(sample)) != null ||
            openBinaryPullParser(sample) != null
    }

    private fun openViaResolvePullParser(input: InputStream): XmlPullParser? =
        runCatching {
            val xmlClass = Class.forName("android.util.Xml")
            val method = xmlClass.getMethod("resolvePullParser", InputStream::class.java)
            method.invoke(null, input) as XmlPullParser
        }.getOrNull()

    private fun openBinaryPullParser(bytes: ByteArray): XmlPullParser? {
        openBinaryPullParserViaNewMethod()?.let { parser ->
            runCatching {
                parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            }.onFailure { return null }
            return parser
        }
        return openBinaryPullParserViaInnerClass(bytes)
    }

    private fun openBinaryPullParserViaNewMethod(): XmlPullParser? =
        runCatching {
            val xmlClass = Class.forName("android.util.Xml")
            val method = xmlClass.getMethod("newBinaryPullParser")
            method.invoke(null) as XmlPullParser
        }.getOrNull()

    private fun openBinaryPullParserViaInnerClass(bytes: ByteArray): XmlPullParser? =
        runCatching {
            val parserClass = Class.forName("android.util.Xml\$BinaryXmlPullParser")
            val parser = parserClass.getConstructor().newInstance() as XmlPullParser
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            parser
        }.getOrNull()

    private fun openTextPullParser(xml: String): XmlPullParser? =
        runCatching {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            factory.newPullParser().apply {
                setInput(java.io.StringReader(xml))
            }
        }.getOrNull()
}
