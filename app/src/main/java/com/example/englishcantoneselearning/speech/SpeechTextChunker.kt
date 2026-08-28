package com.example.englishcantoneselearning.speech

data class SpeechChunk(
    val text: String,
    val startOffset: Int,
)

object SpeechTextChunker {
    private val preferredBreaks = setOf(
        ' ', '\t', '\n', ',', '，', ';', '；', ':', '：', '、',
    )

    fun chunk(
        text: String,
        maxLength: Int,
        baseOffset: Int = 0,
    ): List<SpeechChunk> {
        require(maxLength > 0) { "maxLength must be positive" }
        if (text.isEmpty()) return emptyList()

        val result = mutableListOf<SpeechChunk>()
        var start = 0
        while (start < text.length) {
            val hardEnd = (start + maxLength).coerceAtMost(text.length)
            var end = hardEnd
            if (hardEnd < text.length) {
                val earliestPreferredBreak = start + maxLength / 2
                for (candidate in hardEnd downTo (earliestPreferredBreak + 1)) {
                    if (text[candidate - 1] in preferredBreaks) {
                        end = candidate
                        break
                    }
                }
            }

            result += SpeechChunk(
                text = text.substring(start, end),
                startOffset = baseOffset + start,
            )
            start = end
        }
        return result
    }
}
