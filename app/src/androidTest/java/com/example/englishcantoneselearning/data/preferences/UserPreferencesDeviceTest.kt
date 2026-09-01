package com.example.englishcantoneselearning.data.preferences

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishcantoneselearning.model.LearnerProfile
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.SpeechLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesDeviceTest {
    @Test
    fun learningAndArticleLibraryPreferencesPersistAcrossInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = context.getSharedPreferences("learner_preferences", Context.MODE_PRIVATE)
        val original = stored.all.toMap()

        try {
            stored.edit()
                .putFloat("english_speaking", 5f)
                .putFloat("english_writing", 6f)
                .putFloat("english_reading", 7f)
                .commit()

            val first = UserPreferences(context)
            first.setListeningBand(6.26f)
            first.setArticleLibraryLanguage(MaterialLanguage.CANTONESE)
            val reopened = UserPreferences(context)

            assertEquals(LearnerProfile(6.5f), reopened.learnerProfile())
            assertEquals(MaterialLanguage.CANTONESE, reopened.articleLibraryLanguage())
            assertFalse(stored.contains("english_speaking"))
            assertFalse(stored.contains("english_writing"))
            assertFalse(stored.contains("english_reading"))
        } finally {
            val editor = stored.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
            }
            editor.commit()
        }
    }

    @Test
    fun speechSpeedsResetOnceThenPersistAndPublishChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = context.getSharedPreferences("learner_preferences", Context.MODE_PRIVATE)
        val original = stored.all.toMap()

        try {
            stored.edit()
                .putFloat("speech_speed_english_us", 1.4f)
                .putFloat("speech_speed_cantonese_hk", 1.3f)
                .putFloat("speech_speed_mandarin_cn", 1.2f)
                .remove("speech_speed_defaults_migrated_v2")
                .commit()

            val migrated = UserPreferences(context)
            SpeechLanguage.entries.forEach { language ->
                assertEquals(0.8f, migrated.speechSpeed(language))
            }

            migrated.setSpeechSpeed(SpeechLanguage.ENGLISH_US, 1.26f)
            assertEquals(1.3f, migrated.speechSpeeds.value.english)

            val reopened = UserPreferences(context)
            assertEquals(1.3f, reopened.speechSpeed(SpeechLanguage.ENGLISH_US))
            assertEquals(0.8f, reopened.speechSpeed(SpeechLanguage.CANTONESE_HK))
            assertEquals(0.8f, reopened.speechSpeed(SpeechLanguage.MANDARIN_CN))
        } finally {
            val editor = stored.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
            }
            editor.commit()
        }
    }
}
