package com.example.englishcantoneselearning.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesNormalizationTest {
    @Test
    fun readingFontSizeIsClampedToSupportedRange() {
        assertEquals(MIN_READING_FONT_SIZE_SP, normalizeReadingFontSizeSp(1))
        assertEquals(DEFAULT_READING_FONT_SIZE_SP, normalizeReadingFontSizeSp(16))
        assertEquals(MAX_READING_FONT_SIZE_SP, normalizeReadingFontSizeSp(100))
    }
}
