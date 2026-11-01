package com.monicacare.app.stability

import com.monicacare.app.core.calculator.PregnancyCalculator
import com.monicacare.app.core.data.OnboardingPreferences
import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import com.monicacare.app.feature.home.HomeViewModel
import com.monicacare.app.feature.onboarding.OnboardingViewModel
import com.monicacare.app.feature.pregnancy.JourneyViewModel
import com.monicacare.app.feature.pregnancy.PregnancyProfileViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StabilityRegressionTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2025, 5, 1)
    private val profile = PregnancyProfile(displayName = "Isolated QA", lmpDateIso = "2025-01-01")

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class Repository : PregnancyProfileRepository {
        val records = MutableStateFlow<PregnancyProfile?>(null)
        var failRead = false
        var failSave = false
        var reads = 0
        var saves = 0
        var afterEmit: (suspend () -> Unit)? = null
        override val pregnancyProfile = flow {
            reads++
            if (failRead) throw IOException("Read failed")
            records.collect {
                emit(it)
                afterEmit?.invoke()
            }
        }
        override suspend fun savePregnancyProfile(profile: PregnancyProfile) {
            saves++
            if (failSave) throw IOException("Save failed")
            records.value = profile
        }
        override suspend fun clearProfile() { records.value = null }
    }

    @Test fun loadingEmptyContentAndProfileUpdatesAreDistinct() = runTest {
        val repo = Repository()
        val home = HomeViewModel(repo, { today })
        val journey = JourneyViewModel(repo, { today })
        assertTrue(home.uiState.value.isLoading)
        assertTrue(journey.uiState.value.isLoading)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(home.uiState.value.isLoading)
        assertFalse(journey.uiState.value.isLoading)
        assertFalse(home.uiState.value.hasProfile)
        assertFalse(journey.uiState.value.hasProfile)
        repo.savePregnancyProfile(profile)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Isolated QA", home.uiState.value.displayName)
        assertEquals(profile, journey.uiState.value.pregnancyProfile)
        repo.savePregnancyProfile(profile.copy(lmpDateIso = "2025-02-01"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(12, home.uiState.value.pregnancyProgress!!.currentWeek)
        assertEquals(home.uiState.value.pregnancyProgress, journey.uiState.value.pregnancyProgress)
        assertEquals(2, repo.reads) // one observation per ViewModel
    }

    @Test fun readFailureRetriesResubscribeAndRecoverOnBothScreens() = runTest {
        val repo = Repository().apply { failRead = true }
        val home = HomeViewModel(repo, { today })
        val journey = JourneyViewModel(repo, { today })
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(home.uiState.value.errorMessage)
        assertNotNull(journey.uiState.value.errorMessage)
        assertFalse(home.uiState.value.isLoading)
        assertFalse(journey.uiState.value.isLoading)
        repo.failRead = false
        repo.records.value = profile
        home.loadProfile()
        journey.loadJourney()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, repo.reads)
        assertNull(home.uiState.value.errorMessage)
        assertNull(journey.uiState.value.errorMessage)
        assertTrue(home.uiState.value.hasProfile)
        assertTrue(journey.uiState.value.hasProfile)
        assertEquals(0, repo.saves)
    }

    @Test fun failureAfterContentKeepsLastKnownProfile() = runTest {
        val repo = Repository().apply {
            records.value = profile
            afterEmit = { throw IOException("Disk unavailable") }
        }
        val home = HomeViewModel(repo, { today })
        val journey = JourneyViewModel(repo, { today })
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(home.uiState.value.errorMessage)
        assertNotNull(journey.uiState.value.errorMessage)
        assertEquals(profile, home.uiState.value.pregnancyProfile)
        assertEquals(profile, journey.uiState.value.pregnancyProfile)
        assertNotNull(home.uiState.value.pregnancyProgress)
        assertNotNull(journey.uiState.value.pregnancyProgress)
    }

    @Test fun missingDevelopmentNeverReplacesValidProgressWithSampleData() = runTest {
        val repo = Repository().apply { records.value = profile }
        val home = HomeViewModel(repo, { today }, { null })
        val journey = JourneyViewModel(repo, { today }, { null })
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(home.uiState.value.hasProfile)
        assertTrue(journey.uiState.value.hasProfile)
        assertNotNull(home.uiState.value.pregnancyProgress)
        assertNotNull(journey.uiState.value.pregnancyProgress)
        assertNull(home.uiState.value.babyDevelopment)
        assertNull(journey.uiState.value.babyDevelopment)
        home.refreshCalculations()
        journey.refreshCalculations()
        assertNull(home.uiState.value.babyDevelopment)
    }

    @Test fun invalidStoredDatesKeepProfileWithoutFictionalProgress() = runTest {
        val repo = Repository().apply { records.value = profile.copy(lmpDateIso = "bad date") }
        val home = HomeViewModel(repo, { today })
        val journey = JourneyViewModel(repo, { today })
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(home.uiState.value.hasProfile)
        assertTrue(journey.uiState.value.hasProfile)
        assertNull(home.uiState.value.pregnancyProgress)
        assertNull(journey.uiState.value.pregnancyProgress)
        assertNull(home.uiState.value.babyDevelopment)
    }

    @Test fun calendarRefreshUsesNewDateWithoutAnotherRepositoryCollector() = runTest {
        val repo = Repository().apply { records.value = profile }
        var date = LocalDate.of(2025, 1, 7)
        val home = HomeViewModel(repo, { date })
        val journey = JourneyViewModel(repo, { date })
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(6, home.uiState.value.pregnancyProgress!!.currentDay)
        date = date.plusDays(1)
        home.refreshCalculations()
        journey.refreshCalculations()
        assertEquals(1, home.uiState.value.pregnancyProgress!!.currentWeek)
        assertEquals(0, journey.uiState.value.pregnancyProgress!!.currentDay)
        assertEquals(2, repo.reads)
    }

    @Test fun profileReadFailureBlocksSaveAndRetryLoadsExistingRecord() = runTest {
        val repo = Repository().apply { failRead = true; records.value = profile }
        val editor = PregnancyProfileViewModel(repo, { today })
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(editor.uiState.value.loadError)
        editor.onDisplayNameChanged("Must not overwrite")
        editor.onLmpDateChanged("2025-01-01")
        editor.saveProfile()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repo.saves)
        repo.failRead = false
        editor.loadExistingProfile()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(editor.uiState.value.loadError)
        assertEquals("Isolated QA", editor.uiState.value.displayName)
    }

    @Test fun profileSaveFailureKeepsDraftAndDuplicateSaveIsBlocked() = runTest {
        val repo = Repository().apply { records.value = profile; failSave = true }
        val editor = PregnancyProfileViewModel(repo, { today })
        dispatcher.scheduler.advanceUntilIdle()
        editor.onDisplayNameChanged("Updated QA")
        editor.saveProfile()
        editor.saveProfile()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.saves)
        assertFalse(editor.uiState.value.isSavedSuccess)
        assertNotNull(editor.uiState.value.saveError)
        assertEquals("Updated QA", editor.uiState.value.displayName)
        assertEquals(profile, repo.records.value)
        repo.failSave = false
        editor.saveProfile()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(editor.uiState.value.isSavedSuccess)
        assertEquals("Updated QA", repo.records.value!!.displayName)
    }

    @Test fun onboardingRemainsLoadingUntilFirstPreferenceAndCompletionAwaitsSave() = runTest {
        val initialRead = CompletableDeferred<Boolean>()
        val saveGate = CompletableDeferred<Unit>()
        var saves = 0
        val prefs = object : OnboardingPreferences {
            override val isOnboardingCompleted = flow { emit(initialRead.await()); awaitCancellation() }
            override suspend fun setOnboardingCompleted(completed: Boolean) { saves++; saveGate.await() }
        }
        val vm = OnboardingViewModel(prefs)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.completed)
        initialRead.complete(false)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, vm.uiState.value.completed)
        vm.completeOnboarding()
        vm.completeOnboarding()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, saves)
        assertTrue(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.completionSaved)
        saveGate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.completionSaved)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test fun onboardingReadAndSaveFailuresAreRecoverable() = runTest {
        var failRead = true
        var failSave = true
        val prefs = object : OnboardingPreferences {
            override val isOnboardingCompleted = flow {
                if (failRead) throw IOException("Read failure")
                emit(false)
            }
            override suspend fun setOnboardingCompleted(completed: Boolean) {
                if (failSave) throw IOException("Save failure")
            }
        }
        val vm = OnboardingViewModel(prefs)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.completed)
        assertNotNull(vm.uiState.value.errorMessage)
        failRead = false
        vm.loadPreferences()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        vm.completeOnboarding()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.completionSaved)
        assertNotNull(vm.uiState.value.errorMessage)
        failSave = false
        vm.completeOnboarding()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.completionSaved)
    }

    @Test fun preferenceReadRetryAfterLoadedValueNeverWritesCompletion() = runTest {
        var failRead = true
        var saves = 0
        var reads = 0
        val prefs = object : OnboardingPreferences {
            override val isOnboardingCompleted = flow {
                reads++
                emit(false)
                if (failRead) throw IOException("Read failed after initial value")
            }
            override suspend fun setOnboardingCompleted(completed: Boolean) { saves++ }
        }
        val vm = OnboardingViewModel(prefs)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, vm.uiState.value.completed)
        assertTrue(vm.uiState.value.readFailed)
        failRead = false
        vm.retry()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, reads)
        assertEquals(0, saves)
        assertNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.completionSaved)
    }

    @Test fun progressRejectsNonFiniteValuesAndClampsFiniteValues() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, PregnancyCalculator.safeProgress(value), 0f)
        }
        assertEquals(0f, PregnancyCalculator.safeProgress(-1f), 0f)
        assertEquals(1f, PregnancyCalculator.safeProgress(2f), 0f)
        assertEquals(.5f, PregnancyCalculator.safeProgress(.5f), 0f)
        assertEquals(0f, PregnancyCalculator.calculateProgressPercent(Long.MIN_VALUE), 0f)
        assertEquals(1f, PregnancyCalculator.calculateProgressPercent(Long.MAX_VALUE), 0f)
    }

    @Test fun clinicianMethodOverridesInconsistentLmpAndDueDateRemainsExact() {
        val p = profile.copy(datingMethod = DatingMethod.CLINICIAN_EDD,
            lmpDateIso = "2020-01-01", eddDateIso = "2025-10-15")
        val progress = PregnancyCalculator.calculateProgress(p, LocalDate.of(2025, 10, 15))!!
        assertEquals(40, progress.currentWeek)
        assertEquals(0, progress.daysRemaining)
        assertFalse(progress.isOverdue)
        val overdue = PregnancyCalculator.calculateProgress(p, LocalDate.of(2025, 10, 16))!!
        assertEquals(1, overdue.daysOverdue)
    }

    @Test fun invalidFutureAndExtremeDatesDoNotCrashOrInventTimeline() {
        assertNull(PregnancyCalculator.calculateProgress(profile.copy(lmpDateIso = "2025-05-02"), today))
        assertNull(PregnancyCalculator.calculateProgress(profile.copy(lmpDateIso = "+999999999-12-31"), today))
        assertNull(PregnancyCalculator.calculateProgress(profile.copy(datingMethod = DatingMethod.CLINICIAN_EDD,
            eddDateIso = "-999999999-01-01"), today))
        assertNull(PregnancyCalculator.parseIsoDate("2025-02-29"))
        assertEquals(LocalDate.of(2024, 2, 29), PregnancyCalculator.parseIsoDate("2024-02-29"))
        val boundary = LocalDate.of(2025, 1, 1).plusDays(97)
        val progress = PregnancyCalculator.calculateProgress(profile, boundary)!!
        assertEquals(13, progress.currentWeek)
        assertEquals(6, progress.currentDay)
        assertEquals(1, progress.trimester)
    }
}
