package com.rama.aichat.inference

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    sealed interface TtsState {
        data object Uninitialized : TtsState
        data object Ready : TtsState
        data object Speaking : TtsState
        data class Error(val message: String) : TtsState
    }

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Uninitialized)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val initMutex = Mutex()
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    suspend fun ensureInitialized() = withContext(Dispatchers.Main) {
        if (isInitialized) return@withContext

        initMutex.withLock {
            if (isInitialized) return@withLock

            val initResult = CompletableDeferred<Boolean>()
            textToSpeech = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.getDefault()
                    isInitialized = true
                    _ttsState.value = TtsState.Ready
                    initResult.complete(true)
                } else {
                    _ttsState.value = TtsState.Error("Text-to-speech is not available on this device.")
                    initResult.complete(false)
                }
            }
            initResult.await()
        }
    }

    suspend fun speak(text: String) = withContext(Dispatchers.Main) {
        ensureInitialized()
        val tts = textToSpeech ?: return@withContext
        if (text.isBlank()) return@withContext

        suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _ttsState.value = TtsState.Speaking
                }

                override fun onDone(utteranceId: String?) {
                    _ttsState.value = TtsState.Ready
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _ttsState.value = TtsState.Error("Failed to speak response.")
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _ttsState.value = TtsState.Error("Failed to speak response.")
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            })

            continuation.invokeOnCancellation {
                tts.stop()
                _ttsState.value = TtsState.Ready
            }

            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                _ttsState.value = TtsState.Error("Failed to start speech.")
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    fun stop() {
        textToSpeech?.stop()
        if (isInitialized) {
            _ttsState.value = TtsState.Ready
        }
    }
}
