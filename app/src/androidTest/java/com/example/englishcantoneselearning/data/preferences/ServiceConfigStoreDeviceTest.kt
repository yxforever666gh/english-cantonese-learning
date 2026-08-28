package com.example.englishcantoneselearning.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishcantoneselearning.model.CustomVoiceFavorite
import com.example.englishcantoneselearning.model.MiniMaxTtsConfig
import com.example.englishcantoneselearning.model.MiniMaxVoice
import com.example.englishcantoneselearning.model.MiniMaxVoiceCatalog
import com.example.englishcantoneselearning.model.MiniMaxVoiceKind
import com.example.englishcantoneselearning.model.SpeechLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceConfigStoreDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun clearBefore() = clear()
    @After fun clearAfter() = clear()

    @Test fun defaultsAndVoiceSelectionsFavoritesAndCatalogSurviveRecreation() {
        val first = ServiceConfigStore(context)
        assertEquals("Serene_Woman", first.config().englishVoice)
        assertEquals("Cantonese_GentleLady", first.config().cantoneseVoice)
        assertEquals("female-tianmei", first.config().mandarinVoice)

        first.save(
            MiniMaxTtsConfig(
                apiKey = "private-test-key",
                englishVoice = "English_Graceful_Lady",
                cantoneseVoice = "Cantonese_KindWoman",
                mandarinVoice = "Chinese (Mandarin)_News_Anchor",
            ),
        )
        first.saveCatalog(
            MiniMaxVoiceCatalog(
                listOf(
                    MiniMaxVoice(
                        "English_Graceful_Lady",
                        "Graceful Lady",
                        MiniMaxVoiceKind.SYSTEM,
                        setOf(SpeechLanguage.ENGLISH_US),
                    ),
                ),
                fetchedAt = 99L,
            ),
        )
        first.saveFavorites(
            listOf(
                CustomVoiceFavorite(
                    "favorite-1",
                    "My voice",
                    "custom-voice-001",
                    setOf(SpeechLanguage.ENGLISH_US, SpeechLanguage.CANTONESE_HK),
                ),
            ),
        )

        val reopened = ServiceConfigStore(context)
        assertEquals("English_Graceful_Lady", reopened.config().englishVoice)
        assertEquals("Cantonese_KindWoman", reopened.config().cantoneseVoice)
        assertEquals("Chinese (Mandarin)_News_Anchor", reopened.config().mandarinVoice)
        assertEquals(99L, reopened.catalog()?.fetchedAt)
        assertEquals("custom-voice-001", reopened.favorites().single().voiceId)
    }

    @Test fun excludedAccentAndNonSystemSelectionsMigrateWithoutDeletingData() {
        val first = ServiceConfigStore(context)
        first.save(
            MiniMaxTtsConfig(
                apiKey = "preserved-private-key",
                englishVoice = "English_Aussie_Bloke",
                cantoneseVoice = "custom-cantonese",
                mandarinVoice = "designed-mandarin",
            ),
        )
        first.saveCatalog(
            MiniMaxVoiceCatalog(
                listOf(
                    MiniMaxVoice("English_Aussie_Bloke", "Aussie Bloke", MiniMaxVoiceKind.SYSTEM),
                    MiniMaxVoice("custom-cantonese", "My clone", MiniMaxVoiceKind.CLONED),
                    MiniMaxVoice("designed-mandarin", "My design", MiniMaxVoiceKind.DESIGNED),
                ),
                fetchedAt = 123L,
            ),
        )
        first.saveFavorites(
            listOf(
                CustomVoiceFavorite(
                    "favorite-legacy",
                    "Legacy favorite",
                    "custom-cantonese",
                    setOf(SpeechLanguage.CANTONESE_HK),
                ),
            ),
        )

        val reopened = ServiceConfigStore(context)

        assertEquals("Serene_Woman", reopened.config().englishVoice)
        assertEquals("Cantonese_GentleLady", reopened.config().cantoneseVoice)
        assertEquals("female-tianmei", reopened.config().mandarinVoice)
        assertEquals("preserved-private-key", reopened.config().apiKey)
        assertEquals(123L, reopened.catalog()?.fetchedAt)
        assertEquals("custom-cantonese", reopened.favorites().single().voiceId)
    }

    private fun clear() {
        context.getSharedPreferences("service_configs", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
