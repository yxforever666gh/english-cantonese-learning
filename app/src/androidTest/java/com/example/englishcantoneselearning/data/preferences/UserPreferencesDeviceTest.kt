package com.example.englishcantoneselearning.data.preferences

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishcantoneselearning.model.MaterialLanguage
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
            first.setEnglishListening(6.26f)
            first.setArticleLibraryLanguage(MaterialLanguage.CANTONESE)
            val reopened = UserPreferences(context)

            assertEquals(6.5f, reopened.learnerProfile().englishListening)
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
}
