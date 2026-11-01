package com.monicacare.app.stability

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.monicacare.app.core.data.DataStorePregnancyProfileRepository
import com.monicacare.app.core.data.UserPreferencesRepository
import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

/** Every test uses a temporary file; production DataStore names and contexts are never used. */
class PersistenceRegressionTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun profileSaveUpdateAndReopenPreserveAllStoredFields() = runBlocking {
        val file = File(temp.root, "isolated-profile.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val repo = DataStorePregnancyProfileRepository(store)
        assertNull(repo.pregnancyProfile.first())
        val profile = PregnancyProfile(displayName = "QA only", datingMethod = DatingMethod.CLINICIAN_EDD,
            lmpDateIso = null, eddDateIso = "2025-10-15", isClinicianConfirmed = true,
            healthcareFacility = "Test facility", emergencyContactName = "QA contact",
            emergencyContactPhone = "000", pregnancyNotes = "Test notes")
        try {
            repo.savePregnancyProfile(profile)
            assertEquals(profile, repo.pregnancyProfile.first())
            repo.savePregnancyProfile(profile.copy(displayName = "Updated QA", pregnancyNotes = "Updated notes"))
            assertEquals("Updated QA", repo.pregnancyProfile.first()!!.displayName)
        } finally { job.cancelAndJoin() }
        val reopenedJob = SupervisorJob()
        val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + reopenedJob)) { file }
        try {
            assertEquals(profile.copy(displayName = "Updated QA", pregnancyNotes = "Updated notes"),
                DataStorePregnancyProfileRepository(reopened).pregnancyProfile.first())
        } finally { reopenedJob.cancelAndJoin() }
    }

    @Test fun legacyKeysRemainReadableAndUnrelatedKeysSurviveUpdate() = runBlocking {
        val file = File(temp.root, "legacy.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            store.edit {
                it[booleanPreferencesKey("has_profile")] = true
                it[stringPreferencesKey("display_name")] = "Legacy QA"
                it[stringPreferencesKey("dating_method")] = "LMP"
                it[stringPreferencesKey("lmp_date_iso")] = "2025-01-01"
                it[stringPreferencesKey("future_unrelated_key")] = "Preserve"
            }
            val repo = DataStorePregnancyProfileRepository(store)
            val profile = repo.pregnancyProfile.first()!!
            assertEquals("Legacy QA", profile.displayName)
            assertEquals("2025-01-01", profile.lmpDateIso)
            repo.savePregnancyProfile(profile.copy(eddDateIso = null))
            assertEquals("Preserve", store.data.first()[stringPreferencesKey("future_unrelated_key")])
            assertNull(repo.pregnancyProfile.first()!!.eddDateIso)
        } finally { job.cancelAndJoin() }
    }

    @Test fun onboardingCompletionSurvivesReopen() = runBlocking {
        val file = File(temp.root, "welcome.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            val repo = UserPreferencesRepository(store)
            assertFalse(repo.isOnboardingCompleted.first())
            repo.setOnboardingCompleted(true)
            assertTrue(repo.isOnboardingCompleted.first())
        } finally { job.cancelAndJoin() }
        val reopenedJob = SupervisorJob()
        val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + reopenedJob)) { file }
        try { assertTrue(UserPreferencesRepository(reopened).isOnboardingCompleted.first()) }
        finally { reopenedJob.cancelAndJoin() }
    }

    @Test fun corruptStoragePropagatesFailureInsteadOfReturningEmptyProfile() = runBlocking {
        val file = File(temp.root, "broken.preferences_pb")
        file.writeBytes(byteArrayOf(0, 1, 2, 3))
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            var failed = false
            try { DataStorePregnancyProfileRepository(store).pregnancyProfile.first() }
            catch (_: java.io.IOException) { failed = true }
            assertTrue("Read failure must reach the ViewModel", failed)
            assertArrayEquals(byteArrayOf(0, 1, 2, 3), file.readBytes())
        } finally { job.cancelAndJoin() }
    }
}
