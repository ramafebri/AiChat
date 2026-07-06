package com.rama.aichat.ui.skill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rama.aichat.data.model.SkillFile
import com.rama.aichat.data.repository.SkillException
import com.rama.aichat.data.repository.SkillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkillListUiState(
    val skills: List<SkillFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SkillListViewModel @Inject constructor(
    private val skillRepository: SkillRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillListUiState())
    val uiState: StateFlow<SkillListUiState> = _uiState.asStateFlow()

    init {
        loadSkills()
    }

    fun loadSkills() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val skills = skillRepository.getAllSkills()
                _uiState.update { it.copy(skills = skills, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load skills: ${e.message}")
                }
            }
        }
    }

    fun deleteSkill(fileName: String) {
        viewModelScope.launch {
            try {
                skillRepository.deleteSkill(fileName)
                loadSkills()
            } catch (e: SkillException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete skill: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
