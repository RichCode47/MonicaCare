package com.monicacare.app.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PregnancyProfileRepository {
    val pregnancyProfile: Flow<PregnancyProfile?>
    suspend fun savePregnancyProfile(profile: PregnancyProfile)
    suspend fun clearProfile()
}

val Context.pregnancyProfileDataStore: DataStore<Preferences> by preferencesDataStore(name = "monicacare_pregnancy_profile")

class DataStorePregnancyProfileRepository(private val dataStore: DataStore<Preferences>) : PregnancyProfileRepository {
    constructor(context: Context) : this(context.applicationContext.pregnancyProfileDataStore)

    companion object {
        private val KEY_HAS_PROFILE = booleanPreferencesKey("has_profile")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_DATING_METHOD = stringPreferencesKey("dating_method")
        private val KEY_LMP_DATE_ISO = stringPreferencesKey("lmp_date_iso")
        private val KEY_EDD_DATE_ISO = stringPreferencesKey("edd_date_iso")
        private val KEY_IS_CLINICIAN_CONFIRMED = booleanPreferencesKey("is_clinician_confirmed")
        private val KEY_HEALTHCARE_FACILITY = stringPreferencesKey("healthcare_facility")
        private val KEY_EMERGENCY_CONTACT_NAME = stringPreferencesKey("emergency_contact_name")
        private val KEY_EMERGENCY_CONTACT_PHONE = stringPreferencesKey("emergency_contact_phone")
        private val KEY_PREGNANCY_NOTES = stringPreferencesKey("pregnancy_notes")
    }

    override val pregnancyProfile: Flow<PregnancyProfile?> = dataStore.data
        .map { preferences ->
            val hasProfile = preferences[KEY_HAS_PROFILE] ?: false
            if (!hasProfile) return@map null

            val displayName = preferences[KEY_DISPLAY_NAME] ?: "Mama"
            val datingMethodStr = preferences[KEY_DATING_METHOD] ?: DatingMethod.LMP.name
            val datingMethod = try {
                DatingMethod.valueOf(datingMethodStr)
            } catch (_: Exception) {
                DatingMethod.LMP
            }
            val lmpDateIso = preferences[KEY_LMP_DATE_ISO]
            val eddDateIso = preferences[KEY_EDD_DATE_ISO]
            val isClinicianConfirmed = preferences[KEY_IS_CLINICIAN_CONFIRMED] ?: false
            val healthcareFacility = preferences[KEY_HEALTHCARE_FACILITY] ?: ""
            val emergencyContactName = preferences[KEY_EMERGENCY_CONTACT_NAME] ?: ""
            val emergencyContactPhone = preferences[KEY_EMERGENCY_CONTACT_PHONE] ?: ""
            val pregnancyNotes = preferences[KEY_PREGNANCY_NOTES] ?: ""

            PregnancyProfile(
                displayName = displayName,
                datingMethod = datingMethod,
                lmpDateIso = lmpDateIso,
                eddDateIso = eddDateIso,
                isClinicianConfirmed = isClinicianConfirmed,
                healthcareFacility = healthcareFacility,
                emergencyContactName = emergencyContactName,
                emergencyContactPhone = emergencyContactPhone,
                pregnancyNotes = pregnancyNotes
            )
        }

    override suspend fun savePregnancyProfile(profile: PregnancyProfile) {
        dataStore.edit { preferences ->
            preferences[KEY_HAS_PROFILE] = true
            preferences[KEY_DISPLAY_NAME] = profile.displayName
            preferences[KEY_DATING_METHOD] = profile.datingMethod.name
            if (profile.lmpDateIso != null) {
                preferences[KEY_LMP_DATE_ISO] = profile.lmpDateIso
            } else {
                preferences.remove(KEY_LMP_DATE_ISO)
            }
            if (profile.eddDateIso != null) {
                preferences[KEY_EDD_DATE_ISO] = profile.eddDateIso
            } else {
                preferences.remove(KEY_EDD_DATE_ISO)
            }
            preferences[KEY_IS_CLINICIAN_CONFIRMED] = profile.isClinicianConfirmed
            preferences[KEY_HEALTHCARE_FACILITY] = profile.healthcareFacility
            preferences[KEY_EMERGENCY_CONTACT_NAME] = profile.emergencyContactName
            preferences[KEY_EMERGENCY_CONTACT_PHONE] = profile.emergencyContactPhone
            preferences[KEY_PREGNANCY_NOTES] = profile.pregnancyNotes
        }
    }

    override suspend fun clearProfile() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
