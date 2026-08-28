package com.example.englishcantoneselearning.speech

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioCacheDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        File(context.filesDir, "tts_audio_cache").deleteRecursively()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "tts_audio_cache").deleteRecursively()
    }

    @Test
    fun cachePersistsAcrossInstancesAndEvictsLeastRecentlyUsedFiles() {
        val cache = AudioCache(context, maxBytes = 5)
        cache.put("old", byteArrayOf(1, 2, 3))
        Thread.sleep(5)
        cache.put("new", byteArrayOf(4, 5, 6))

        assertNull(cache.get("old"))
        val restored = AudioCache(context, maxBytes = 5).get("new")
        assertArrayEquals(byteArrayOf(4, 5, 6), restored?.readBytes())
        assertEquals(3L, cache.sizeBytes())
    }
}
