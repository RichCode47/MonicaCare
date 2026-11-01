package com.monicacare.app.core.backend

import com.monicacare.app.core.calculator.PregnancyCalculator
import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

val BackendJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

enum class PrivateTable(val wire: String) {
    PROFILE("profiles"), PREGNANCY("pregnancy_profiles"), APPOINTMENT("appointments"),
    JOURNAL("health_journal"), SETTINGS("user_settings")
}

@Serializable
data class PendingWrite(val mutationId: String, val expectedVersion: Long)

@Serializable
data class CachedRecord(
    val table: String, val id: String, val payload: JsonObject,
    val version: Long = 0, val pending: PendingWrite? = null,
    val conflict: JsonObject? = null
) {
    val key get() = "$table/$id"
    val deleted get() = payload["deleted_at"]?.let { it != JsonNull } == true
}

@Serializable
data class AccountCache(
    val records: Map<String, CachedRecord> = emptyMap(),
    val initialized: Boolean = false,
    val localDataImported: Boolean = false,
    val lastSyncedAt: String? = null
)

@Serializable
data class BackendCache(
    val accounts: Map<String, AccountCache> = emptyMap(),
    val catalogUpdatedAt: String? = null,
    val weeks: List<WeekRecord> = emptyList(),
    val articles: List<ArticleRecord> = emptyList(),
    val emergencyContacts: List<EmergencyContactRecord> = emptyList()
)

@Serializable
data class AppointmentRecord(
    val id: String = UUID.randomUUID().toString(),
    val pregnancy_profile_id: String? = null,
    val title: String,
    val appointment_date: String,
    val appointment_time: String,
    val time_zone: String = "Africa/Lusaka",
    val facility_name: String = "",
    val provider_name: String = "",
    val notes: String = "",
    val status: String = "upcoming",
    val deleted_at: String? = null
) {
    fun validate() {
        UUID.fromString(id)
        pregnancy_profile_id?.let(UUID::fromString)
        require(title.trim().length in 1..200)
        LocalDate.parse(appointment_date)
        LocalTime.parse(appointment_time)
        require(time_zone == "Africa/Lusaka")
        require(status in listOf("upcoming", "completed", "cancelled"))
        require(facility_name.length <= 200 && provider_name.length <= 100 && notes.length <= 5000)
    }
}

@Serializable
data class JournalRecord(
    val id: String = UUID.randomUUID().toString(),
    val pregnancy_profile_id: String? = null,
    val entry_date: String,
    val category: String,
    val notes: String,
    val deleted_at: String? = null
) {
    fun validate() {
        UUID.fromString(id)
        pregnancy_profile_id?.let(UUID::fromString)
        LocalDate.parse(entry_date)
        require(category in journalCategories)
        require(notes.trim().length in 1..5000)
    }
}
val journalCategories = listOf("blood_pressure", "weight", "symptoms", "labs", "notes")

@Serializable
data class SettingsRecord(
    val preferred_language: String = "en",
    val notification_preferences: JsonObject = buildJsonObject {
        put("appointments", false); put("journal", false)
    },
    val deleted_at: String? = null
)

@Serializable
data class WeekRecord(
    val id: String,
    val week_number: Int,
    val title: String,
    val baby_development: String,
    val maternal_changes: String = "",
    val educational_guidance: String = "",
    val fruit_comparison: String? = null,
    val length_cm: Double? = null,
    val weight_grams: Double? = null,
    val milestones: List<String> = emptyList()
)
@Serializable
data class ArticleRecord(val id: String, val title: String, val category: String, val content: String)
@Serializable
data class EmergencyContactRecord(
    val id: String, val name: String, val phone_number: String,
    val category: String, val location: String
)

fun JsonObject.text(key: String, fallback: String = "") =
    (get(key) as? JsonPrimitive)?.contentOrNull ?: fallback
fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
fun remoteRecord(table: PrivateTable, row: JsonObject): CachedRecord {
    val id = row.text(if (table == PrivateTable.SETTINGS) "user_id" else "id")
    require(id.isNotBlank() && row.long("version") > 0) { "Invalid server record" }
    return CachedRecord(table.wire, id, row, row.long("version"))
}

fun pregnancyPayload(profile: PregnancyProfile): JsonObject {
    val progress = PregnancyCalculator.calculateProgress(profile, LocalDate.now())
        ?: error("Review the pregnancy dates before saving.")
    val lmp = profile.lmpDateIso?.let(PregnancyCalculator::parseIsoDate)
    val edd = if (profile.datingMethod == DatingMethod.LMP) {
        lmp?.let(PregnancyCalculator::calculateEddFromLmp)
    } else profile.eddDateIso?.let(PregnancyCalculator::parseIsoDate)
    require(edd != null && progress.progressPercent.isFinite())
    require(profile.displayName.trim().length in 1..100)
    require(profile.healthcareFacility.length <= 200 && profile.emergencyContactName.length <= 100)
    require(profile.emergencyContactPhone.length <= 40 && profile.pregnancyNotes.length <= 5000)
    return buildJsonObject {
        put("display_name", profile.displayName)
        put("last_menstrual_period", lmp?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        put("estimated_due_date", edd.toString())
        put("due_date_source", if (profile.datingMethod == DatingMethod.LMP) "lmp" else "clinician")
        put("is_clinician_confirmed", profile.isClinicianConfirmed)
        put("pregnancy_status", profile.pregnancyStatus.lowercase().also {
            require(it in listOf("active", "completed", "archived"))
        })
        put("healthcare_facility", profile.healthcareFacility)
        put("emergency_contact_name", profile.emergencyContactName)
        put("emergency_contact_phone", profile.emergencyContactPhone)
        put("pregnancy_notes", profile.pregnancyNotes)
        put("deleted_at", JsonNull)
    }
}
fun pregnancyFromPayload(row: JsonObject) = PregnancyProfile(
    displayName = row.text("display_name"),
    datingMethod = if (row.text("due_date_source") == "clinician") DatingMethod.CLINICIAN_EDD else DatingMethod.LMP,
    lmpDateIso = row.text("last_menstrual_period").ifBlank { null },
    eddDateIso = row.text("estimated_due_date").ifBlank { null },
    isClinicianConfirmed = (row["is_clinician_confirmed"] as? JsonPrimitive)?.booleanOrNull ?: false,
    pregnancyStatus = row.text("pregnancy_status", "active").replaceFirstChar { it.uppercase() },
    healthcareFacility = row.text("healthcare_facility"),
    emergencyContactName = row.text("emergency_contact_name"),
    emergencyContactPhone = row.text("emergency_contact_phone"),
    pregnancyNotes = row.text("pregnancy_notes")
)
