package com.example.translator.viewmodel

/**
 * One translated exchange in the running conversation.
 */
data class ConversationEntry(
    val id: Long = System.nanoTime(),
    val english: String,
    val translatedText: String,
    /** Romanization shown above [translatedText]; null when target language is not Chinese. */
    val pinyin: String? = null,
)
