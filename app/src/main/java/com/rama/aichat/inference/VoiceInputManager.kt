package com.rama.aichat.inference

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceOwner {
    Chat,
    LiveAnalyzer
}

@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    companion object {
        private const val MAX_TIMEOUT_RETRIES = 1
    }

    sealed interface VoiceState {
        data object Idle : VoiceState
        data class Listening(val owner: VoiceOwner) : VoiceState
        data class Retrying(val owner: VoiceOwner, val attempt: Int) : VoiceState
        data class Result(val owner: VoiceOwner, val transcript: String) : VoiceState
        data class Error(val owner: VoiceOwner, val message: String) : VoiceState
    }

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var timeoutRetryCount = 0
    private var isManualStop = false
    private var activeOwner: VoiceOwner? = null

    fun startListening(owner: VoiceOwner) {
        if (activeOwner != null && activeOwner != owner) {
            _voiceState.value = VoiceState.Error(owner, "Microphone is in use by another screen.")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _voiceState.value = VoiceState.Error(owner, "Speech recognition is not available on this device.")
            return
        }

        activeOwner = owner
        isManualStop = false
        val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            speechRecognizer = it
            it.setRecognitionListener(createRecognitionListener())
        }

        _voiceState.value = VoiceState.Listening(owner)
        recognizer.startListening(createRecognizerIntent())
    }

    fun stopListening(owner: VoiceOwner) {
        if (activeOwner != owner) return
        isManualStop = true
        speechRecognizer?.stopListening()
        activeOwner = null
        _voiceState.value = VoiceState.Idle
    }

    fun releaseOwner(owner: VoiceOwner) {
        if (activeOwner == owner) {
            isManualStop = true
            speechRecognizer?.stopListening()
            activeOwner = null
            _voiceState.value = VoiceState.Idle
        }
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        timeoutRetryCount = 0
        isManualStop = false
        activeOwner = null
        _voiceState.value = VoiceState.Idle
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (isManualStop && error == SpeechRecognizer.ERROR_CLIENT) {
                isManualStop = false
                _voiceState.value = VoiceState.Idle
                return
            }

            if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                && timeoutRetryCount < MAX_TIMEOUT_RETRIES
            ) {
                timeoutRetryCount += 1
                val owner = activeOwner ?: VoiceOwner.Chat
                _voiceState.value = VoiceState.Retrying(owner, timeoutRetryCount)
                speechRecognizer?.startListening(createRecognizerIntent())
                return
            }

            timeoutRetryCount = 0
            val owner = activeOwner ?: VoiceOwner.Chat
            _voiceState.value = VoiceState.Error(owner, errorToMessage(error))
            activeOwner = null
            _voiceState.value = VoiceState.Idle
        }

        override fun onResults(results: Bundle?) {
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            val owner = activeOwner ?: VoiceOwner.Chat
            if (transcript.isNotEmpty()) {
                timeoutRetryCount = 0
                _voiceState.value = VoiceState.Result(owner, transcript)
            } else {
                _voiceState.value = VoiceState.Error(owner, "No speech recognized. Please try again.")
            }
            activeOwner = null
            _voiceState.value = VoiceState.Idle
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun errorToMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
            SpeechRecognizer.ERROR_CLIENT -> "Client error while listening."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
            else -> "Speech recognition failed."
        }
    }
}
