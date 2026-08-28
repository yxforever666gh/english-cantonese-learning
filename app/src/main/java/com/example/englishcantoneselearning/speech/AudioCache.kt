package com.example.englishcantoneselearning.speech

import android.content.Context
import java.io.File
import java.security.MessageDigest

interface SpeechAudioCache {
    fun get(cacheIdentity: String): File?
    fun put(cacheIdentity: String, bytes: ByteArray): File
    fun sizeBytes(): Long
    fun clear()
}

class AudioCache(
    context: Context,
    private val maxBytes: Long = 500L * 1024 * 1024,
) : SpeechAudioCache {
    private val directory = File(context.applicationContext.filesDir, "tts_audio_cache").apply { mkdirs() }

    @Synchronized
    override fun get(cacheIdentity: String): File? {
        val file = File(directory, "${hash(cacheIdentity)}.mp3")
        if (!file.isFile || file.length() == 0L) return null
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    @Synchronized
    override fun put(cacheIdentity: String, bytes: ByteArray): File {
        require(bytes.isNotEmpty()) { "语音服务返回了空音频" }
        val target = File(directory, "${hash(cacheIdentity)}.mp3")
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "无法保存语音缓存" }
        target.setLastModified(System.currentTimeMillis())
        trim()
        return target
    }

    @Synchronized
    override fun sizeBytes(): Long = directory.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L

    @Synchronized
    override fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    @Synchronized
    private fun trim() {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedBy(File::lastModified).orEmpty()
        var total = files.sumOf(File::length)
        for (file in files) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        directory.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
