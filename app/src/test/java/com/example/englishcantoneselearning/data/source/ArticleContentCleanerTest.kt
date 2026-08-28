package com.example.englishcantoneselearning.data.source

import com.example.englishcantoneselearning.model.ArticleSourceDefinition
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.MaterialTopic
import com.example.englishcantoneselearning.model.SourceCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleContentCleanerTest {
    private val definition = ArticleSourceDefinition(
        "test", "Publisher", "https://example.com/feed", MaterialLanguage.ENGLISH,
        MaterialTopic.entries.toSet(), setOf("example.com"), listOf("article"),
    )

    @Test
    fun removesBoilerplatePreservesHeadingsOrderAndDeduplicatesParagraphs() {
        val useful = "This paragraph explains a useful fact in enough detail for a listening exercise, while preserving the original article order."
        val html = buildString {
            append("<html><nav>Navigation item</nav><article><h2>First section</h2>")
            repeat(9) { append("<p>$useful Number $it adds a distinct and important detail for the learner.</p>") }
            append("<p>$useful Number 1 adds a distinct and important detail for the learner.</p>")
            append("<div class='advertisement'><p>Advertisement should disappear completely from this article body.</p></div>")
            append("<footer>Copyright and subscribe to our newsletter</footer></article></html>")
        }

        val snapshot = ArticleContentCleaner().clean(
            definition,
            SourceCandidate("test", "Publisher", "Title", "https://example.com/article", null, ""),
            html,
            fetchedAt = 10L,
        )

        assertEquals(9, snapshot.paragraphs.size)
        assertEquals("First section", snapshot.paragraphs.first().heading)
        assertEquals("p001", snapshot.paragraphs.first().id)
        assertFalse(snapshot.paragraphs.any { "Advertisement" in it.text || "Copyright" in it.text })
        assertEquals(64, snapshot.contentHash.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPageWithoutEnoughMainText() {
        ArticleContentCleaner().clean(
            definition,
            SourceCandidate("test", "Publisher", "Title", "https://example.com/article", null, ""),
            "<html><article><p>Too short.</p></article></html>",
        )
    }
}
