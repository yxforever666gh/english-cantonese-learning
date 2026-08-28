package com.example.englishcantoneselearning.speech

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.example.englishcantoneselearning.data.preferences.MiniMaxConfigStore
import com.example.englishcantoneselearning.model.SpeechLanguage
import com.example.englishcantoneselearning.model.TtsAvailability
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class CloudSpeechController(
    private val configStore: MiniMaxConfigStore,
    private val gateway: MiniMaxSpeechGateway,
    private val audioCache: SpeechAudioCache,
) : SpeechController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()
    private var preparationJob: Job? = null
    private var player: MediaPlayer? = null
    private val inFlightLock = Any()
    private val inFlightAudio = mutableMapOf<String, Deferred<File>>()
    @Volatile private var activeRequestId: Long? = null

    init { _events.tryEmit(SpeechEvent.Initialized(successful = true)) }

    override fun checkAvailability(language: SpeechLanguage): TtsAvailability = TtsAvailability.READY

    override fun speak(
        requestId: Long,
        text: String,
        language: SpeechLanguage,
        speed: Float,
        startOffset: Int,
    ): Boolean {
        val config = configStore.config()
        if (text.isBlank()) return false
        val safeOffset = startOffset.coerceIn(0, text.length)
        val request = MiniMaxSpeechRequest(text.substring(safeOffset), language, speed)
        return prepareAndPlay(requestId, config, request)
    }

    override fun preview(
        requestId: Long,
        text: String,
        language: SpeechLanguage,
        voiceId: String,
        speed: Float,
    ): Boolean {
        val cleanVoiceId = com.example.englishcantoneselearning.data.preferences.ServiceConfigStore
            .sanitizeVoiceId(voiceId)
        if (text.isBlank() || cleanVoiceId.isBlank()) return false
        return prepareAndPlay(
            requestId,
            configStore.config(),
            MiniMaxSpeechRequest(text, language, speed, voiceIdOverride = cleanVoiceId),
        )
    }

    override suspend fun preload(
        text: String,
        language: SpeechLanguage,
        speed: Float,
    ): Boolean {
        if (text.isBlank()) return false
        val config = configStore.config()
        return try {
            cachedAudio(config, MiniMaxSpeechRequest(text, language, speed)).await()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
        }
    }

    private fun prepareAndPlay(
        requestId: Long,
        config: com.example.englishcantoneselearning.model.MiniMaxTtsConfig,
        request: MiniMaxSpeechRequest,
    ): Boolean {
        stop()
        activeRequestId = requestId
        preparationJob = scope.launch {
            try {
                val file = cachedAudio(config, request).await()
                if (activeRequestId == requestId) mainHandler.post { startPlayer(requestId, file) }
            } catch (_: CancellationException) {
                // An explicit stop or jump invalidated this request.
            } catch (error: Throwable) {
                if (activeRequestId == requestId) {
                    activeRequestId = null
                    _events.tryEmit(SpeechEvent.Error(requestId, error.message ?: "MiniMax 语音播放失败"))
                }
            }
        }
        return true
    }

    private fun cachedAudio(
        config: com.example.englishcantoneselearning.model.MiniMaxTtsConfig,
        request: MiniMaxSpeechRequest,
    ): Deferred<File> {
        val identity = gateway.cacheIdentity(config, request)
        audioCache.get(identity)?.let { return CompletableDeferred(it) }
        synchronized(inFlightLock) {
            audioCache.get(identity)?.let { return CompletableDeferred(it) }
            inFlightAudio[identity]?.let { return it }
            val deferred = scope.async {
                if (config.apiKey.isBlank()) throw MiniMaxSpeechException(
                    "该句没有语音缓存，请先在设置中填写 MiniMax API Key并联网生成",
                )
                audioCache.put(identity, gateway.synthesize(config, request))
            }
            inFlightAudio[identity] = deferred
            deferred.invokeOnCompletion {
                synchronized(inFlightLock) {
                    if (inFlightAudio[identity] === deferred) inFlightAudio.remove(identity)
                }
            }
            return deferred
        }
    }

    override fun pause(): Boolean {
        val current = player ?: return false
        return runCatching {
            if (current.isPlaying) current.pause()
            true
        }.getOrDefault(false)
    }

    override fun resume(): Boolean {
        val current = player ?: return false
        return runCatching {
            current.start()
            activeRequestId?.let { _events.tryEmit(SpeechEvent.Started(it)) }
            true
        }.getOrDefault(false)
    }

    override fun stop() {
        activeRequestId = null
        preparationJob?.cancel()
        preparationJob = null
        val oldPlayer = player
        player = null
        if (oldPlayer != null) mainHandler.post {
            runCatching { oldPlayer.stop() }
            oldPlayer.release()
        }
    }

    override fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun startPlayer(requestId: Long, file: File) {
        if (activeRequestId != requestId) return
        runCatching {
            MediaPlayer().apply {
                player = this
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    if (activeRequestId == requestId) {
                        activeRequestId = null
                        player = null
                        it.release()
                        _events.tryEmit(SpeechEvent.Done(requestId))
                    }
                }
                setOnErrorListener { mediaPlayer, _, _ ->
                    if (activeRequestId == requestId) {
                        activeRequestId = null
                        player = null
                        mediaPlayer.release()
                        _events.tryEmit(SpeechEvent.Error(requestId, "缓存音频无法播放"))
                    }
                    true
                }
                prepare()
                start()
            }
            _events.tryEmit(SpeechEvent.Started(requestId))
        }.onFailure {
            activeRequestId = null
            player?.release()
            player = null
            _events.tryEmit(SpeechEvent.Error(requestId, "缓存音频无法播放"))
        }
    }
}
