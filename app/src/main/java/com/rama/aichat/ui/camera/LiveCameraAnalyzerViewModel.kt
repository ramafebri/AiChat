package com.rama.aichat.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.aichat.inference.GemmaInferenceLock
import com.rama.aichat.inference.GemmaInferenceManager
import com.rama.aichat.inference.GemmaToolChatManager
import com.rama.aichat.inference.InferenceBusyException
import com.rama.aichat.inference.InferenceOwner
import com.rama.aichat.inference.LiveCameraManager
import com.rama.aichat.inference.TextToSpeechManager
import com.rama.aichat.inference.VoiceInputManager
import com.rama.aichat.inference.VoiceOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class AnalyzerPhase {
    Idle,
    Listening,
    Analyzing,
    Speaking,
    Error
}

data class LiveCameraAnalyzerUiState(
    val phase: AnalyzerPhase = AnalyzerPhase.Idle,
    val statusMessage: String? = null,
    val lastTranscript: String? = null,
    val lastResponse: String? = null,
    val error: String? = null,
    val isModelReady: Boolean = false
)

@HiltViewModel
class LiveCameraAnalyzerViewModel @Inject constructor(
    private val gemmaInferenceManager: GemmaInferenceManager,
    private val gemmaToolChatManager: GemmaToolChatManager,
    private val gemmaInferenceLock: GemmaInferenceLock,
    private val voiceInputManager: VoiceInputManager,
    private val textToSpeechManager: TextToSpeechManager,
    private val liveCameraManager: LiveCameraManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveCameraAnalyzerUiState())
    val uiState: StateFlow<LiveCameraAnalyzerUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    init {
        viewModelScope.launch {
            gemmaToolChatManager.initialize()
        }
        observeModelLoadState()
        observeVoiceInput()
    }

    private fun observeModelLoadState() {
        viewModelScope.launch {
            gemmaInferenceManager.loadState.collectLatest { loadState ->
                _uiState.update {
                    it.copy(isModelReady = loadState is GemmaInferenceManager.LoadState.Ready)
                }
            }
        }
    }

    private fun observeVoiceInput() {
        viewModelScope.launch {
            voiceInputManager.voiceState.collectLatest { voiceState ->
                when (voiceState) {
                    VoiceInputManager.VoiceState.Idle -> {
                        if (_uiState.value.phase == AnalyzerPhase.Listening) {
                            _uiState.update {
                                it.copy(phase = AnalyzerPhase.Idle, statusMessage = null)
                            }
                        }
                    }

                    is VoiceInputManager.VoiceState.Listening -> {
                        if (voiceState.owner != VoiceOwner.LiveAnalyzer) return@collectLatest
                        _uiState.update {
                            it.copy(
                                phase = AnalyzerPhase.Listening,
                                statusMessage = "Listening...",
                                error = null
                            )
                        }
                    }

                    is VoiceInputManager.VoiceState.Retrying -> {
                        if (voiceState.owner != VoiceOwner.LiveAnalyzer) return@collectLatest
                        _uiState.update {
                            it.copy(
                                phase = AnalyzerPhase.Listening,
                                statusMessage = "Didn't catch that, retrying..."
                            )
                        }
                    }

                    is VoiceInputManager.VoiceState.Result -> {
                        if (voiceState.owner != VoiceOwner.LiveAnalyzer) return@collectLatest
                        onVoiceResult(voiceState.transcript)
                    }

                    is VoiceInputManager.VoiceState.Error -> {
                        if (voiceState.owner != VoiceOwner.LiveAnalyzer) return@collectLatest
                        _uiState.update {
                            it.copy(
                                phase = AnalyzerPhase.Error,
                                statusMessage = null,
                                error = voiceState.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun bindCamera(previewView: androidx.camera.view.PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        viewModelScope.launch {
            liveCameraManager.bind(previewView, lifecycleOwner)
        }
    }

    fun unbindCamera() {
        viewModelScope.launch {
            liveCameraManager.unbind()
        }
    }

    fun startListening() {
        val state = _uiState.value
        if (!state.isModelReady) {
            _uiState.update { it.copy(error = "Model is still loading. Please wait.") }
            return
        }
        if (state.phase == AnalyzerPhase.Analyzing || state.phase == AnalyzerPhase.Speaking) {
            return
        }
        textToSpeechManager.stop()
        voiceInputManager.startListening(VoiceOwner.LiveAnalyzer)
    }

    fun stopListening() {
        voiceInputManager.stopListening(VoiceOwner.LiveAnalyzer)
        _uiState.update {
            it.copy(phase = AnalyzerPhase.Idle, statusMessage = null)
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(error = null, phase = if (it.phase == AnalyzerPhase.Error) AnalyzerPhase.Idle else it.phase)
        }
    }

    private fun onVoiceResult(transcript: String) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = AnalyzerPhase.Analyzing,
                    statusMessage = "Analyzing...",
                    lastTranscript = transcript,
                    error = null
                )
            }

            val frameBitmap = liveCameraManager.captureFrameBitmap()
            if (frameBitmap == null) {
                _uiState.update {
                    it.copy(
                        phase = AnalyzerPhase.Error,
                        statusMessage = null,
                        error = "No camera frame available. Point the camera and try again."
                    )
                }
                return@launch
            }

            val response = runCatching {
                withContext(Dispatchers.IO) {
                    val lockResult = gemmaInferenceLock.tryWithLock(InferenceOwner.LiveAnalyzer) {
                        gemmaInferenceManager.resetConversation(emptyList())
                        val full = StringBuilder()
                        gemmaInferenceManager.generateResponse(transcript, frameBitmap).collect { chunk ->
                            full.append(chunk)
                        }
                        full.toString()
                    }
                    lockResult.getOrElse { error ->
                        if (error is InferenceBusyException) {
                            "The model is busy with chat. Please try again shortly."
                        } else {
                            throw error
                        }
                    }
                }
            }.getOrElse { error ->
                frameBitmap.recycle()
                _uiState.update {
                    it.copy(
                        phase = AnalyzerPhase.Error,
                        statusMessage = null,
                        error = "Analysis failed: ${error.message}"
                    )
                }
                return@launch
            }

            if (!frameBitmap.isRecycled) {
                frameBitmap.recycle()
            }

            if (response.isBlank()) {
                _uiState.update {
                    it.copy(
                        phase = AnalyzerPhase.Error,
                        statusMessage = null,
                        error = "The model returned an empty response."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    phase = AnalyzerPhase.Speaking,
                    statusMessage = "Speaking...",
                    lastResponse = response
                )
            }

            textToSpeechManager.speak(response)

            _uiState.update {
                it.copy(phase = AnalyzerPhase.Idle, statusMessage = null)
            }
        }
    }

    override fun onCleared() {
        analysisJob?.cancel()
        voiceInputManager.releaseOwner(VoiceOwner.LiveAnalyzer)
        textToSpeechManager.shutdown()
        super.onCleared()
    }
}
