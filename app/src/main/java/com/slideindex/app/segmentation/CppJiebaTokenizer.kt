package com.slideindex.app.segmentation

import android.content.Context
import com.slideindex.app.BuildConfig
import java.io.File
import java.io.FileOutputStream

/**
 * Local cppjieba MixSegment wrapper used by pick-result word chips.
 */
class CppJiebaTokenizer private constructor(
    private val appContext: Context,
) {
    private var initialized = false

    fun segment(text: String): IntArray? {
        if (text.isEmpty()) return null
        ensureInitialized()
        val tokenSpans = nativeCut(text) ?: return null
        if (tokenSpans.isEmpty()) return null
        return buildSegments(text, tokenSpans)
    }

    fun cutWords(text: String): List<String> {
        val segment = segment(text) ?: return emptyList()
        return extractWordTokens(text, segment)
    }

    fun warmUp() {
        ensureInitialized()
    }

    private fun ensureInitialized() {
        if (initialized) return
        val dictDir = ensureDictDirectory()
        check(nativeInit(dictDir.absolutePath)) { "cppjieba init failed" }
        if (BuildConfig.DEBUG) {
            selfCheck()
        }
        initialized = true
    }

    private fun ensureDictDirectory(): File {
        val dictDir = File(appContext.filesDir, DICT_DIR)
        if (hasRequiredFiles(dictDir)) {
            return dictDir
        }
        copyAssetDirectory(DICT_DIR, dictDir)
        check(hasRequiredFiles(dictDir)) { "cppjieba dict files missing" }
        return dictDir
    }

    private fun hasRequiredFiles(dictDir: File): Boolean {
        if (!dictDir.isDirectory) return false
        return REQUIRED_DICT_FILES.all { fileName ->
            File(dictDir, fileName).isFile
        }
    }

    private fun copyAssetDirectory(assetPath: String, targetDir: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                FileOutputStream(targetDir).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }
        targetDir.mkdirs()
        children.forEach { child ->
            copyAssetDirectory("$assetPath/$child", File(targetDir, child))
        }
    }

    private fun selfCheck() {
        val tokenSpans = nativeCut("我来到北京清华大学")
        check(tokenSpans != null && tokenSpans.size >= 2 && tokenSpans.size % 2 == 0) {
            "cppjieba self-check failed"
        }
    }

    private fun buildSegments(text: String, tokenSpans: IntArray): IntArray? {
        val words = ArrayList<Int>()
        val punctuations = ArrayList<Int>()
        var cursor = 0
        var index = 0
        while (index < tokenSpans.size) {
            val start = tokenSpans[index]
            val endInclusive = tokenSpans[index + 1]
            if (start < cursor || endInclusive < start || endInclusive >= text.length) {
                index += 2
                continue
            }
            appendGapPunctuation(text, cursor, start, punctuations)
            if (hasWordCodePoint(text, start, endInclusive + 1)) {
                words.add(start)
                words.add(endInclusive)
            } else {
                appendPunctuation(text, start, endInclusive + 1, punctuations)
            }
            cursor = endInclusive + 1
            index += 2
        }
        appendGapPunctuation(text, cursor, text.length, punctuations)
        if (words.isEmpty() && punctuations.isEmpty()) return null
        return IntArray(words.size + punctuations.size + 1) { offset ->
            when {
                offset < words.size -> words[offset]
                offset == words.size -> -1
                else -> punctuations[offset - words.size - 1]
            }
        }
    }

    private fun appendGapPunctuation(
        text: String,
        start: Int,
        end: Int,
        out: ArrayList<Int>,
    ) {
        if (start < end) {
            appendPunctuation(text, start, end, out)
        }
    }

    private fun appendPunctuation(
        text: String,
        start: Int,
        end: Int,
        out: ArrayList<Int>,
    ) {
        var index = start
        while (index < end) {
            val codePoint = text.codePointAt(index)
            val next = index + Character.charCount(codePoint)
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                out.add(index)
                out.add(next - 1)
            }
            index = next
        }
    }

    private fun hasWordCodePoint(text: String, start: Int, end: Int): Boolean {
        var index = start
        while (index < end) {
            val codePoint = text.codePointAt(index)
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_'.code) {
                return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }

    private external fun nativeInit(dictDir: String): Boolean
    private external fun nativeCut(text: String): IntArray?

    companion object {
        private const val DICT_DIR = "dict"
        private val REQUIRED_DICT_FILES = arrayOf(
            "jieba.dict.utf8",
            "hmm_model.utf8",
            "user.dict.utf8",
        )

        @Volatile
        private var instance: CppJiebaTokenizer? = null

        @Volatile
        private var nativeLoaded = false

        init {
            nativeLoaded = runCatching {
                System.loadLibrary("slideindex_jieba")
                true
            }.getOrDefault(false)
        }

        fun get(context: Context): CppJiebaTokenizer {
            check(nativeLoaded) { "slideindex_jieba native library unavailable" }
            return instance ?: synchronized(this) {
                instance ?: CppJiebaTokenizer(context.applicationContext).also { instance = it }
            }
        }

        fun isNativeAvailable(): Boolean = nativeLoaded

        fun extractWordTokens(text: String, segment: IntArray): List<String> {
            val separatorIndex = segment.indexOfFirst { it == -1 }.takeIf { it >= 0 } ?: segment.size
            val words = ArrayList<String>()
            var index = 0
            while (index + 1 < separatorIndex) {
                val start = segment[index]
                val endInclusive = segment[index + 1]
                if (start in 0..endInclusive && endInclusive < text.length) {
                    words.add(text.substring(start, endInclusive + 1))
                }
                index += 2
            }
            return words
        }
    }
}
