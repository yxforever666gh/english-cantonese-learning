package com.example.englishcantoneselearning.data.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialResponseParserTest {
    @Test
    fun parsesStructuredMaterialsAndSearchSources() {
        val material = JSONObject()
            .put("title", "A short title")
            .put("topic", "科技")
            .put("difficulty", "EASY")
            .put("target_text", "A short text.")
            .put(
                "sentences",
                JSONArray().put(
                    JSONObject()
                        .put("target_text", "A short text.")
                        .put("jyutping", "")
                        .put("simplified_chinese", "一段短文。"),
                ),
            )
            .put(
                "sources",
                JSONArray().put(
                    JSONObject()
                        .put("title", "Source")
                        .put("publisher", "Publisher")
                        .put("url", "https://example.com/article?tracking=1")
                        .put("published_at", "2026-08-20")
                        .put("source_language", "English"),
                ),
            )
        val outputText = JSONObject().put("materials", JSONArray().put(material)).toString()
        val response = JSONObject()
            .put("id", "resp_123")
            .put("output_text", outputText)
            .put("usage", JSONObject().put("input_tokens", 12).put("output_tokens", 34))
            .put(
                "output",
                JSONArray().put(
                    JSONObject()
                        .put("type", "web_search_call")
                        .put(
                            "action",
                            JSONObject().put(
                                "sources",
                                JSONArray().put(JSONObject().put("url", "https://example.com/article")),
                            ),
                        ),
                ),
            )
            .toString()

        val parsed = MaterialResponseParser.parse(response)

        assertEquals("resp_123", parsed.responseId)
        assertEquals(12, parsed.inputTokens)
        assertEquals(34, parsed.outputTokens)
        assertEquals("A short title", parsed.materials.single().title)
        assertEquals("一段短文。", parsed.materials.single().sentences.single().simplifiedChinese)
        assertTrue("https://example.com/article" in parsed.webSourceUrls)
    }

    @Test(expected = GatewayFormatException::class)
    fun rejectsNonJsonOutput() {
        MaterialResponseParser.parse("{\"id\":\"resp\",\"output_text\":\"not json\"}")
    }

    @Test
    fun parsesCompletedSseResponse() {
        val completedResponse = JSONObject()
            .put("id", "resp_stream")
            .put("output_text", JSONObject().put("materials", JSONArray()).toString())
            .put("usage", JSONObject().put("input_tokens", 7).put("output_tokens", 9))
        val sse = buildString {
            append("event: response.created\n")
            append("data: {\"type\":\"response.created\"}\n\n")
            append("event: response.completed\n")
            append("data: ")
            append(JSONObject().put("type", "response.completed").put("response", completedResponse))
            append("\n\ndata: [DONE]\n\n")
        }

        val parsed = MaterialResponseParser.parse(sse)

        assertEquals("resp_stream", parsed.responseId)
        assertEquals(7, parsed.inputTokens)
        assertEquals(9, parsed.outputTokens)
    }

    @Test
    fun rejectsFailedSseResponse() {
        val sse = "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"message\":\"tool failed\"}}}\n\n"

        val error = org.junit.Assert.assertThrows(GatewayFormatException::class.java) {
            MaterialResponseParser.parse(sse)
        }

        assertEquals("tool failed", error.message)
    }

    @Test
    fun keepsSearchSourcesReportedByStreamingOutputItemEvents() {
        val completedResponse = JSONObject()
            .put("id", "resp_stream")
            .put("output_text", JSONObject().put("materials", JSONArray()).toString())
            .put("output", JSONArray())
        val webSearchItem = JSONObject()
            .put("type", "web_search_call")
            .put(
                "action",
                JSONObject().put(
                    "sources",
                    JSONArray().put(JSONObject().put("url", "https://example.com/from-stream")),
                ),
            )
        val sse = buildString {
            append("data: ")
            append(JSONObject().put("type", "response.output_item.done").put("item", webSearchItem))
            append("\n\ndata: ")
            append(JSONObject().put("type", "response.completed").put("response", completedResponse))
            append("\n\ndata: [DONE]\n\n")
        }

        val parsed = MaterialResponseParser.parse(sse)

        assertTrue("https://example.com/from-stream" in parsed.webSourceUrls)
    }

    @Test
    fun reportsIncompleteReason() {
        val incompleteResponse = JSONObject()
            .put("id", "resp_incomplete")
            .put("status", "incomplete")
            .put("incomplete_details", JSONObject().put("reason", "max_output_tokens"))
        val sse = "data: ${JSONObject().put("type", "response.incomplete").put("response", incompleteResponse)}\n\n"

        val error = org.junit.Assert.assertThrows(GatewayFormatException::class.java) {
            MaterialResponseParser.parse(sse)
        }

        assertTrue(error.message.orEmpty().contains("max_output_tokens"))
    }

    @Test
    fun acceptsGatewaySourceStringsAndLinkFields() {
        val outputText = JSONObject().put("materials", JSONArray()).toString()
        val root = JSONObject()
            .put("id", "resp_gateway_shape")
            .put("output_text", outputText)
            .put(
                "output",
                JSONArray().put(
                    JSONObject()
                        .put("type", "web_search_call")
                        .put(
                            "action",
                            JSONObject().put(
                                "sources",
                                JSONArray()
                                    .put("https://example.com/string-source")
                                    .put(JSONObject().put("link", "https://example.com/link-source")),
                            ),
                        ),
                ),
            )

        val parsed = MaterialResponseParser.parse(root.toString())

        assertTrue("https://example.com/string-source" in parsed.webSourceUrls)
        assertTrue("https://example.com/link-source" in parsed.webSourceUrls)
    }
}
