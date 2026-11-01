package com.monicacare.app.feature.pregnancy

import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import com.monicacare.app.feature.home.FakePregnancyProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PregnancyProfileViewModelTest {

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
    fun testInitialStateEmptyWhenNoExistingProfile() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = PregnancyProfileViewModel(fakeRepo, currentDateProvider = { fixedToday })

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("", state.displayName)
        assertEquals(DatingMethod.LMP, state.datingMethod)
        assertFalse(state.isSavedSuccess)
    }

    @Test
    fun testInitialStatePopulatedWhenExistingProfilePresent() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        fakeRepo.profileFlow.value = PregnancyProfile(
            displayName = "Monica Test",
            datingMethod = DatingMethod.CLINICIAN_EDD,
            eddDateIso = "2025-08-08",
            isClinicianConfirmed = true,
            healthcareFacility = "Kalingalinga Clinic"
        )

        val viewModel = PregnancyProfileViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Monica Test", state.displayName)
        assertEquals(DatingMethod.CLINICIAN_EDD, state.datingMethod)
        assertEquals("2025-08-08", state.eddDateIso)
        assertTrue(state.isClinicianConfirmed)
        assertEquals("Kalingalinga Clinic", state.healthcareFacility)
        assertNotNull(state.calculatedProgress)
    }

    @Test
    fun testValidationRejectsEmptyDisplayName() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = PregnancyProfileViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLmpDateChanged("2025-01-01")
        viewModel.onDisplayNameChanged("   ") // blank
        viewModel.saveProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.displayNameError)
        assertFalse(state.isSavedSuccess)
        assertNull(fakeRepo.profileFlow.value)
    }

    @Test
    fun testValidationRejectsFutureLmpDate() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = PregnancyProfileViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDisplayNameChanged("Monica")
        viewModel.onDatingMethodChanged(DatingMethod.LMP)
        viewModel.onLmpDateChanged("2025-05-10") // 9 days in the future relative to 2025-05-01
        viewModel.saveProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("LMP date cannot be in the future.", state.dateError)
        assertFalse(state.isSavedSuccess)
    }

    @Test
    fun testValidationRejectsInvalidDateFormat() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = PregnancyProfileViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDisplayNameChanged("Monica")
        viewModel.onDatingMethodChanged(DatingMethod.LMP)
        viewModel.onLmpDateChanged("not-a-date")
        viewModel.saveProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.dateError)
        assertFalse(state.isSavedSuccess)
    }

    @Test
    fun testSuccessfulSaveWithLmpDatingMethod() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = PregnancyProfileViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDisplayNameChanged("Chileshe")
        viewModel.onDatingMethodChanged(DatingMethod.LMP)
        viewModel.onLmpDateChanged("2024-11-01")
        viewModel.saveProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedSuccess)
        assertNull(state.dateError)
        assertNull(state.displayNameError)

        val saved = fakeRepo.profileFlow.value
        assertNotNull(saved)
        assertEquals("Chileshe", saved!!.displayName)
        assertEquals("2024-11-01", saved.lmpDateIso)
        // 2024-11-01 + 280 days = 2025-08-08
        assertEquals("2025-08-08", saved.eddDateIso)
    }

    @Test
    fun testSuccessfulSaveWithClinicianEddDatingMethod() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = PregnancyProfileViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDisplayNameChanged("Mutinta")
        viewModel.onDatingMethodChanged(DatingMethod.CLINICIAN_EDD)
        viewModel.onEddDateChanged("2025-09-15")
        viewModel.onClinicianConfirmedChanged(true)
        viewModel.saveProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSavedSuccess)

        val saved = fakeRepo.profileFlow.value
        assertNotNull(saved)
        assertEquals("Mutinta", saved!!.displayName)
        assertEquals("2025-09-15", saved.eddDateIso)
        assertTrue(saved.isClinicianConfirmed)
    }

    @Test
    fun testSwitchingDatingMethodPreservesAndRecalculatesPreview() = runTest {
        val fakeRepo = FakePregnancyProfileRepository()
        val viewModel = PregnancyProfileViewModel(fakeRepo, currentDateProvider = { fixedToday })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDisplayNameChanged("Test Mother")
        viewModel.onDatingMethodChanged(DatingMethod.LMP)
        viewModel.onLmpDateChanged("2024-11-01")

        // calculatedEddIso should be 2025-08-08
        val stateAfterLmp = viewModel.uiState.value
        assertEquals("2025-08-08", stateAfterLmp.calculatedEddIso)

        // Switch to CLINICIAN_EDD
        viewModel.onDatingMethodChanged(DatingMethod.CLINICIAN_EDD)
        val stateAfterSwitch = viewModel.uiState.value
        assertEquals(DatingMethod.CLINICIAN_EDD, stateAfterSwitch.datingMethod)
        assertEquals("2025-08-08", stateAfterSwitch.eddDateIso)
        assertNotNull(stateAfterSwitch.calculatedProgress)
    }
}
