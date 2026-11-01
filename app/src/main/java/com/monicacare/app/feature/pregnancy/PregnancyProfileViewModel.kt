@file:Suppress("NewApi")

package com.monicacare.app.feature.pregnancy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.monicacare.app.core.calculator.PregnancyCalculator
import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import com.monicacare.app.core.model.PregnancyProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PregnancyProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val displayName: String = "",
    val datingMethod: DatingMethod = DatingMethod.LMP,
    val lmpDateIso: String = "",
    val eddDateIso: String = "",
    val isClinicianConfirmed: Boolean = false,
    val healthcareFacility: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val pregnancyNotes: String = "",
    val calculatedEddIso: String? = null,
    val calculatedLmpIso: String? = null,
    val calculatedProgress: PregnancyProgress? = null,
    val displayNameError: String? = null,
    val dateError: String? = null,
    val isSavedSuccess: Boolean = false
)

class PregnancyProfileViewModel(
    private val repository: PregnancyProfileRepository,
    private val currentDateProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(PregnancyProfileUiState())
    val uiState: StateFlow<PregnancyProfileUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadExistingProfile()
    }

    fun loadExistingProfile() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                val existing = repository.pregnancyProfile.firstOrNull()

                if (existing != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            displayName = existing.displayName,
                            datingMethod = existing.datingMethod,
                            lmpDateIso = existing.lmpDateIso ?: "",
                            eddDateIso = existing.eddDateIso ?: "",
                            isClinicianConfirmed = existing.isClinicianConfirmed,
                            healthcareFacility = existing.healthcareFacility,
                            emergencyContactName = existing.emergencyContactName,
                            emergencyContactPhone = existing.emergencyContactPhone,
                            pregnancyNotes = existing.pregnancyNotes
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            displayName = "",
                            lmpDateIso = "",
                            eddDateIso = ""
                        )
                    }
                }
                recalculatePreview()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, loadError = "Unable to load your saved profile. Retry before making changes.") }
            }
        }
    }

    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name, displayNameError = null) }
    }

    fun onDatingMethodChanged(method: DatingMethod) {
        _uiState.update { current ->
            val newEdd = if (method == DatingMethod.CLINICIAN_EDD && current.eddDateIso.isBlank()) {
                current.calculatedEddIso ?: ""
            } else current.eddDateIso

            val newLmp = if (method == DatingMethod.LMP && current.lmpDateIso.isBlank()) {
                current.calculatedLmpIso ?: ""
            } else current.lmpDateIso

            current.copy(
                datingMethod = method,
                dateError = null,
                eddDateIso = newEdd,
                lmpDateIso = newLmp,
                isClinicianConfirmed = if (method == DatingMethod.CLINICIAN_EDD) true else current.isClinicianConfirmed
            )
        }
        recalculatePreview()
    }

    fun onLmpDateChanged(isoDate: String) {
        _uiState.update { it.copy(lmpDateIso = isoDate, dateError = null) }
        recalculatePreview()
    }

    fun onEddDateChanged(isoDate: String) {
        _uiState.update { it.copy(eddDateIso = isoDate, dateError = null) }
        recalculatePreview()
    }

    fun onClinicianConfirmedChanged(confirmed: Boolean) {
        _uiState.update { it.copy(isClinicianConfirmed = confirmed) }
    }

    fun onHealthcareFacilityChanged(facility: String) {
        _uiState.update { it.copy(healthcareFacility = facility) }
    }

    fun onEmergencyContactNameChanged(name: String) {
        _uiState.update { it.copy(emergencyContactName = name) }
    }

    fun onEmergencyContactPhoneChanged(phone: String) {
        _uiState.update { it.copy(emergencyContactPhone = phone) }
    }

    fun onPregnancyNotesChanged(notes: String) {
        _uiState.update { it.copy(pregnancyNotes = notes) }
    }

    private fun recalculatePreview() {
        val state = _uiState.value
        val today = currentDateProvider()

        val tempProfile = PregnancyProfile(
            displayName = state.displayName,
            datingMethod = state.datingMethod,
            lmpDateIso = if (state.datingMethod == DatingMethod.LMP) state.lmpDateIso else null,
            eddDateIso = if (state.datingMethod == DatingMethod.CLINICIAN_EDD) state.eddDateIso else null,
            isClinicianConfirmed = state.isClinicianConfirmed
        )

        val progress = PregnancyCalculator.calculateProgress(tempProfile, today)

        var calcEdd: String? = null
        var calcLmp: String? = null

        if (state.datingMethod == DatingMethod.LMP) {
            val lmp = PregnancyCalculator.parseIsoDate(state.lmpDateIso)
            if (lmp != null) {
                calcEdd = runCatching { PregnancyCalculator.calculateEddFromLmp(lmp).toString() }.getOrNull()
            }
        } else {
            val edd = PregnancyCalculator.parseIsoDate(state.eddDateIso)
            if (edd != null) {
                calcLmp = runCatching { PregnancyCalculator.calculateLmpFromEdd(edd).toString() }.getOrNull()
            }
        }

        _uiState.update {
            it.copy(
                calculatedEddIso = calcEdd,
                calculatedLmpIso = calcLmp,
                calculatedProgress = progress
            )
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.isLoading || state.loadError != null || state.isSaving || state.isSavedSuccess) return
        var hasError = false

        if (state.displayName.trim().isEmpty()) {
            _uiState.update { it.copy(displayNameError = "Display name is required.") }
            hasError = true
        }

        val today = currentDateProvider()

        if (state.datingMethod == DatingMethod.LMP) {
            val lmpDate = PregnancyCalculator.parseIsoDate(state.lmpDateIso)
            if (lmpDate == null) {
                _uiState.update { it.copy(dateError = "Please enter a valid Last Menstrual Period date (YYYY-MM-DD).") }
                hasError = true
            } else if (lmpDate.isAfter(today)) {
                _uiState.update { it.copy(dateError = "LMP date cannot be in the future.") }
                hasError = true
            } else if (lmpDate.isBefore(today.minusDays(300))) {
                _uiState.update { it.copy(dateError = "LMP date is beyond supported gestational range (> 42 weeks).") }
                hasError = true
            }
        } else {
            val eddDate = PregnancyCalculator.parseIsoDate(state.eddDateIso)
            if (eddDate == null) {
                _uiState.update { it.copy(dateError = "Please enter a valid Estimated Delivery Date (YYYY-MM-DD).") }
                hasError = true
            } else if (eddDate.isBefore(today.minusDays(30))) {
                _uiState.update { it.copy(dateError = "EDD date cannot be more than 30 days in the past.") }
                hasError = true
            } else if (eddDate.isAfter(today.plusDays(300))) {
                _uiState.update { it.copy(dateError = "EDD date is beyond supported gestational range.") }
                hasError = true
            }
        }

        if (hasError) return
        if (state.calculatedProgress == null) {
            _uiState.update { it.copy(dateError = "Please review the pregnancy dates before saving.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            val eddIso = if (state.datingMethod == DatingMethod.CLINICIAN_EDD) {
                state.eddDateIso
            } else {
                state.calculatedEddIso
            }

            val lmpIso = if (state.datingMethod == DatingMethod.LMP) {
                state.lmpDateIso
            } else {
                state.calculatedLmpIso
            }

            val profile = PregnancyProfile(
                displayName = state.displayName.trim(),
                datingMethod = state.datingMethod,
                lmpDateIso = lmpIso,
                eddDateIso = eddIso,
                isClinicianConfirmed = state.isClinicianConfirmed,
                healthcareFacility = state.healthcareFacility.trim(),
                emergencyContactName = state.emergencyContactName.trim(),
                emergencyContactPhone = state.emergencyContactPhone.trim(),
                pregnancyNotes = state.pregnancyNotes.trim()
            )

            try {
                repository.savePregnancyProfile(profile)
                _uiState.update { it.copy(isSaving = false, isSavedSuccess = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = "Unable to save your profile. Your changes are still here; please retry.") }
            }
        }
    }

    class Factory(
        private val repository: PregnancyProfileRepository,
        private val currentDateProvider: () -> LocalDate = { LocalDate.now() }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PregnancyProfileViewModel(repository, currentDateProvider) as T
        }
    }
}
