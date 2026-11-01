@file:Suppress("NewApi")

package com.monicacare.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.monicacare.app.core.calculator.PregnancyCalculator
import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.model.AntenatalAppointment
import com.monicacare.app.core.model.BabyDevelopment
import com.monicacare.app.core.model.BabyDevelopmentRepository
import com.monicacare.app.core.model.DailyPregnancyTip
import com.monicacare.app.core.model.DemonstrationData
import com.monicacare.app.core.model.PregnancyProfile
import com.monicacare.app.core.model.PregnancyProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val pregnancyProgress: PregnancyProgress? = null,
    val pregnancyProfile: PregnancyProfile? = null,
    val babyDevelopment: BabyDevelopment? = null,
    val upcomingAppointment: AntenatalAppointment? = DemonstrationData.sampleAppointment,
    val dailyTip: DailyPregnancyTip? = DemonstrationData.sampleDailyTip,
    val hasProfile: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: PregnancyProfileRepository,
    private val currentDateProvider: () -> LocalDate = { LocalDate.now() },
    private val developmentProvider: (Int) -> BabyDevelopment? = BabyDevelopmentRepository::getForWeek
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        loadProfile()
    }

    fun loadProfile() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.pregnancyProfile
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load pregnancy profile: ${exception.localizedMessage}"
                        )
                    }
                }
                .collect { profile ->
                    val currentDate = currentDateProvider()
                    if (profile == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                hasProfile = false,
                                displayName = "Mama",
                                pregnancyProfile = null,
                                pregnancyProgress = null,
                                babyDevelopment = null,
                                errorMessage = null
                            )
                        }
                    } else {
                        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)
                        val babyDev = progress?.let { developmentProvider(it.currentWeek) }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                hasProfile = true,
                                displayName = profile.displayName.ifBlank { "Mama" },
                                pregnancyProfile = profile,
                                pregnancyProgress = progress,
                                babyDevelopment = babyDev,
                                errorMessage = null
                            )
                        }
                    }
                }
        }
    }

    fun refreshCalculations() {
        val currentProfile = _uiState.value.pregnancyProfile ?: return
        val currentDate = currentDateProvider()
        val progress = PregnancyCalculator.calculateProgress(currentProfile, currentDate)
        val babyDev = progress?.let { developmentProvider(it.currentWeek) }

        _uiState.update {
            it.copy(
                pregnancyProgress = progress,
                babyDevelopment = babyDev
            )
        }
    }

    class Factory(
        private val repository: PregnancyProfileRepository,
        private val currentDateProvider: () -> LocalDate = { LocalDate.now() }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, currentDateProvider) as T
        }
    }
}
