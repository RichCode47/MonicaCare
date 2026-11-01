package com.monicacare.app.feature.account

import com.monicacare.app.core.backend.text
import kotlinx.serialization.json.JsonObject

fun recordTitle(table: String) = when (table) {
    "pregnancy_profiles" -> "Pregnancy profile"
    "health_journal" -> "Journal entry"
    "appointments" -> "Appointment"
    "profiles" -> "Account name"
    else -> "Preferences"
}
fun recordSummary(row: JsonObject): String {
    val labels = listOf(
        "display_name" to "Name", "title" to "Title", "appointment_date" to "Date",
        "appointment_time" to "Time", "entry_date" to "Entry date", "notes" to "Notes",
        "last_menstrual_period" to "Last period", "estimated_due_date" to "Due date",
        "pregnancy_status" to "Pregnancy status", "pregnancy_notes" to "Pregnancy notes",
        "facility_name" to "Facility", "healthcare_facility" to "Facility",
        "provider_name" to "Provider", "emergency_contact_name" to "Emergency contact",
        "emergency_contact_phone" to "Emergency phone", "preferred_language" to "Language",
        "status" to "Status"
    )
    val lines = labels.mapNotNull { (key, label) -> row.text(key).takeIf(String::isNotBlank)?.let { "$label: $it" } }
    return (lines + if (row.text("deleted_at").isNotBlank()) listOf("This copy is marked as removed.") else emptyList()).joinToString("\n")
        .ifBlank { "No displayable fields." }
}
