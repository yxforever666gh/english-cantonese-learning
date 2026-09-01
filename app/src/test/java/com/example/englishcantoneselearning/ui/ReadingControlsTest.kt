package com.example.englishcantoneselearning.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingControlsTest {
    @Test
    fun numericValuesClampAndRoundToConfiguredStep() {
        assertEquals(0.5f, normalizeNumericValue(0.1f, 0.5f..2f, 0.1f), 0f)
        assertEquals(2f, normalizeNumericValue(5f, 0.5f..2f, 0.1f), 0f)
        assertEquals(1.3f, normalizeNumericValue(1.26f, 0.5f..2f, 0.1f), 0f)
        assertEquals(16f, normalizeNumericValue(15.6f, 12f..32f, 1f), 0f)
    }

    @Test
    fun nonFiniteNumericValueFallsBackToMinimum() {
        assertEquals(12f, normalizeNumericValue(Float.NaN, 12f..32f, 1f), 0f)
        assertEquals(12f, normalizeNumericValue(Float.POSITIVE_INFINITY, 12f..32f, 1f), 0f)
    }
}
