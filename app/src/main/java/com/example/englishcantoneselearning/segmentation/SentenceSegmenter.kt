package com.example.englishcantoneselearning.segmentation

import com.example.englishcantoneselearning.model.LearningLanguage
import com.example.englishcantoneselearning.model.SentenceItem
import java.util.concurrent.atomic.AtomicLong

interface SentenceSegmenter {
    fun segment(text: String, language: LearningLanguage): List<SentenceItem>
}

class RuleBasedSentenceSegmenter(
    private val idSource: AtomicLong = AtomicLong(1),
) : SentenceSegmenter {

    override fun segment(text: String, language: LearningLanguage): List<SentenceItem> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        return normalized
            .split('\n')
            .flatMap { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    emptyList()
                } else {
                    when (language) {
                        LearningLanguage.ENGLISH -> splitEnglishLine(trimmed)
                        LearningLanguage.CANTONESE -> splitCantoneseLine(trimmed)
                    }
                }
            }
            .mapNotNull { raw ->
                raw.trim().takeIf(String::isNotEmpty)?.let { sentence ->
                    SentenceItem(id = idSource.getAndIncrement(), text = sentence)
                }
            }
    }

    private fun splitEnglishLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var sentenceStart = 0
        var index = 0

        while (index < line.length) {
            val char = line[index]
            val isBoundary = when (char) {
                '!', '?' -> true
                '.' -> shouldSplitAtEnglishPeriod(line, index)
                else -> false
            }

            if (!isBoundary) {
                index++
                continue
            }

            var end = index + 1
            while (end < line.length && line[end] in ENGLISH_TERMINATORS) end++
            while (end < line.length && line[end] in CLOSING_CHARACTERS) end++
            result += line.substring(sentenceStart, end)
            sentenceStart = end
            index = end
        }

        if (sentenceStart < line.length) result += line.substring(sentenceStart)
        return result
    }

    private fun splitCantoneseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var sentenceStart = 0
        var index = 0

        while (index < line.length) {
            if (line[index] !in CANTONESE_TERMINATORS) {
                index++
                continue
            }

            var end = index + 1
            while (end < line.length && line[end] in CANTONESE_TERMINATORS) end++
            while (end < line.length && line[end] in CLOSING_CHARACTERS) end++
            result += line.substring(sentenceStart, end)
            sentenceStart = end
            index = end
        }

        if (sentenceStart < line.length) result += line.substring(sentenceStart)
        return result
    }

    private fun shouldSplitAtEnglishPeriod(text: String, periodIndex: Int): Boolean {
        val previous = text.getOrNull(periodIndex - 1)
        val immediateNext = text.getOrNull(periodIndex + 1)

        if (previous?.isDigit() == true && immediateNext?.isDigit() == true) return false
        if (previous?.isLetter() == true && immediateNext?.isLetter() == true) return false

        var tokenStart = periodIndex - 1
        while (tokenStart >= 0 && (text[tokenStart].isLetter() || text[tokenStart] == '.')) {
            tokenStart--
        }
        val token = text.substring(tokenStart + 1, periodIndex + 1).lowercase()
        if (token in NON_BREAKING_ABBREVIATIONS) return false

        val nextNonWhitespace = text.indexOfFirstFrom(periodIndex + 1) { !it.isWhitespace() }
        if (previous?.isUpperCase() == true && token.length == 2 && nextNonWhitespace >= 0) {
            return false
        }

        return true
    }

    private inline fun String.indexOfFirstFrom(startIndex: Int, predicate: (Char) -> Boolean): Int {
        for (index in startIndex until length) {
            if (predicate(this[index])) return index
        }
        return -1
    }

    private companion object {
        val ENGLISH_TERMINATORS = setOf('.', '!', '?')
        val CANTONESE_TERMINATORS = setOf('。', '！', '？', '；', '…', '.', '!', '?', ';')
        val CLOSING_CHARACTERS = setOf('"', '\'', '”', '’', ')', ']', '}', '》', '」', '』')
        val NON_BREAKING_ABBREVIATIONS = setOf(
            "mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.", "st.", "vs.",
            "e.g.", "i.e.", "a.m.", "p.m.",
        )
    }
}
