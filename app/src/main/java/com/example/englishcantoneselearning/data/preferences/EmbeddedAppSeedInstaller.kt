package com.example.englishcantoneselearning.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONArray

/** Installs optional, ignored build-time assets into an otherwise empty installation. */
class EmbeddedAppSeedInstaller(private val context: Context) {
    fun installPreferencesIfEligible() {
        val state = context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)
        if (state.getBoolean(SEED_APPLIED, false) || hasConfiguredCredential()) return
        val serviceValues = readPreferenceAsset(SERVICE_CONFIG_ASSET) ?: return
        applyValues(SERVICE_CONFIG_PREFERENCES, serviceValues)
        readPreferenceAsset(LEARNER_PREFERENCES_ASSET)?.let {
            applyValues(LEARNER_PREFERENCES, it)
        }
        state.edit().putBoolean(SEED_APPLIED, true).commit()
    }

    /** Returns true when Room should create a fresh database from the embedded asset. */
    fun prepareDatabaseSeed(): Boolean {
        if (!assetExists(DATABASE_ASSET)) return false
        val database = context.getDatabasePath(DATABASE_NAME)
        if (!database.isFile) return true
        if (!databaseIsEmpty(database)) return false
        return context.deleteDatabase(DATABASE_NAME)
    }

    private fun hasConfiguredCredential(): Boolean {
        val preferences = context.getSharedPreferences(SERVICE_CONFIG_PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getString(MINIMAX_API_KEY, "").orEmpty().isNotBlank()) return true
        return runCatching {
            val providers = JSONArray(preferences.getString(MATERIAL_PROVIDERS, "[]"))
            (0 until providers.length()).any { providers.getJSONObject(it).optString("apiKey").isNotBlank() }
        }.getOrDefault(false)
    }

    private fun databaseIsEmpty(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            listOf("practice_materials", "material_playback_progress", "material_generation_drafts").all { table ->
                database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getLong(0) == 0L
                }
            }
        }
    }.getOrDefault(false)

    private fun readPreferenceAsset(path: String): Map<String, Any>? = runCatching {
        context.assets.open(path).use { input ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
            buildMap {
                val children = document.documentElement.childNodes
                for (index in 0 until children.length) {
                    val node = children.item(index)
                    val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                    when (node.nodeName) {
                        "string" -> put(name, node.textContent.orEmpty())
                        "boolean" -> put(name, node.attributes.getNamedItem("value").nodeValue.toBoolean())
                        "int" -> put(name, node.attributes.getNamedItem("value").nodeValue.toInt())
                        "long" -> put(name, node.attributes.getNamedItem("value").nodeValue.toLong())
                        "float" -> put(name, node.attributes.getNamedItem("value").nodeValue.toFloat())
                    }
                }
            }
        }
    }.getOrNull()

    private fun applyValues(name: String, values: Map<String, Any>) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        check(editor.commit()) { "无法安装内置设置" }
    }

    private fun assetExists(path: String): Boolean = runCatching {
        context.assets.open(path).close()
        true
    }.getOrDefault(false)

    companion object {
        const val DATABASE_ASSET = "embedded/listening-materials.db"
        private const val SERVICE_CONFIG_ASSET = "embedded/service_configs.xml"
        private const val LEARNER_PREFERENCES_ASSET = "embedded/learner_preferences.xml"
        private const val DATABASE_NAME = "listening-materials.db"
        private const val SERVICE_CONFIG_PREFERENCES = "service_configs"
        private const val LEARNER_PREFERENCES = "learner_preferences"
        private const val STATE_PREFERENCES = "embedded_seed_state"
        private const val SEED_APPLIED = "seed_applied_v1"
        private const val MINIMAX_API_KEY = "minimax_api_key"
        private const val MATERIAL_PROVIDERS = "material_providers"
    }
}
