@file:Suppress("NewApi")

package com.monicacare.app.core.calculator

import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import com.monicacare.app.core.model.PregnancyProgress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

object PregnancyCalculator {

    const val PREGNANCY_DURATION_DAYS = 280L

    fun calculateEddFromLmp(lmpDate: LocalDate): LocalDate {
        return lmpDate.plusDays(PREGNANCY_DURATION_DAYS)
    }

    fun calculateLmpFromEdd(eddDate: LocalDate): LocalDate {
        return eddDate.minusDays(PREGNANCY_DURATION_DAYS)
    }

    fun calculateGestationalDays(lmpDate: LocalDate, currentDate: LocalDate): Long {
        val days = ChronoUnit.DAYS.between(lmpDate, currentDate)
        return max(0L, days)
    }

    fun calculateCompletedWeeks(gestationalDays: Long): Int {
        return (gestationalDays.coerceAtLeast(0) / 7).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun calculateAdditionalDays(gestationalDays: Long): Int {
        return (gestationalDays % 7).toInt()
    }

    fun calculateTrimester(completedWeeks: Int): Int {
        return when {
            completedWeeks < 14 -> 1
            completedWeeks in 14..27 -> 2
            else -> 3
        }
    }

    fun calculateDaysRemaining(currentDate: LocalDate, eddDate: LocalDate): Long {
        val days = ChronoUnit.DAYS.between(currentDate, eddDate)
        return max(0L, days)
    }

    fun calculateProgressPercent(gestationalDays: Long): Float {
        val raw = gestationalDays.toFloat() / PREGNANCY_DURATION_DAYS.toFloat()
        return safeProgress(raw)
    }

    fun safeProgress(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    fun calculateProgress(
        profile: PregnancyProfile,
        currentDate: LocalDate = LocalDate.now(),
    ): PregnancyProgress? = try {
        calculateProgressOrNull(profile, currentDate)
    } catch (_: java.time.DateTimeException) {
        null
    }

    private fun calculateProgressOrNull(profile: PregnancyProfile, currentDate: LocalDate): PregnancyProgress? {
        val lmpDate: LocalDate = when (profile.datingMethod) {
            DatingMethod.LMP -> {
                profile.lmpDateIso?.let { parseIsoDate(it) }
            }
            DatingMethod.CLINICIAN_EDD -> {
                profile.eddDateIso?.let { parseIsoDate(it) }?.let { edd ->
                    calculateLmpFromEdd(edd)
                }
            }
        } ?: return null

        val eddDate: LocalDate = when (profile.datingMethod) {
            DatingMethod.CLINICIAN_EDD -> {
                profile.eddDateIso?.let { parseIsoDate(it) } ?: calculateEddFromLmp(lmpDate)
            }
            DatingMethod.LMP -> {
                calculateEddFromLmp(lmpDate)
            }
        }

        if (lmpDate.isAfter(currentDate)) return null

        val gestationalDays = calculateGestationalDays(lmpDate, currentDate)
        val weeks = calculateCompletedWeeks(gestationalDays)
        val days = calculateAdditionalDays(gestationalDays)
        val trimester = calculateTrimester(weeks)
        val daysRemaining = calculateDaysRemaining(currentDate, eddDate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val progressPercent = calculateProgressPercent(gestationalDays)
        val isOverdue = currentDate.isAfter(eddDate)
        val daysOverdue = if (isOverdue) ChronoUnit.DAYS.between(eddDate, currentDate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0

        val eddFormatted = formatDisplayDate(eddDate)

        return PregnancyProgress(
            currentWeek = weeks,
            currentDay = days,
            totalWeeks = 40,
            trimester = trimester,
            estimatedDueDateFormatted = eddFormatted,
            daysRemaining = daysRemaining,
            progressPercent = progressPercent,
            isOverdue = isOverdue,
            daysOverdue = daysOverdue
        )
    }

    fun parseIsoDate(isoString: String): LocalDate? {
        val trimmed = isoString.trim()
        if (trimmed.isEmpty()) return null
        return try {
            LocalDate.parse(trimmed)
        } catch (_: Exception) {
            try {
                val parts = trimmed.replace("/", "-").split("-")
                if (parts.size == 3) {
                    val year = parts[0].toIntOrNull()
                    val month = parts[1].toIntOrNull()
                    val day = parts[2].toIntOrNull()
                    if (year != null && month != null && day != null && month in 1..12 && day in 1..31) {
                        LocalDate.of(year, month, day)
                    } else null
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun formatIsoDate(date: LocalDate): String {
        return date.toString()
    }

    fun formatDisplayDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
        return date.format(formatter)
    }
}
