package com.example.englishcantoneselearning.data.network

import android.util.Log
import com.example.englishcantoneselearning.model.SourceReference
import org.json.JSONArray
import org.json.JSONObject

object MaterialResponseParser {
    fun parse(responseBody: String): GeneratedBatch {
        val normalizedBody = normalizeResponseBody(responseBody)
        val root = runCatching { JSONObject(normalizedBody) }
            .getOrElse { throw GatewayFormatException("网关返回的不是有效 JSON") }
        root.optJSONObject("error")?.let {
            throw GatewayFormatException(it.optString("message", "模型生成失败"))
        }
        if (root.optString("status") == "incomplete") {
            val reason = root.optJSONObject("incomplete_details")
                ?.optString("reason")
                ?.takeIf { it.isNotBlank() }
            throw GatewayFormatException(
                if (reason == null) "模型输出未完成，本批材料未保存"
                else "模型输出未完成（$reason），本批材料未保存",
            )
        }

        val outputText = extractOutputText(root)
            ?: throw GatewayFormatException("模型响应中没有可读取的材料")
        val materialJson = runCatching { JSONObject(stripCodeFence(outputText)) }
            .getOrElse { throw GatewayFormatException("模型没有返回约定的材料 JSON") }
        val materialsArray = materialJson.optJSONArray("materials")
            ?: throw GatewayFormatException("材料 JSON 缺少 materials")

        val materials = buildList {
            for (index in 0 until materialsArray.length()) {
                val item = materialsArray.optJSONObject(index)
                    ?: throw GatewayFormatException("第 ${index + 1} 篇材料格式错误")
                add(parseMaterial(item))
            }
        }
        val usage = root.optJSONObject("usage")
        val searchSourceUrls = collectSearchSourceUrls(root)
        logResponseShape(responseBody, root, materials.size, searchSourceUrls.size)
        return GeneratedBatch(
            responseId = root.optString("id"),
            inputTokens = usage?.optInt("input_tokens") ?: 0,
            outputTokens = usage?.optInt("output_tokens") ?: 0,
            materials = materials,
            webSourceUrls = searchSourceUrls,
        )
    }

    private fun normalizeResponseBody(responseBody: String): String {
        val trimmed = responseBody.trim()
        if (trimmed.startsWith("{")) return trimmed
        if (!trimmed.lineSequence().any { it.startsWith("data:") }) {
            throw GatewayFormatException("网关返回的不是 JSON 或 SSE")
        }

        var completedResponse: JSONObject? = null
        var finalResponse: JSONObject? = null
        val streamedSearchUrls = linkedSetOf<String>()
        for (eventData in sseDataEvents(trimmed)) {
            if (eventData == "[DONE]") continue
            val event = runCatching { JSONObject(eventData) }.getOrNull() ?: continue
            collectStreamedSearchUrls(event, streamedSearchUrls)
            when (event.optString("type")) {
                "response.completed" -> {
                    completedResponse = event.optJSONObject("response")
                        ?: throw GatewayFormatException("流式完成事件缺少 response")
                }
                "response.failed", "response.incomplete", "error" -> {
                    throw GatewayFormatException(streamErrorMessage(event))
                }
            }
            if (event.has("output") || event.has("output_text")) finalResponse = event
        }
        val result = completedResponse ?: finalResponse
            ?: throw GatewayFormatException("流式响应结束，但没有完整的模型结果")
        if (streamedSearchUrls.isNotEmpty()) {
            val output = result.optJSONArray("output") ?: JSONArray().also { result.put("output", it) }
            output.put(
                JSONObject()
                    .put("type", "web_search_call")
                    .put(
                        "action",
                        JSONObject().put(
                            "sources",
                            JSONArray().apply {
                                streamedSearchUrls.forEach { put(JSONObject().put("url", it)) }
                            },
                        ),
                    ),
            )
        }
        return result.toString()
    }

    private fun collectStreamedSearchUrls(event: JSONObject, urls: MutableSet<String>) {
        val eventType = event.optString("type")
        val item = event.optJSONObject("item")
        when {
            "web_search" in eventType -> collectUrls(event, urls)
            item?.optString("type") == "web_search_call" -> collectUrls(item, urls)
            eventType.contains("annotation") -> collectUrls(event, urls)
        }
    }

    private fun sseDataEvents(body: String): List<String> {
        val events = mutableListOf<String>()
        val dataLines = mutableListOf<String>()
        fun flush() {
            if (dataLines.isNotEmpty()) {
                events += dataLines.joinToString("\n")
                dataLines.clear()
            }
        }
        body.lineSequence().forEach { line ->
            when {
                line.isBlank() -> flush()
                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
            }
        }
        flush()
        return events
    }

    private fun streamErrorMessage(event: JSONObject): String {
        val response = event.optJSONObject("response")
        val nestedError = event.optJSONObject("error")
            ?: response?.optJSONObject("error")
        val incompleteReason = response?.optJSONObject("incomplete_details")
            ?.optString("reason")
            ?.takeIf { it.isNotBlank() }
        return nestedError?.optString("message")?.takeIf { it.isNotBlank() }
            ?: incompleteReason?.let { "模型输出未完成（$it），本批材料未保存" }
            ?: event.optString("message").takeIf { it.isNotBlank() }
            ?: "模型流式生成失败（${event.optString("type", "unknown")}）"
    }

    private fun parseMaterial(item: JSONObject): GeneratedMaterial {
        val sentenceArray = item.optJSONArray("sentences")
            ?: throw GatewayFormatException("材料缺少逐句内容")
        val sentences = buildList {
            for (index in 0 until sentenceArray.length()) {
                val sentence = sentenceArray.optJSONObject(index)
                    ?: throw GatewayFormatException("句子格式错误")
                add(
                    GeneratedSentence(
                        targetText = sentence.optString("target_text").trim(),
                        jyutping = sentence.optString("jyutping").trim(),
                        simplifiedChinese = sentence.optString("simplified_chinese").trim(),
                    ),
                )
            }
        }
        val sourceArray = item.optJSONArray("sources")
            ?: throw GatewayFormatException("材料缺少来源")
        val sources = buildList {
            for (index in 0 until sourceArray.length()) {
                val source = sourceArray.optJSONObject(index)
                    ?: throw GatewayFormatException("来源格式错误")
                add(
                    SourceReference(
                        title = source.optString("title").trim(),
                        publisher = source.optString("publisher").trim(),
                        url = source.optString("url").trim(),
                        publishedAt = source.optString("published_at").trim().ifEmpty { null },
                        sourceLanguage = source.optString("source_language").trim(),
                    ),
                )
            }
        }
        val sections = buildList {
            val array = item.optJSONArray("sections") ?: JSONArray()
            for (index in 0 until array.length()) {
                val section = array.optJSONObject(index) ?: continue
                add(
                    GeneratedSection(
                        id = section.optString("id", "section-$index"),
                        title = section.optString("title"),
                        startSentenceIndex = section.optInt("start_sentence_index"),
                    ),
                )
            }
        }
        fun stringList(name: String): List<String> = buildList {
            val array = item.optJSONArray(name) ?: JSONArray()
            for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
        return GeneratedMaterial(
            title = item.optString("title").trim(),
            topic = item.optString("topic").trim(),
            difficulty = item.optString("difficulty").trim(),
            targetText = item.optString("target_text").trim(),
            sentences = sentences,
            sources = sources,
            sections = sections,
            outlineSections = stringList("outline_sections"),
            coveredSectionIds = stringList("covered_section_ids"),
            coveredParagraphIds = stringList("covered_paragraph_ids"),
            hasMore = item.optBoolean("has_more"),
            nextSectionIndex = item.optInt("next_section_index"),
        )
    }

    private fun extractOutputText(root: JSONObject): String? {
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = root.optJSONArray("output") ?: return null
        for (outputIndex in 0 until output.length()) {
            val item = output.optJSONObject(outputIndex) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val block = content.optJSONObject(contentIndex) ?: continue
                if (block.optString("type") == "output_text") {
                    block.optString("text").takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return null
    }

    private fun collectSearchSourceUrls(root: JSONObject): Set<String> {
        val urls = linkedSetOf<String>()
        val output = root.optJSONArray("output") ?: return urls
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            if (item.optString("type") == "web_search_call") collectUrls(item, urls)
            val content = item.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val annotations = content.optJSONObject(contentIndex)?.optJSONArray("annotations") ?: continue
                for (annotationIndex in 0 until annotations.length()) {
                    val annotation = annotations.optJSONObject(annotationIndex) ?: continue
                    if (annotation.optString("type") == "url_citation") {
                        annotation.optString("url").takeIf { it.startsWith("http") }?.let(urls::add)
                    }
                }
            }
        }
        return urls
    }

    private fun collectUrls(value: Any?, urls: MutableSet<String>) {
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                val child = value.opt(key)
                if (key == "url" && child is String && child.startsWith("http")) urls += child
                else collectUrls(child, urls)
            }
            is JSONArray -> for (index in 0 until value.length()) collectUrls(value.opt(index), urls)
            is String -> if (value.startsWith("https://") || value.startsWith("http://")) urls += value
        }
    }

    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun logResponseShape(
        rawBody: String,
        root: JSONObject,
        materialCount: Int,
        sourceCount: Int,
    ) {
        val eventTypes = if (rawBody.trimStart().startsWith("{")) {
            emptySet()
        } else {
            sseDataEvents(rawBody).mapNotNull { data ->
                runCatching { JSONObject(data).optString("type").takeIf(String::isNotBlank) }.getOrNull()
            }.toSet()
        }
        val outputTypes = buildSet {
            val output = root.optJSONArray("output") ?: return@buildSet
            for (index in 0 until output.length()) {
                output.optJSONObject(index)?.optString("type")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val message = "parsed materials=$materialCount sources=$sourceCount " +
            "outputTypes=${outputTypes.sorted().joinToString(",")} " +
            "eventTypes=${eventTypes.sorted().take(20).joinToString(",")}"
        runCatching { Log.i(LOG_TAG, message) }
    }

    private const val LOG_TAG = "MaterialResponseParser"
}
