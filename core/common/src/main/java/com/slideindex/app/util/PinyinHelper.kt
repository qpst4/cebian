package com.slideindex.app.util

import net.sourceforge.pinyin4j.PinyinHelper as Pinyin4j
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

object PinyinHelper {
    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    fun firstLetter(label: String): Char {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return '#'
        val c = trimmed.first()
        return when {
            c in 'A'..'Z' -> c
            c in 'a'..'z' -> c.uppercaseChar()
            isChinese(c) -> {
                val pinyin = runCatching { Pinyin4j.toHanyuPinyinStringArray(c, format) }
                    .getOrNull()
                    ?.firstOrNull()
                pinyin?.firstOrNull()?.uppercaseChar() ?: '#'
            }
            c.isDigit() -> '#'
            else -> '#'
        }
    }

    fun sortKey(label: String): String {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return "#"
        val builder = StringBuilder()
        trimmed.forEach { ch ->
            when {
                ch in 'A'..'Z' || ch in 'a'..'z' -> builder.append(ch.lowercaseChar())
                isChinese(ch) -> {
                    val pinyin = runCatching { Pinyin4j.toHanyuPinyinStringArray(ch, format) }
                        .getOrNull()
                        ?.firstOrNull()
                    if (pinyin != null) builder.append(pinyin)
                }
                ch.isDigit() -> builder.append(ch)
            }
        }
        return builder.toString().ifEmpty { "#" }
    }

    fun initialKey(label: String): String {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return "#"
        val builder = StringBuilder()
        trimmed.forEach { ch ->
            when {
                ch in 'A'..'Z' || ch in 'a'..'z' -> builder.append(ch.lowercaseChar())
                isChinese(ch) -> {
                    val pinyin = runCatching { Pinyin4j.toHanyuPinyinStringArray(ch, format) }
                        .getOrNull()
                        ?.firstOrNull()
                    if (!pinyin.isNullOrEmpty()) builder.append(pinyin.first().lowercaseChar())
                }
                ch.isDigit() -> builder.append(ch)
            }
        }
        return builder.toString().ifEmpty { "#" }
    }

    private fun isChinese(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }
}
