package com.example.englishcantoneselearning.data.source

import android.content.Context
import com.example.englishcantoneselearning.model.MaterialLanguage
import com.example.englishcantoneselearning.model.NewsItem
import com.example.englishcantoneselearning.model.NewsTag
import org.json.JSONArray
import org.json.JSONObject

data class CachedNewsFeed(
    val savedAt: Long,
    val items: List<NewsItem>,
)

interface NewsFeedCacheStore {
    fun load(language: MaterialLanguage): CachedNewsFeed?
    fun save(language: MaterialLanguage, feed: CachedNewsFeed)
}

object NoOpNewsFeedCacheStore : NewsFeedCacheStore {
    override fun load(language: MaterialLanguage): CachedNewsFeed? = null
    override fun save(language: MaterialLanguage, feed: CachedNewsFeed) = Unit
}

class SharedPreferencesNewsFeedCacheStore(context: Context) : NewsFeedCacheStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun load(language: MaterialLanguage): CachedNewsFeed? = runCatching {
        preferences.getString(key(language), null)?.let(NewsFeedCacheCodec::decode)
    }.getOrNull()

    override fun save(language: MaterialLanguage, feed: CachedNewsFeed) {
        preferences.edit().putString(key(language), NewsFeedCacheCodec.encode(feed)).apply()
    }

    private fun key(language: MaterialLanguage) = "feed_${language.name.lowercase()}"

    private companion object {
        const val PREFERENCES = "news_feed_cache"
    }
}

internal object NewsFeedCacheCodec {
    fun encode(feed: CachedNewsFeed): String = JSONObject()
        .put("savedAt", feed.savedAt)
        .put("items", JSONArray().apply {
            feed.items.take(MAX_PERSISTED_ITEMS).forEach { item ->
                put(JSONObject()
                    .put("sourceId", item.sourceId)
                    .put("publisher", item.publisher)
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("publishedAt", item.publishedAt)
                    .put("publishedAtEpochMillis", item.publishedAtEpochMillis)
                    .put("summary", item.summary)
                    .put("language", item.language.name)
                    .put("tags", JSONArray(item.tags.map(NewsTag::name))))
            }
        })
        .toString()

    fun decode(value: String): CachedNewsFeed {
        val root = JSONObject(value)
        val itemsJson = root.getJSONArray("items")
        val items = buildList {
            for (index in 0 until minOf(itemsJson.length(), MAX_PERSISTED_ITEMS)) {
                val item = itemsJson.getJSONObject(index)
                val tagsJson = item.optJSONArray("tags") ?: JSONArray()
                val tags = buildSet {
                    for (tagIndex in 0 until tagsJson.length()) {
                        runCatching { NewsTag.valueOf(tagsJson.getString(tagIndex)) }.getOrNull()?.let(::add)
                    }
                }
                val publishedAt = item.optString("publishedAt").takeUnless { it.isBlank() || it == "null" }
                val epoch = if (item.isNull("publishedAtEpochMillis")) null else item.optLong("publishedAtEpochMillis")
                val language = runCatching { MaterialLanguage.valueOf(item.getString("language")) }.getOrNull()
                    ?: continue
                add(NewsItem(
                    sourceId = item.getString("sourceId"),
                    publisher = item.getString("publisher"),
                    title = item.getString("title"),
                    url = item.getString("url"),
                    publishedAt = publishedAt,
                    publishedAtEpochMillis = epoch,
                    summary = item.optString("summary"),
                    language = language,
                    tags = tags,
                ))
            }
        }
        return CachedNewsFeed(root.getLong("savedAt"), items)
    }

    private const val MAX_PERSISTED_ITEMS = 200
}
