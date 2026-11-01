package com.monicacare.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.monicacare.app.core.data.OnboardingPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val completed: Boolean? = null,
    val isSaving: Boolean = false,
    val completionSaved: Boolean = false,
    val errorMessage: String? = null,
    val readFailed: Boolean = false
)

class OnboardingViewModel(private val preferences: OnboardingPreferences) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState = _uiState.asStateFlow()
    private var readJob: Job? = null

    init { loadPreferences() }

    fun loadPreferences() {
        readJob?.cancel()
        _uiState.update { it.copy(errorMessage = null, readFailed = false) }
        readJob = viewModelScope.launch {
            try {
                preferences.isOnboardingCompleted.collect { completed ->
                    _uiState.update { it.copy(completed = completed, errorMessage = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Unable to read welcome preferences. Please retry.", readFailed = true) }
            }
        }
    }

    fun retry() {
        if (_uiState.value.readFailed) loadPreferences() else completeOnboarding()
    }

    fun completeOnboarding() {
        if (_uiState.value.isSaving || _uiState.value.completionSaved) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null, readFailed = false) }
        viewModelScope.launch {
            try {
                preferences.setOnboardingCompleted(true)
                _uiState.update { it.copy(isSaving = false, completionSaved = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Unable to save welcome preferences. Please retry.")
                }
            }
        }
    }

    class Factory(private val preferences: OnboardingPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OnboardingViewModel(preferences) as T
    }
}
