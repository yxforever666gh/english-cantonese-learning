package com.example.englishcantoneselearning.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServiceConfigStoreTest {
    @Test
    fun endpointsAcceptHostOrV1WithoutDuplicatingPath() {
        assertEquals(
            "https://example.com/v1/models",
            ServiceConfigStore.endpoint(" https://example.com/ ", "models"),
        )
        assertEquals(
            "https://example.com/v1/responses",
            ServiceConfigStore.endpoint("https://example.com/v1/", "/responses"),
        )
    }

    @Test
    fun productionAddressesRequireHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            ServiceConfigStore.normalizeBaseUrl("http://example.com")
        }
        assertEquals(
            "http://127.0.0.1:8080",
            ServiceConfigStore.normalizeBaseUrl("http://127.0.0.1:8080/"),
        )
    }

    @Test
    fun apiKeysRemovePastedLineBreaksAndOtherWhitespace() {
        assertEquals(
            "sk-example-key",
            ServiceConfigStore.sanitizeApiKey("  sk-exam\r\n准ple-\tkey  "),
        )
    }
}
