package com.example.translator.di

import com.example.translator.pinyin.Pinyin4jPinyinService
import com.example.translator.pinyin.PinyinService

/** Dependency wiring for [PinyinService]. */
object PinyinModule {

    fun createPinyinService(): PinyinService = Pinyin4jPinyinService.instance
}
