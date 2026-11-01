package com.monicacare.app.feature.home

import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class FakePregnancyProfileRepository : PregnancyProfileRepository {
    val profileFlow = MutableStateFlow<PregnancyProfile?>(null)
    override val pregnancyProfile: Flow<PregnancyProfile?> = profileFlow

    override suspend fun savePregnancyProfile(profile: PregnancyProfile) {
        profileFlow.value = profile
    }

    override suspend fun clearProfile() {
        profileFlow.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testHomeViewModelNoProfileState() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = HomeViewModel(fakeRepo, currentDateProvider = { LocalDate.of(2025, 5, 1) })

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasProfile)
        assertEquals("Mama", state.displayName)
    }

    @Test
    fun testHomeViewModelProfileAvailableState() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Chileshe",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2024-11-01"
        )

        val viewModel = HomeViewModel(fakeRepo, currentDateProvider = { LocalDate.of(2025, 5, 1) })

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasProfile)
        assertEquals("Chileshe", state.displayName)
        assertNotNull(state.pregnancyProgress)
        assertNotNull(state.babyDevelopment)
    }

    @Test
    fun testHomeViewModelProfileReactiveUpdates() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = HomeViewModel(fakeRepo, currentDateProvider = { LocalDate.of(2025, 5, 1) })

        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasProfile)

        // Save new profile
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Monica",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2024-11-01"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasProfile)
        assertEquals("Monica", state.displayName)
        assertNotNull(state.pregnancyProgress)

        // Clear profile
        fakeRepo.profileFlow.value = null
        testDispatcher.scheduler.advanceUntilIdle()

        val clearedState = viewModel.uiState.value
        assertFalse(clearedState.hasProfile)
        assertEquals("Mama", clearedState.displayName)
    }

    @Test
    fun testHomeViewModelOverdueState() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        // LMP: 2024-01-01 -> EDD: 2024-10-07. Current date: 2024-10-15 (8 days overdue)
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2024-01-01"
        )

        val viewModel = HomeViewModel(fakeRepo, currentDateProvider = { LocalDate.of(2024, 10, 15) })
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.pregnancyProgress)
        assertTrue(state.pregnancyProgress!!.isOverdue)
        assertEquals(8, state.pregnancyProgress!!.daysOverdue)
    }

    @Test
    fun testHomeViewModelRefreshCalculations() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Refresh Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        var testDate = LocalDate.of(2025, 2, 1)
        val viewModel = HomeViewModel(fakeRepo, currentDateProvider = { testDate })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.pregnancyProgress?.currentWeek)

        // Advance simulated date to week 8
        testDate = LocalDate.of(2025, 2, 26)
        viewModel.refreshCalculations()

        assertEquals(8, viewModel.uiState.value.pregnancyProgress?.currentWeek)
    }
}
