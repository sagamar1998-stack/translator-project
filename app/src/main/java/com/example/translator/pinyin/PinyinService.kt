package com.example.translator.pinyin

/**
 * Converts Chinese text to readable pinyin locally (no network).
 */
interface PinyinService {

    /**
     * Returns pinyin for [chineseText] with tone marks, or an empty string when
     * the input contains no Chinese characters.
     */
    fun toPinyin(chineseText: String): String
}
