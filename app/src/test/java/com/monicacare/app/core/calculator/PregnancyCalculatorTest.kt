package com.monicacare.app.core.calculator

import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PregnancyCalculatorTest {

    @Test
    fun testPregnancyAt0Weeks() {
        val lmp = LocalDate.of(2025, 1, 1)
        val currentDate = LocalDate.of(2025, 1, 1)

        val profile = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)

        assertNotNull(progress)
        assertEquals(0, progress!!.currentWeek)
        assertEquals(0, progress.currentDay)
        assertEquals(1, progress.trimester)
        assertEquals(280, progress.daysRemaining)
        assertEquals(0.0f, progress.progressPercent, 0.001f)
        assertFalse(progress.isOverdue)
    }

    @Test
    fun testPregnancyAt12Weeks() {
        val lmp = LocalDate.of(2025, 1, 1)
        val currentDate = lmp.plusDays(12 * 7) // 84 days

        val profile = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)

        assertNotNull(progress)
        assertEquals(12, progress!!.currentWeek)
        assertEquals(0, progress.currentDay)
        assertEquals(1, progress.trimester)
    }

    @Test
    fun testPregnancyAt14Weeks() {
        val lmp = LocalDate.of(2025, 1, 1)
        val currentDate = lmp.plusDays(14 * 7) // 98 days

        val profile = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)

        assertNotNull(progress)
        assertEquals(14, progress!!.currentWeek)
        assertEquals(0, progress.currentDay)
        assertEquals(2, progress.trimester) // 2nd trimester starts at 14 weeks
    }

    @Test
    fun testPregnancyAt27WeeksAnd6Days() {
        val lmp = LocalDate.of(2025, 1, 1)
        val currentDate = lmp.plusDays(27 * 7 + 6) // 195 days

        val profile = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)

        assertNotNull(progress)
        assertEquals(27, progress!!.currentWeek)
        assertEquals(6, progress.currentDay)
        assertEquals(2, progress.trimester) // Still 2nd trimester until 28 completed weeks
    }

    @Test
    fun testPregnancyAt28Weeks() {
        val lmp = LocalDate.of(2025, 1, 1)
        val currentDate = lmp.plusDays(28 * 7) // 196 days

        val profile = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)

        assertNotNull(progress)
        assertEquals(28, progress!!.currentWeek)
        assertEquals(0, progress.currentDay)
        assertEquals(3, progress.trimester) // 3rd trimester starts at 28 completed weeks
    }

    @Test
    fun testPregnancyAt40Weeks() {
        val lmp = LocalDate.of(2025, 1, 1)
        val currentDate = lmp.plusDays(280) // 280 days

        val profile = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)

        assertNotNull(progress)
        assertEquals(40, progress!!.currentWeek)
        assertEquals(0, progress.currentDay)
        assertEquals(3, progress.trimester)
        assertEquals(0, progress.daysRemaining)
        assertEquals(1.0f, progress.progressPercent, 0.001f)
        assertFalse(progress.isOverdue)
    }

    @Test
    fun testPregnancyBeyond40Weeks() {
        val lmp = LocalDate.of(2025, 1, 1)
        val currentDate = lmp.plusDays(287) // 41 weeks

        val profile = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "2025-01-01"
        )

        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)

        assertNotNull(progress)
        assertEquals(41, progress!!.currentWeek)
        assertEquals(0, progress.currentDay)
        assertEquals(3, progress.trimester)
        assertEquals(0, progress.daysRemaining)
        assertEquals(1.0f, progress.progressPercent, 0.001f) // Clamped to 1.0f
        assertTrue(progress.isOverdue)
    }

    @Test
    fun testLeapYearDateCalculations() {
        val lmpLeapYear = LocalDate.of(2024, 2, 1) // 2024 is a leap year
        val edd = PregnancyCalculator.calculateEddFromLmp(lmpLeapYear)

        // 2024-02-01 + 280 days = 2024-11-07
        assertEquals(LocalDate.of(2024, 11, 7), edd)
    }

    @Test
    fun testMonthAndYearTransitions() {
        val lmp = LocalDate.of(2024, 11, 15)
        val edd = PregnancyCalculator.calculateEddFromLmp(lmp)

        // 2024-11-15 + 280 days = 2025-08-22
        assertEquals(LocalDate.of(2025, 8, 22), edd)
    }

    @Test
    fun testInvalidInputDates() {
        val parsedInvalid = PregnancyCalculator.parseIsoDate("invalid-date-string")
        assertNull(parsedInvalid)

        val invalidProfile = PregnancyProfile(
            displayName = "Test",
            datingMethod = DatingMethod.LMP,
            lmpDateIso = "not-a-valid-date"
        )

        val progress = PregnancyCalculator.calculateProgress(invalidProfile, LocalDate.of(2025, 1, 1))
        assertNull(progress)
    }

    @Test
    fun testClinicianConfirmedEddCalculations() {
        // Clinician confirmed EDD: 2025-10-15
        val edd = LocalDate.of(2025, 10, 15)
        val derivedLmp = PregnancyCalculator.calculateLmpFromEdd(edd)
        assertEquals(LocalDate.of(2025, 1, 8), derivedLmp)

        val profile = PregnancyProfile(
            displayName = "Clinician Test",
            datingMethod = DatingMethod.CLINICIAN_EDD,
            eddDateIso = "2025-10-15",
            isClinicianConfirmed = true
        )

        val currentDate = derivedLmp.plusDays(140) // 20 weeks
        val progress = PregnancyCalculator.calculateProgress(profile, currentDate)

        assertNotNull(progress)
        assertEquals(20, progress!!.currentWeek)
        assertEquals(2, progress.trimester)
        assertEquals("15 Oct 2025", profile.estimatedDeliveryDateFormatted)
    }

    @Test
    fun testProgressFractionBoundaries() {
        assertEquals(0.0f, PregnancyCalculator.calculateProgressPercent(0), 0.001f)
        assertEquals(0.5f, PregnancyCalculator.calculateProgressPercent(140), 0.001f)
        assertEquals(1.0f, PregnancyCalculator.calculateProgressPercent(280), 0.001f)
        assertEquals(1.0f, PregnancyCalculator.calculateProgressPercent(350), 0.001f) // Clamped
    }
}
