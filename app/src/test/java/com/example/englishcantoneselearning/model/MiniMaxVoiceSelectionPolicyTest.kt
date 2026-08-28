package com.example.englishcantoneselearning.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniMaxVoiceSelectionPolicyTest {
    @Test
    fun englishKeepsGenericAmericanAndBritishButRejectsOtherAccents() {
        val candidates = listOf(
            BuiltInMiniMaxVoices.find("Serene_Woman")!!,
            BuiltInMiniMaxVoices.find("English_Aussie_Bloke")!!,
            system("new-us", "American Presenter"),
            system("new-uk", "British Narrator"),
            system("new-indian", "Indian English"),
            system("new-irish", "Irish English"),
            system("new-south-african", "South African English"),
            system("new-canadian", "Canadian English"),
            system("new-kiwi", "New Zealand English"),
        )

        val ids = MiniMaxVoiceSelectionPolicy.selectableVoices(SpeechLanguage.ENGLISH_US, candidates)
            .map { it.id }

        assertTrue("Serene_Woman" in ids)
        assertTrue("new-us" in ids)
        assertTrue("new-uk" in ids)
        assertFalse("English_Aussie_Bloke" in ids)
        assertFalse("new-indian" in ids)
        assertFalse("new-irish" in ids)
        assertFalse("new-south-african" in ids)
        assertFalse("new-canadian" in ids)
        assertFalse("new-kiwi" in ids)
    }

    @Test
    fun allLanguagesOnlyExposeCorrectOfficialSystemVoices() {
        val catalog = BuiltInMiniMaxVoices.voices + listOf(
            MiniMaxVoice("clone", "Clone", MiniMaxVoiceKind.CLONED, setOf(SpeechLanguage.ENGLISH_US)),
            MiniMaxVoice("designed", "Designed", MiniMaxVoiceKind.DESIGNED, setOf(SpeechLanguage.CANTONESE_HK)),
            MiniMaxVoice("favorite", "Favorite", MiniMaxVoiceKind.CUSTOM_FAVORITE, setOf(SpeechLanguage.MANDARIN_CN)),
            MiniMaxVoice("unknown", "Unknown", MiniMaxVoiceKind.UNKNOWN),
            system("unclassified-system", "Unclassified Voice"),
        )

        val english = MiniMaxVoiceSelectionPolicy.selectableVoices(SpeechLanguage.ENGLISH_US, catalog)
        val cantonese = MiniMaxVoiceSelectionPolicy.selectableVoices(SpeechLanguage.CANTONESE_HK, catalog)
        val mandarin = MiniMaxVoiceSelectionPolicy.selectableVoices(SpeechLanguage.MANDARIN_CN, catalog)

        assertTrue(english.isNotEmpty())
        assertTrue(cantonese.isNotEmpty())
        assertTrue(mandarin.isNotEmpty())
        assertTrue(english.all { it.kind == MiniMaxVoiceKind.SYSTEM })
        assertTrue(english.filter { BuiltInMiniMaxVoices.find(it.id) != null }
            .all { SpeechLanguage.ENGLISH_US in it.supportedLanguages })
        assertTrue(cantonese.all { it.kind == MiniMaxVoiceKind.SYSTEM && SpeechLanguage.CANTONESE_HK in it.supportedLanguages })
        assertTrue(mandarin.all { it.kind == MiniMaxVoiceKind.SYSTEM && SpeechLanguage.MANDARIN_CN in it.supportedLanguages })
        assertTrue(listOf(english, cantonese, mandarin).flatten().none { it.id in setOf("clone", "designed", "favorite", "unknown", "unclassified-system") })
    }

    @Test
    fun fallbackIdsAreStableOfficialSystemVoices() {
        assertEquals("Serene_Woman", MiniMaxVoiceSelectionPolicy.fallbackVoiceId(SpeechLanguage.ENGLISH_US))
        assertEquals("Cantonese_GentleLady", MiniMaxVoiceSelectionPolicy.fallbackVoiceId(SpeechLanguage.CANTONESE_HK))
        assertEquals("female-tianmei", MiniMaxVoiceSelectionPolicy.fallbackVoiceId(SpeechLanguage.MANDARIN_CN))
    }

    private fun system(id: String, name: String) = MiniMaxVoice(id, name, MiniMaxVoiceKind.SYSTEM)
}
