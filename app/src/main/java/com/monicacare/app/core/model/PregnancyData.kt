package com.monicacare.app.core.model

enum class DatingMethod {
    LMP,
    CLINICIAN_EDD
}

data class PregnancyProfile(
    val displayName: String = "Monica",
    val datingMethod: DatingMethod = DatingMethod.LMP,
    val lmpDateIso: String? = "2024-11-01",
    val eddDateIso: String? = "2025-08-08",
    val isClinicianConfirmed: Boolean = true,
    val pregnancyStatus: String = "Active",
    val healthcareFacility: String = "Lusaka General Hospital",
    val emergencyContactName: String = "Chileshe (Husband)",
    val emergencyContactPhone: String = "+260 97 1234567",
    val pregnancyNotes: String = ""
) {
    val lastMenstrualPeriodFormatted: String
        get() = formatIsoDateToDisplay(lmpDateIso) ?: "Not set"

    val estimatedDeliveryDateFormatted: String
        get() = formatIsoDateToDisplay(eddDateIso) ?: "Not set"

    companion object {
        fun formatIsoDateToDisplay(isoDate: String?): String? {
            if (isoDate == null || isoDate.trim().isEmpty()) return null
            return try {
                val parts = isoDate.split("-")
                if (parts.size != 3) return isoDate
                val year = parts[0]
                val monthInt = parts[1].toIntOrNull() ?: return isoDate
                val day = parts[2].toIntOrNull() ?: return isoDate
                val monthNames = listOf(
                    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                )
                if (monthInt in 1..12) {
                    val formattedDay = if (day < 10) "0$day" else "$day"
                    "$formattedDay ${monthNames[monthInt - 1]} $year"
                } else isoDate
            } catch (_: Exception) {
                isoDate
            }
        }
    }
}

data class PregnancyProgress(
    val currentWeek: Int = 24,
    val currentDay: Int = 3,
    val totalWeeks: Int = 40,
    val trimester: Int = 2,
    val estimatedDueDateFormatted: String = "08 Aug 2025",
    val daysRemaining: Int = 110,
    val progressPercent: Float = 0.61f,
    val isOverdue: Boolean = false,
    val daysOverdue: Int = 0
)

data class BabyDevelopment(
    val week: Int = 24,
    val fruitComparison: String = "Cantaloupe",
    val lengthCm: Double = 30.0,
    val weightGrams: Double = 600.0,
    val summary: String = "Your baby's inner ear is fully developed, helping them sense balance. Taste buds are forming on the tongue!",
    val milestoneDetails: List<String> = listOf(
        "Real hair is growing on the head and body (lanugo)",
        "Lungs are developing blood vessels and producing surfactant",
        "Baby responds to familiar sounds and voices"
    )
)

data class AntenatalAppointment(
    val id: String = "1",
    val title: String = "ANC Visit #4 - Second Trimester Checkup",
    val providerName: String = "Dr. Mwamba",
    val facilityName: String = "Lusaka General Hospital",
    val appointmentDate: String = "Thursday, 24 May 2025",
    val appointmentTime: String = "10:00 AM",
    val status: AppointmentStatus = AppointmentStatus.UPCOMING,
    val notes: String = "Bring Maternal Health Record Booklet and recent blood test results."
)

enum class AppointmentStatus {
    UPCOMING, COMPLETED, CANCELLED
}

data class DailyPregnancyTip(
    val id: String = "tip_24",
    val title: String = "Hydration & Nutrient-Rich Meals",
    val category: String = "Nutrition",
    val content: String = "Drinking at least 8-10 glasses of clean water daily helps maintain amniotic fluid levels and reduces swelling. Pair local leafy greens (bondwe/chibwabwa) with iron-rich foods.",
    val readTimeMinutes: Int = 2
)

object BabyDevelopmentRepository {
    private val weekData = mapOf(
        1 to BabyDevelopment(1, "Poppy Seed", 0.1, 0.1, "Conception occurs; fertilized egg implants into the uterine lining.", listOf("Cell division rapidly occurs", "Hormones signal body to support pregnancy")),
        4 to BabyDevelopment(4, "Poppy Seed", 0.2, 0.5, "The blastocyst has attached to your uterus. The neural tube is starting to form.", listOf("Placenta begins forming", "Heart cells begin beating")),
        8 to BabyDevelopment(8, "Raspberry", 1.6, 1.0, "All major organs and body structures have begun developing. Fingers and toes are forming.", listOf("Ears and nose are forming", "Baby is making subtle movements")),
        12 to BabyDevelopment(12, "Lime", 5.4, 14.0, "Reflexes are developing. Your baby can open and close fingers and curl toes.", listOf("Fingernails are appearing", "Kidneys begin producing urine")),
        16 to BabyDevelopment(16, "Avocado", 11.6, 100.0, "Your baby's eyes can blink and heart and blood vessels are fully formed.", listOf("Fingerprints are established", "Baby can suck their thumb")),
        20 to BabyDevelopment(20, "Banana", 25.6, 300.0, "Halfway mark! You may feel kicks and turns. Vernix coat protects delicate skin.", listOf("Baby swallows amniotic fluid", "Hearing is well developed")),
        24 to BabyDevelopment(24, "Cantaloupe", 30.0, 600.0, "Inner ear is fully developed for balance. Real hair is growing on the head.", listOf("Lungs produce surfactant", "Responds to familiar sounds")),
        28 to BabyDevelopment(28, "Eggplant", 37.6, 1000.0, "Third trimester! Eyes can open and close and baby can sense changes in light.", listOf("Brain develop rapid nerve connections", "Gaining protective body fat")),
        32 to BabyDevelopment(32, "Squash", 42.4, 1700.0, "Bones are fully formed, though soft. Baby practices breathing movements.", listOf("Toenails are visible", "Fetal movement is active")),
        36 to BabyDevelopment(36, "Papaya", 47.4, 2600.0, "Baby is gaining about 200g a week. Brain and lungs continue maturing.", listOf("Shedding lanugo hair", "Taking up most space in uterus")),
        40 to BabyDevelopment(40, "Watermelon", 51.2, 3400.0, "Full term! Your baby is ready to meet the world.", listOf("Reflexes coordinated", "Placenta supplies antibodies"))
    )

    fun getForWeek(week: Int): BabyDevelopment {
        val clampedWeek = week.coerceIn(1, 42)
        val matchedWeek = weekData.keys.filter { it <= clampedWeek }.maxOrNull() ?: 1
        val base = weekData[matchedWeek]!!
        return base.copy(week = clampedWeek)
    }
}

object DemonstrationData {
    val sampleProfile = PregnancyProfile()
    val sampleProgress = PregnancyProgress()
    val sampleBabyDevelopment = BabyDevelopmentRepository.getForWeek(24)
    val sampleAppointment = AntenatalAppointment()
    val sampleDailyTip = DailyPregnancyTip()
}
