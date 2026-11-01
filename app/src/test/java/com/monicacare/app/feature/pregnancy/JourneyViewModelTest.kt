package com.monicacare.app.feature.pregnancy

import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import com.monicacare.app.feature.home.FakePregnancyProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class JourneyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedToday = LocalDate.of(2025, 5, 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testJourneyViewModelNoProfileState() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = JourneyViewModel(fakeRepo, currentDateProvider = { fixedToday })

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasProfile)
        assertNull(state.pregnancyProfile)
        assertNull(state.pregnancyProgress)
        assertNull(state.babyDevelopment)
        assertNull(state.errorMessage)
    }

    @Test
    fun testJourneyViewModelProfileAvailableState() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        // 2024-11-01 to 2025-05-01 = 181 days = 25 weeks, 6 days -> Trimester 2
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Monica",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2024-11-01"
        )

        val viewModel = JourneyViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasProfile)
        assertNotNull(state.pregnancyProgress)
        assertEquals(25, state.pregnancyProgress?.currentWeek)
        assertEquals(6, state.pregnancyProgress?.currentDay)
        assertEquals(2, state.pregnancyProgress?.trimester)
        assertNotNull(state.babyDevelopment)
        assertEquals("Cantaloupe", state.babyDevelopment?.fruitComparison)
    }

    @Test
    fun testJourneyViewModelProfileReactiveUpdates() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = JourneyViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasProfile)

        // Save new profile
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Chileshe",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val stateWithProfile = viewModel.uiState.value
        assertTrue(stateWithProfile.hasProfile)
        assertNotNull(stateWithProfile.pregnancyProgress)

        // Clear profile
        fakeRepo.profileFlow.value = null
        testDispatcher.scheduler.advanceUntilIdle()

        val stateAfterClear = viewModel.uiState.value
        assertFalse(stateAfterClear.hasProfile)
        assertNull(stateAfterClear.pregnancyProgress)
        assertNull(stateAfterClear.babyDevelopment)
    }

    @Test
    fun testJourneyViewModelRefreshCalculations() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Refresh Journey Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        var testDate = LocalDate.of(2025, 2, 1) // ~4 weeks
        val viewModel = JourneyViewModel(fakeRepo, currentDateProvider = { testDate })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.pregnancyProgress?.currentWeek)

        // Advance date to week 16
        testDate = LocalDate.of(2025, 4, 23)
        viewModel.refreshCalculations()

        assertEquals(16, viewModel.uiState.value.pregnancyProgress?.currentWeek)
        assertEquals("Avocado", viewModel.uiState.value.babyDevelopment?.fruitComparison)
    }

    @Test
    fun testJourneyViewModelErrorState() = runTest {
        val errorRepo = object : PregnancyProfileRepository {
            override val pregnancyProfile: Flow<PregnancyProfile?> = flow {
                throw RuntimeException("Simulated DataStore read failure")
            }
            override suspend fun savePregnancyProfile(profile: PregnancyProfile) {}
            override suspend fun clearProfile() {}
        }

        val viewModel = JourneyViewModel(errorRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasProfile)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage?.contains("Simulated DataStore read failure") == true)
    }

    @Test
    fun testJourneyViewModelOverdueState() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        // LMP: 2024-01-01 -> EDD: 2024-10-07. Today: 2024-10-17 (10 days overdue)
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Overdue Mom",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2024-01-01"
        )

        val overdueDate = LocalDate.of(2024, 10, 17)
        val viewModel = JourneyViewModel(fakeRepo, currentDateProvider = { overdueDate })
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.pregnancyProgress)
        assertTrue(state.pregnancyProgress?.isOverdue == true)
        assertEquals(10, state.pregnancyProgress?.daysOverdue)
        assertEquals(0, state.pregnancyProgress?.daysRemaining)
    }

    @Test
    fun testJourneyViewModelClinicianEddMethod() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        // Clinician EDD: 2025-08-08 -> Derived LMP: 2024-11-01
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Clinician Confirmed",
            datingMethod = DatingMethod.CLINICIAN_EDD,
            eddDateIso = "2025-08-08",
            isClinicianConfirmed = true
        )

        val viewModel = JourneyViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasProfile)
        assertNotNull(state.pregnancyProgress)
        assertEquals(25, state.pregnancyProgress?.currentWeek)
        assertEquals(2, state.pregnancyProgress?.trimester)
        assertEquals(99, state.pregnancyProgress?.daysRemaining)
    }

    @Test
    fun testJourneyViewModelRetryRecoversFromError() = runTest {
        var shouldThrow = true
        val retryableRepo = object : PregnancyProfileRepository {
            override val pregnancyProfile: Flow<PregnancyProfile?> = flow {
                if (shouldThrow) {
                    throw RuntimeException("Temporary disk lock")
                } else {
                    emit(
                        PregnancyProfile(
                            displayName = "Recovered Mom",
                            datingMethod = DatingMethod.LMP,
                            lmpDateIso = "2024-11-01"
                        )
                    )
                }
            }
            override suspend fun savePregnancyProfile(profile: PregnancyProfile) {}
            override suspend fun clearProfile() {}
        }

        val viewModel = JourneyViewModel(retryableRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify initial error
        assertTrue(viewModel.uiState.value.errorMessage != null)
        assertFalse(viewModel.uiState.value.hasProfile)

        // Now fix error and retry
        shouldThrow = false
        viewModel.loadJourney()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify recovery
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.hasProfile)
        assertEquals("Recovered Mom", viewModel.uiState.value.pregnancyProfile?.displayName)
    }
}

