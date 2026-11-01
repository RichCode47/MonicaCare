package com.monicacare.app.core.backend

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AccountSession(
    val initialized: Boolean = false,
    val userId: String? = null,
    val email: String? = null,
    val initializationFailed: Boolean = false
)
interface AccountAuth {
    val session: StateFlow<AccountSession>
    /** Serializes account changes with network synchronization. */
    val sessionMutex: Mutex
    fun retryInitialization() = Unit
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun signOut()
}
class SupabaseAccountAuth(private val client: SupabaseClient, scope: CoroutineScope) : AccountAuth {
    private val state = MutableStateFlow(AccountSession())
    override val session = state.asStateFlow()
    override val sessionMutex = Mutex()
    private val authScope = scope
    private var initializationJob: Job? = null
    init { retryInitialization() }
    override fun retryInitialization() {
        if (initializationJob?.isActive == true) return
        state.value = AccountSession()
        initializationJob = authScope.launch {
            try {
                // SDK auto-load is disabled so storage failures are visible and retryable.
                val saved = client.auth.sessionManager.loadSession()
                if (saved != null) {
                    require(!saved.user?.id.isNullOrBlank()) { "Stored session has no owner." }
                    // Establish the cached owner before an expired token refresh attempts the network.
                    client.auth.importSession(saved, autoRefresh = false)
                    state.value = AccountSession(true, saved.user?.id, saved.user?.email)
                    client.auth.startAutoRefreshForCurrentSession()
                }
                client.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> state.value = AccountSession(
                            true, status.session.user?.id, status.session.user?.email
                        )
                        is SessionStatus.NotAuthenticated -> state.value = AccountSession(initialized = true)
                        SessionStatus.Initializing -> Unit
                        else -> Unit // Offline refresh failure retains this account's encrypted cache.
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state.value = AccountSession(initializationFailed = true) }
        }
    }
    override suspend fun signIn(email: String, password: String) = sessionMutex.withLock {
        require(email.trim().contains('@') && password.isNotEmpty())
        client.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
    }
    override suspend fun signUp(email: String, password: String) = sessionMutex.withLock {
        require(email.trim().contains('@') && password.length >= 8)
        client.auth.signUpWith(Email) { this.email = email.trim(); this.password = password }
        Unit
    }
    override suspend fun signOut() = sessionMutex.withLock {
        try {
            client.auth.signOut()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            // An offline device can still remove its local credentials. Remote revocation is not claimed.
        } finally {
            withContext(NonCancellable) { client.auth.clearSession() }
        }
    }
}
class OfflineAccountAuth : AccountAuth {
    override val session = MutableStateFlow(AccountSession(initialized = true)).asStateFlow()
    override val sessionMutex = Mutex()
    override suspend fun signIn(email: String, password: String): Unit = error("Supabase is not configured.")
    override suspend fun signUp(email: String, password: String): Unit = error("Supabase is not configured.")
    override suspend fun signOut() = Unit
}
