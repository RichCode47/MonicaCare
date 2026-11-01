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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.monicacare.app.core.model.AppointmentStatus
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
    val upcomingAppointment: AntenatalAppointment? = null,
    val appointmentError: String? = null,
    val dailyTip: DailyPregnancyTip? = DemonstrationData.sampleDailyTip,
    val hasProfile: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: PregnancyProfileRepository,
    private val currentDateProvider: () -> LocalDate = { LocalDate.now() },
    private val developmentProvider: (Int) -> BabyDevelopment? = BabyDevelopmentRepository::getForWeek,
    private val appointments: Flow<List<AntenatalAppointment>> = flowOf(emptyList())
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    private var appointmentCache: List<AntenatalAppointment> = emptyList()

    init {
        loadProfile()
        viewModelScope.launch {
            appointments.catch {
                _uiState.update { it.copy(appointmentError = "Appointment records are unavailable. Open Appointments to retry.") }
            }.collect { records ->
                appointmentCache = records
                _uiState.update { it.copy(upcomingAppointment = nextAppointment(), appointmentError = null) }
            }
        }
    }

    private fun nextAppointment(): AntenatalAppointment? = appointmentCache
        .filter { it.status == AppointmentStatus.UPCOMING &&
            PregnancyCalculator.parseIsoDate(it.appointmentDate)?.isBefore(currentDateProvider()) != true }
        .minByOrNull { it.appointmentDate + it.appointmentTime }

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
        _uiState.update { it.copy(upcomingAppointment = nextAppointment()) }
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
        private val currentDateProvider: () -> LocalDate = { LocalDate.now() },
        private val developmentProvider: (Int) -> BabyDevelopment? = BabyDevelopmentRepository::getForWeek,
        private val appointments: Flow<List<AntenatalAppointment>> = flowOf(emptyList())
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, currentDateProvider, developmentProvider, appointments) as T
        }
    }
}
