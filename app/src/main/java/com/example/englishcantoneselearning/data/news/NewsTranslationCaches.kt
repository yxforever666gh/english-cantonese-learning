package com.example.englishcantoneselearning.data.news

import android.content.Context
import android.content.SharedPreferences
import com.example.englishcantoneselearning.model.MaterialLanguage
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

interface TitleTranslationCache {
    fun get(
        promptVersion: String,
        language: MaterialLanguage,
        normalizedUrl: String,
        title: String,
    ): String?

    fun put(
        promptVersion: String,
        language: MaterialLanguage,
        normalizedUrl: String,
        title: String,
        translation: String,
    )
}

object NoOpTitleTranslationCache : TitleTranslationCache {
    override fun get(
        promptVersion: String,
        language: MaterialLanguage,
        normalizedUrl: String,
        title: String,
    ): String? = null

    override fun put(
        promptVersion: String,
        language: MaterialLanguage,
        normalizedUrl: String,
        title: String,
        translation: String,
    ) = Unit
}

class SharedPreferencesTitleTranslationCache internal constructor(
    private val preferences: SharedPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : TitleTranslationCache {
    constructor(
        context: Context,
        nowMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE),
        nowMillis,
    )

    private val lock = Any()

    override fun get(
        promptVersion: String,
        language: MaterialLanguage,
        normalizedUrl: String,
        title: String,
    ): String? = synchronized(lock) {
        val now = nowMillis()
        val decodedEntries = readEntries()
        val entries = decodedEntries.orEmpty()
        val pruned = prune(entries, now)
        if (decodedEntries == null) writeEntries(pruned) else persistIfChanged(entries, pruned)
        pruned[cacheKey(promptVersion, language, normalizedUrl, title)]?.translation
    }

    override fun put(
        promptVersion: String,
        language: MaterialLanguage,
        normalizedUrl: String,
        title: String,
        translation: String,
    ) {
        val normalizedTranslation = translation.trim()
        if (normalizedTranslation.isEmpty()) return
        synchronized(lock) {
            val now = nowMillis()
            val entries = prune(readEntries().orEmpty(), now).toMutableMap()
            val key = cacheKey(promptVersion, language, normalizedUrl, title)
            entries.remove(key)
            val newestExisting = entries.entries
                .sortedByDescending { it.value.savedAt }
                .take(MAX_TITLE_ENTRIES - 1)
                .associate { it.toPair() }
            writeEntries(newestExisting + (key to TitleCacheEntry(now, normalizedTranslation)))
        }
    }

    private fun readEntries(): Map<String, TitleCacheEntry>? = runCatching {
        val root = JSONObject(preferences.getString(ENTRIES, "{}") ?: "{}")
        buildMap {
            root.keys().forEach { key ->
                val entry = root.optJSONObject(key) ?: return@forEach
                val translation = entry.optString("translation").trim()
                val savedAt = entry.optLong("savedAt", -1L)
                if (translation.isNotEmpty() && savedAt >= 0L) {
                    put(key, TitleCacheEntry(savedAt, translation))
                }
            }
        }
    }.getOrNull()

    private fun prune(entries: Map<String, TitleCacheEntry>, now: Long): Map<String, TitleCacheEntry> =
        entries
            .filterValues { entry -> entry.savedAt <= now && now - entry.savedAt <= TITLE_TTL_MILLIS }
            .entries
            .sortedByDescending { it.value.savedAt }
            .take(MAX_TITLE_ENTRIES)
            .associate { it.toPair() }

    private fun persistIfChanged(
        old: Map<String, TitleCacheEntry>,
        new: Map<String, TitleCacheEntry>,
    ) {
        if (old != new) writeEntries(new)
    }

    private fun writeEntries(entries: Map<String, TitleCacheEntry>) {
        val root = JSONObject()
        entries.forEach { (key, entry) ->
            root.put(
                key,
                JSONObject()
                    .put("savedAt", entry.savedAt)
                    .put("translation", entry.translation),
            )
        }
        preferences.edit().putString(ENTRIES, root.toString()).commit()
    }

    private companion object {
        const val PREFERENCES = "news_title_translation_cache"
        const val ENTRIES = "entries"
        const val MAX_TITLE_ENTRIES = 400
        const val TITLE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1_000
    }
}

data class ArticleTranslationCacheKey(
    val promptVersion: String,
    val language: MaterialLanguage,
    val contentHash: String,
    val sentenceHash: String,
)

interface ArticleTranslationCache {
    fun load(key: ArticleTranslationCacheKey): Map<String, String>?
    fun save(key: ArticleTranslationCacheKey, translations: Map<String, String>)
}

object NoOpArticleTranslationCache : ArticleTranslationCache {
    override fun load(key: ArticleTranslationCacheKey): Map<String, String>? = null
    override fun save(key: ArticleTranslationCacheKey, translations: Map<String, String>) = Unit
}

class FileArticleTranslationCache(
    private val directory: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ArticleTranslationCache {
    constructor(context: Context, nowMillis: () -> Long = System::currentTimeMillis) : this(
        File(context.applicationContext.filesDir, DIRECTORY),
        nowMillis,
    )

    private val lock = Any()

    override fun load(key: ArticleTranslationCacheKey): Map<String, String>? = synchronized(lock) {
        directory.mkdirs()
        prune(nowMillis())
        val file = fileFor(key)
        if (!file.isFile) return@synchronized null
        val entry = decode(file, key)
        if (entry == null) file.delete()
        entry?.translations
    }

    override fun save(key: ArticleTranslationCacheKey, translations: Map<String, String>) {
        val cleanTranslations = translations
            .mapValues { it.value.trim() }
            .filterKeys(String::isNotBlank)
            .filterValues(String::isNotBlank)
        if (cleanTranslations.isEmpty()) return

        synchronized(lock) {
            directory.mkdirs()
            val now = nowMillis()
            prune(now)
            val target = fileFor(key)
            val temporary = File(directory, ".${target.name}.${System.nanoTime()}.tmp")
            try {
                temporary.writeText(encode(key, now, cleanTranslations), Charsets.UTF_8)
                moveAtomically(temporary, target)
                prune(now, preserve = target)
            } finally {
                temporary.delete()
            }
        }
    }

    private fun prune(now: Long, preserve: File? = null) {
        val entries = directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension == FILE_EXTENSION }
            .mapNotNull { file ->
                val savedAt = runCatching { JSONObject(file.readText()).optLong("savedAt", -1L) }.getOrDefault(-1L)
                if (savedAt < 0L || savedAt > now || now - savedAt > ARTICLE_TTL_MILLIS) {
                    file.delete()
                    null
                } else {
                    file to savedAt
                }
            }
            .sortedWith(
                compareByDescending<Pair<File, Long>> { it.first == preserve }
                    .thenByDescending { it.second },
            )
        entries.drop(MAX_ARTICLE_ENTRIES).forEach { (file, _) -> file.delete() }
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".tmp") }
            .forEach(File::delete)
    }

    private fun decode(file: File, expectedKey: ArticleTranslationCacheKey): ArticleCacheEntry? = runCatching {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val savedAt = root.getLong("savedAt")
        val now = nowMillis()
        check(savedAt <= now && now - savedAt <= ARTICLE_TTL_MILLIS)
        check(root.getString("promptVersion") == expectedKey.promptVersion)
        check(root.getString("language") == expectedKey.language.name)
        check(root.getString("contentHash") == expectedKey.contentHash)
        check(root.getString("sentenceHash") == expectedKey.sentenceHash)
        val jsonTranslations = root.getJSONObject("translations")
        val translations = buildMap {
            jsonTranslations.keys().forEach { id ->
                val translation = jsonTranslations.getString(id).trim()
                check(id.isNotBlank() && translation.isNotBlank())
                put(id, translation)
            }
        }
        check(translations.isNotEmpty())
        ArticleCacheEntry(savedAt, translations)
    }.getOrNull()

    private fun encode(
        key: ArticleTranslationCacheKey,
        savedAt: Long,
        translations: Map<String, String>,
    ): String = JSONObject()
        .put("promptVersion", key.promptVersion)
        .put("language", key.language.name)
        .put("contentHash", key.contentHash)
        .put("sentenceHash", key.sentenceHash)
        .put("savedAt", savedAt)
        .put("translations", JSONObject().apply {
            translations.forEach { (id, translation) -> put(id, translation) }
        })
        .toString()

    private fun fileFor(key: ArticleTranslationCacheKey): File = File(
        directory,
        "${sha256("${key.promptVersion}|${key.language.name}|${key.contentHash}|${key.sentenceHash}")}.$FILE_EXTENSION",
    )

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val DIRECTORY = "news_article_translation_cache"
        const val FILE_EXTENSION = "json"
        const val MAX_ARTICLE_ENTRIES = 30
        const val ARTICLE_TTL_MILLIS = 14L * 24 * 60 * 60 * 1_000
    }
}

private data class TitleCacheEntry(val savedAt: Long, val translation: String)
private data class ArticleCacheEntry(val savedAt: Long, val translations: Map<String, String>)

private fun cacheKey(
    promptVersion: String,
    language: MaterialLanguage,
    normalizedUrl: String,
    title: String,
): String = sha256("$promptVersion|${language.name}|$normalizedUrl|${sha256(title)}")

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
