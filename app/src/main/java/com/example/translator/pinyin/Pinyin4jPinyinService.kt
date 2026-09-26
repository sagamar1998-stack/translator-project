package com.example.translator.pinyin

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * Offline pinyin generation using the pinyin4j library.
 */
class Pinyin4jPinyinService : PinyinService {

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_MARK
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }

    override fun toPinyin(chineseText: String): String {
        if (chineseText.isBlank()) return ""

        val syllables = mutableListOf<String>()
        for (char in chineseText) {
            if (isChinese(char)) {
                val pinyin = PinyinHelper.toHanyuPinyinStringArray(char, format)
                    ?.firstOrNull()
                if (!pinyin.isNullOrBlank()) {
                    syllables += pinyin
                }
            }
        }
        return syllables.joinToString(" ")
    }

    private fun isChinese(char: Char): Boolean =
        Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN

    companion object {
        /** Eager singleton — pinyin4j has no async init. */
        val instance: PinyinService by lazy { Pinyin4jPinyinService() }
    }
}
