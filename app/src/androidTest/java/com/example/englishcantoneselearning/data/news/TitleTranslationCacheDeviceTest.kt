package com.example.englishcantoneselearning.data.news

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishcantoneselearning.model.MaterialLanguage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TitleTranslationCacheDeviceTest {
    @Test
    fun keyDimensionsPersistAndExpiredEntriesAreRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = context.getSharedPreferences("news_title_translation_cache", Context.MODE_PRIVATE)
        val original = stored.all.toMap()
        var now = 1_800_000_000_000L

        try {
            stored.edit().clear().commit()
            val cache = SharedPreferencesTitleTranslationCache(context) { now }
            cache.put("v1", MaterialLanguage.ENGLISH, "https://example.com/a", "Title", "译题")

            assertEquals(
                "译题",
                SharedPreferencesTitleTranslationCache(context) { now }.get(
                    "v1",
                    MaterialLanguage.ENGLISH,
                    "https://example.com/a",
                    "Title",
                ),
            )
            assertNull(cache.get("v2", MaterialLanguage.ENGLISH, "https://example.com/a", "Title"))
            assertNull(cache.get("v1", MaterialLanguage.CANTONESE, "https://example.com/a", "Title"))
            assertNull(cache.get("v1", MaterialLanguage.ENGLISH, "https://example.com/b", "Title"))
            assertNull(cache.get("v1", MaterialLanguage.ENGLISH, "https://example.com/a", "Changed"))

            now += 7L * 24 * 60 * 60 * 1_000 + 1
            assertNull(cache.get("v1", MaterialLanguage.ENGLISH, "https://example.com/a", "Title"))
            assertEquals(0, JSONObject(stored.getString("entries", "{}")!!).length())

            stored.edit().putString("entries", "damaged-json").commit()
            assertNull(cache.get("v1", MaterialLanguage.ENGLISH, "https://example.com/a", "Title"))
            assertEquals(0, JSONObject(stored.getString("entries", "{}")!!).length())
        } finally {
            restore(stored, original)
        }
    }

    @Test
    fun onlyFourHundredNewestTitlesAreKept() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = context.getSharedPreferences("news_title_translation_cache", Context.MODE_PRIVATE)
        val original = stored.all.toMap()
        var now = 1_800_000_000_000L

        try {
            stored.edit().clear().commit()
            val cache = SharedPreferencesTitleTranslationCache(context) { now }
            repeat(401) { index ->
                cache.put(
                    "v1",
                    MaterialLanguage.ENGLISH,
                    "https://example.com/$index",
                    "Title $index",
                    "译题 $index",
                )
                now += 1
            }

            assertEquals(400, JSONObject(stored.getString("entries", "{}")!!).length())
            assertNull(cache.get("v1", MaterialLanguage.ENGLISH, "https://example.com/0", "Title 0"))
            assertEquals(
                "译题 400",
                cache.get("v1", MaterialLanguage.ENGLISH, "https://example.com/400", "Title 400"),
            )
            assertTrue(stored.contains("entries"))
        } finally {
            restore(stored, original)
        }
    }

    private fun restore(
        stored: android.content.SharedPreferences,
        original: Map<String, *>,
    ) {
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
