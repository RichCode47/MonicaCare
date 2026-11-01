package com.monicacare.app.backend

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monicacare.app.BuildConfig
import com.monicacare.app.core.backend.*
import com.monicacare.app.core.designsystem.theme.MonicaCareTheme
import com.monicacare.app.feature.account.*
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** QA variant forces cloud configuration off unless explicitly opted in. No production data is used. */
class BackendDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun records(journal: Boolean): BackendViewModel {
        lateinit var vm: BackendViewModel
        compose.setContent {
            val backend = BackendContainer.get(LocalContext.current)
            vm = viewModel(factory = BackendViewModel.Factory(backend, LOCAL_OWNER))
            MonicaCareTheme { ManagedRecordsScreen(vm, journal) }
        }
        compose.waitUntil(15000) { !vm.uiState.value.isLoading }
        return vm
    }

    @Test fun appNavigationConnectsOfflineRecordsProfileAndAccount() {
        compose.setContent { com.monicacare.app.MonicaCareApp() }
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Appointments").performClick()
        compose.onNodeWithText("Antenatal Appointments").assertExists()
        compose.onNodeWithText("Device-only records").assertExists()
        compose.onNodeWithText("Health").performClick()
        compose.onNodeWithText("Maternal Health Journal").assertExists()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Account & Sync").performScrollTo().performClick()
        compose.onNodeWithText("Cloud accounts are unavailable in this build. You can continue using device storage.").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Journey").performClick()
        compose.onNodeWithText("Pregnancy Journey").assertExists()
    }

    @Test fun appointmentValidationAndOfflineSaveWorkWithoutCloudConfiguration() {
        assertEquals("",BuildConfig.SUPABASE_URL)
        assertEquals("",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        val vm = records(journal = false)
        compose.onNodeWithText("+ Schedule New ANC Visit").performClick()
        compose.onNodeWithText("Visit title").performTextInput("QA isolated visit")
        compose.onNodeWithText("Date (YYYY-MM-DD)").performTextReplacement("not-a-date")
        compose.onNodeWithText("Save on device").performClick()
        compose.onAllNodesWithText("Please review the fields and dates before trying again.").onLast().assertExists()
        compose.onNodeWithText("Date (YYYY-MM-DD)").performScrollTo().performTextReplacement("2026-11-15")
        compose.onNodeWithText("Save on device").performClick()
        compose.waitUntil(15000) {
            vm.uiState.value.records.any { it.table=="appointments" && it.payload.text("title")=="QA isolated visit" }
        }
        compose.onNodeWithText("QA isolated visit").assertExists()
        compose.runOnIdle {
            val row = vm.uiState.value.records.first { it.table=="appointments" }
            assertNotNull(row.pending)
            assertEquals(0,row.version)
        }
    }

    @Test fun journalSaveAndRemoveAreDeviceOnlyAndRecoverable() {
        val vm = records(journal = true)
        compose.onNodeWithText("+ Record Today's Health Log").performClick()
        compose.onNodeWithText("Your notes").performScrollTo().performTextInput("QA isolated journal note")
        compose.onNodeWithText("Save on device").performClick()
        compose.waitUntil(15000) { vm.uiState.value.records.any {
            it.table=="health_journal" && it.payload.text("notes")=="QA isolated journal note"
        } }
        compose.onNodeWithText("QA isolated journal note").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove").performScrollTo().performClick()
        compose.onNodeWithText("Remove record?").assertExists()
        compose.onAllNodesWithText("Remove").onLast().performClick()
        compose.waitUntil(15000) { vm.uiState.value.records.any { it.table=="health_journal" && it.deleted } }
        compose.runOnIdle {
            val removed = vm.uiState.value.records.first { it.table=="health_journal" }
            assertTrue(removed.deleted)
            assertNotNull(removed.pending)
        }
    }

    @Test fun accountScreenKeepsDeviceModeUsableAndStoresPreferencesOffline() {
        lateinit var vm: BackendViewModel
        compose.setContent {
            val backend = BackendContainer.get(LocalContext.current)
            vm = viewModel(factory = BackendViewModel.Factory(backend,LOCAL_OWNER))
            MonicaCareTheme { AccountScreen(vm,{}) }
        }
        compose.waitUntil(15000) { !vm.uiState.value.isLoading }
        compose.onNodeWithText("Cloud accounts are unavailable in this build. You can continue using device storage.").assertExists()
        compose.onNodeWithText("Language & reminder preferences").performScrollTo().performClick()
        compose.onNodeWithText("Save preferences").performClick()
        compose.waitUntil(15000) { vm.uiState.value.records.any { it.table=="user_settings" } }
        compose.runOnIdle {
            assertEquals("en",vm.uiState.value.records.first { it.table=="user_settings" }.payload.text("preferred_language"))
        }
    }

    @Test fun androidKeystoreCipherAuthenticatesContentAndPurpose() {
        val cipher = AndroidTextCipher()
        val clear = "Synthetic QA health note"
        val encrypted = cipher.seal(clear,"cache")
        assertFalse(encrypted.contains(clear))
        assertEquals(clear,AndroidTextCipher().open(encrypted,"cache"))
        var failed = false
        try { cipher.open(encrypted,"session") } catch (_: Exception) { failed = true }
        assertTrue("Ciphertext cannot be moved between cache and session fields",failed)
        val bytes = android.util.Base64.decode(encrypted,android.util.Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        failed = false
        try { cipher.open(android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP),"cache") }
        catch (_: Exception) { failed = true }
        assertTrue("Tampered ciphertext must be rejected",failed)
    }

    @Test fun encryptedAccountCacheSurvivesStoreReopen() = runBlocking {
        val file = File(compose.activity.noBackupFilesDir,"qa-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO+job)) { file }
        val row = CachedRecord("health_journal",UUID.randomUUID().toString(),
            buildJsonObject { put("notes","Synthetic persisted QA note") },pending = PendingWrite(UUID.randomUUID().toString(),0))
        val initial = BackendCache(accounts = mapOf("qa-account" to AccountCache(mapOf(row.key to row),true)))
        try {
            val store = EncryptedBackendStore(preferences,AndroidTextCipher())
            store.update { initial }
            assertEquals(initial,store.snapshot())
            assertFalse(preferences.data.first().asMap().values.any { it.toString().contains("Synthetic persisted QA note") })
        } finally { job.cancelAndJoin() }
        val reopenedJob = SupervisorJob()
        try {
            val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO+reopenedJob)) { file }
            assertEquals(initial,EncryptedBackendStore(reopened,AndroidTextCipher()).snapshot())
        } finally {
            reopenedJob.cancelAndJoin()
            file.delete() // Only the uniquely named QA test file.
        }
    }

    @OptIn(kotlin.time.ExperimentalTime::class)
    @Test fun encryptedSessionRoundTripPreservesDefaultFieldsAndDeletesLocally() = runBlocking {
        val manager = AndroidSessionManager(compose.activity,AndroidTextCipher())
        val session = UserSession(accessToken="qa-not-a-real-token",refreshToken="qa-not-a-real-refresh",
            expiresIn=3600,tokenType="bearer")
        manager.saveSession(session)
        assertEquals(session,manager.loadSession())
        manager.deleteSession()
        assertNull(manager.loadSession())
    }
}
