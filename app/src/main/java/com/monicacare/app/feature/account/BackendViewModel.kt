package com.monicacare.app.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.monicacare.app.core.backend.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class BackendUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val records: List<CachedRecord> = emptyList(),
    val weeks: List<WeekRecord> = emptyList(),
    val articles: List<ArticleRecord> = emptyList(),
    val contacts: List<EmergencyContactRecord> = emptyList(),
    val lastSyncedAt: String? = null,
    val localDataImported: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
class BackendViewModel(val backend: BackendContainer, val owner: String) : ViewModel() {
    private val state = MutableStateFlow(BackendUiState())
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    init { load() }
    fun load() {
        if (backend.storageReadFailed.value) backend.retryCacheRead()
        loadJob?.cancel()
        state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                if (owner != LOCAL_OWNER) backend.sync.ensureLoaded(owner)
                backend.store.data.collect { cache ->
                    val account = cache.accounts[owner]
                    state.update { it.copy(isLoading = false,
                        records = account?.records?.values?.toList().orEmpty(),
                        weeks = cache.weeks, articles = cache.articles, contacts = cache.emergencyContacts,
                        lastSyncedAt = account?.lastSyncedAt, localDataImported = account?.localDataImported ?: false) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                state.update { it.copy(isLoading = false, error = "Unable to load account records. Retry before making changes.") }
            }
        }
    }
    private fun action(success: String? = null, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (state.value.isBusy) return
        state.update { it.copy(isBusy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                block()
                state.update { it.copy(isBusy = false, message = success) }
                onSuccess()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = when (e) {
                    is java.time.DateTimeException, is IllegalArgumentException -> "Please review the fields and dates before trying again."
                    is IllegalStateException -> e.message ?: "This action is unavailable."
                    else -> "The action could not finish. Saved local changes remain on this device; please retry."
                }
                state.update { it.copy(isBusy = false, error = message) }
            }
        }
    }
    fun signIn(email: String, password: String) = action {
        backend.auth.signIn(email, password)
    }
    fun signUp(email: String, password: String) = action(
        success = "Check your email to confirm registration, then sign in. If an account already exists, use Sign in."
    ) { backend.auth.signUp(email, password) }
    fun signOut() = action { backend.auth.signOut() }
    fun synchronize() = action(success = "Sync complete. Review any conflicts below.") {
        backend.sync.synchronize(owner)
        load()
    }
    fun refreshGuidance() = action(success = "Published guidance and directory refreshed.") {
        backend.sync.refreshCatalog()
    }
    fun importLocal() = action(success = "Device records copied to this account's local cache. Use Sync to upload them.") {
        backend.records.importLocalData(owner, backend.legacyPregnancy)
    }
    fun resolve(key: String, keepLocal: Boolean) = action {
        backend.records.resolve(owner, key, keepLocal)
    }
    fun saveAppointment(record: AppointmentRecord, onSuccess: () -> Unit) =
        action("Appointment saved on this device.", onSuccess) { backend.records.saveAppointment(owner, record) }
    fun saveJournal(record: JournalRecord, onSuccess: () -> Unit) =
        action("Journal entry saved on this device.", onSuccess) { backend.records.saveJournal(owner, record) }
    fun delete(record: CachedRecord) = action("Record removed locally. Sync sends the deletion marker.") {
        backend.records.delete(owner, record)
    }
    fun saveSettings(record: SettingsRecord, onSuccess: () -> Unit) =
        action("Preferences saved on this device.", onSuccess) { backend.records.saveSettings(owner, record) }
    fun saveDisplayName(name: String) = action("Account name saved on this device.") {
        require(name.trim().length in 1..100)
        backend.records.save(owner, PrivateTable.PROFILE, owner, buildJsonObject {
            put("display_name", name.trim()); put("deleted_at", JsonNull)
        })
    }
    class Factory(private val backend: BackendContainer, private val owner: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BackendViewModel(backend, owner) as T
    }
}
