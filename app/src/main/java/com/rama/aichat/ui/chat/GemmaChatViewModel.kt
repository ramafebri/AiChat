package com.rama.aichat.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.aichat.data.model.ChatMessage
import com.rama.aichat.data.model.ChatSession
import com.rama.aichat.data.repository.ChatRepository
import com.rama.aichat.inference.GemmaInferenceManager
import com.rama.aichat.inference.GemmaToolChatManager
import com.rama.aichat.inference.ImageAttachmentManager
import com.rama.aichat.inference.VoiceInputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GemmaChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val isRecording: Boolean = false,
    val recordingHint: String? = null,
    val pendingImagePath: String? = null,
    val isAttachingImage: Boolean = false,
    val streamingText: String = "",
    val error: String? = null
)

@HiltViewModel
class GemmaChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val gemmaToolChatManager: GemmaToolChatManager,
    private val imageAttachmentManager: ImageAttachmentManager,
    private val voiceInputManager: VoiceInputManager
) : ViewModel() {
    companion object {
        private const val IMAGE_ONLY_DEFAULT_PROMPT = "Describe this image."
    }

    private val _uiState = MutableStateFlow(GemmaChatUiState())
    val uiState: StateFlow<GemmaChatUiState> = _uiState.asStateFlow()

    val loadState: StateFlow<GemmaInferenceManager.LoadState> =
        gemmaToolChatManager.loadState

    private var messagesJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                gemmaToolChatManager.initialize()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to initialize model: ${e.message}") }
            }
        }
        observeVoiceInput()
        observeSessions()
    }

    private fun observeVoiceInput() {
        viewModelScope.launch {
            voiceInputManager.voiceState.collectLatest { voiceState ->
                when (voiceState) {
                    VoiceInputManager.VoiceState.Idle -> {
                        _uiState.update { it.copy(isRecording = false, recordingHint = null) }
                    }

                    VoiceInputManager.VoiceState.Listening -> {
                        _uiState.update { it.copy(isRecording = true, recordingHint = "Listening...") }
                    }

                    is VoiceInputManager.VoiceState.Retrying -> {
                        _uiState.update {
                            it.copy(
                                isRecording = true,
                                recordingHint = "Didn't catch that, retrying..."
                            )
                        }
                    }

                    is VoiceInputManager.VoiceState.Result -> {
                        _uiState.update {
                            it.copy(
                                inputText = voiceState.transcript,
                                isRecording = false,
                                recordingHint = null
                            )
                        }
                        sendMessage()
                    }

                    is VoiceInputManager.VoiceState.Error -> {
                        _uiState.update {
                            it.copy(
                                isRecording = false,
                                recordingHint = null,
                                error = voiceState.message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            chatRepository.getAllSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
                // Only select a session; never auto-create to avoid racing with ChatViewModel
                if (_uiState.value.currentSessionId == null && sessions.isNotEmpty()) {
                    selectSession(sessions.first().id)
                }
            }
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val id = chatRepository.createSession("New Chat")
            selectSession(id)
        }
    }

    fun selectSession(sessionId: String) {
        _uiState.update { it.copy(currentSessionId = sessionId, messages = emptyList()) }
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getSessionMessages(sessionId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun createCameraCaptureUri(): Uri = imageAttachmentManager.createCameraCaptureUri()

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAttachingImage = true, error = null) }
            runCatching {
                imageAttachmentManager.persistFromUri(uri)
            }.onSuccess { path ->
                _uiState.update {
                    it.copy(
                        pendingImagePath = path,
                        isAttachingImage = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAttachingImage = false,
                        error = "Failed to attach image: ${error.message}"
                    )
                }
            }
        }
    }

    fun clearPendingImage() {
        _uiState.update { it.copy(pendingImagePath = null) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val sessionId = state.currentSessionId ?: return
        val typedMessage = state.inputText.trim()
        val imagePath = state.pendingImagePath
        if ((typedMessage.isEmpty() && imagePath == null) || state.isGenerating || state.isAttachingImage) return
        val userMessage = typedMessage.ifBlank { IMAGE_ONLY_DEFAULT_PROMPT }

        val isFirstMessage = state.messages.isEmpty()
        val priorMessages = state.messages
        _uiState.update {
            it.copy(
                inputText = "",
                pendingImagePath = null,
                isGenerating = true,
                streamingText = "",
                error = null
            )
        }

        viewModelScope.launch {
            try {
                chatRepository.addMessage(sessionId, userMessage, "user", imagePath = imagePath)
                val imageBitmap = imagePath?.let { imageAttachmentManager.loadBitmapForInference(it) }

                val fullResponse = StringBuilder()
                gemmaToolChatManager.generateResponse(priorMessages, userMessage, imageBitmap).collect { chunk ->
                    fullResponse.append(chunk)
                    _uiState.update { it.copy(streamingText = fullResponse.toString()) }
                }

                val responseText = fullResponse.toString()
                if (responseText.isNotBlank()) {
                    chatRepository.addMessage(sessionId, responseText, "model")
                }

                if (isFirstMessage) {
                    chatRepository.updateSessionTitle(sessionId, userMessage.take(50))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Generation failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isGenerating = false, streamingText = "") }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                _uiState.update { it.copy(currentSessionId = null, messages = emptyList()) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun startVoiceInput() {
        val state = _uiState.value
        if (state.isGenerating) return
        voiceInputManager.startListening()
    }

    fun stopVoiceInput() {
        voiceInputManager.stopListening()
    }

    override fun onCleared() {
        voiceInputManager.destroy()
        super.onCleared()
    }
}
