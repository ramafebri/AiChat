package com.rama.aichat.ui.skill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.aichat.data.repository.SkillException
import com.rama.aichat.data.repository.SkillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkillEditUiState(
    val fileName: String = "",
    val content: String = "",
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SkillEditViewModel @Inject constructor(
    private val skillRepository: SkillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillEditUiState())
    val uiState: StateFlow<SkillEditUiState> = _uiState.asStateFlow()

    private var initialized = false

    fun initialize(fileName: String?) {
        if (initialized) return
        initialized = true

        if (fileName == null) {
            _uiState.update { it.copy(isNew = true) }
            return
        }

        _uiState.update { it.copy(isNew = false, fileName = fileName, isLoading = true) }
        viewModelScope.launch {
            try {
                val content = skillRepository.getSkillContent(fileName)
                _uiState.update { it.copy(content = content, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load skill: ${e.message}")
                }
            }
        }
    }

    fun updateFileName(name: String) {
        _uiState.update { it.copy(fileName = name) }
    }

    fun updateContent(content: String) {
        _uiState.update { it.copy(content = content) }
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                if (state.isNew) {
                    skillRepository.createSkill(state.fileName, state.content)
                } else {
                    skillRepository.updateSkill(state.fileName, state.content)
                }
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: SkillException) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save skill: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
