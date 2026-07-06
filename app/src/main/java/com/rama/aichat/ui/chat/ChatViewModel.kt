package com.rama.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.aichat.data.model.ChatMessage
import com.rama.aichat.data.model.ChatSession
import com.rama.aichat.data.repository.ChatRepository
import com.rama.aichat.inference.GeminiChatManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val geminiChatManager: GeminiChatManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            chatRepository.getAllSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
                if (_uiState.value.currentSessionId == null) {
                    if (sessions.isNotEmpty()) {
                        selectSession(sessions.first().id)
                    } else {
                        createNewSession()
                    }
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

    fun sendMessage() {
        val state = _uiState.value
        val sessionId = state.currentSessionId ?: return
        val userMessage = state.inputText.trim()
        if (userMessage.isEmpty() || state.isGenerating) return

        val isFirstMessage = state.messages.isEmpty()
        val priorMessages = state.messages
        _uiState.update { it.copy(inputText = "", isGenerating = true, streamingText = "", error = null) }

        viewModelScope.launch {
            try {
                chatRepository.addMessage(sessionId, userMessage, "user")

                val fullResponse = StringBuilder()
                geminiChatManager.generateResponse(priorMessages, userMessage).collect { chunk ->
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
}
