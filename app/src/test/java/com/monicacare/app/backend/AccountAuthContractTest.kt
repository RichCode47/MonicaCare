@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.monicacare.app.backend

import com.monicacare.app.core.backend.*
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

private const val OWNER = "11111111-1111-4111-8111-111111111111"
private const val EMAIL = "qa@example.invalid"
private const val USER_JSON = """{"id":"$OWNER","aud":"authenticated","email":"$EMAIL","role":"authenticated"}"""
private const val SESSION_JSON = """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":3600,"token_type":"bearer","user":$USER_JSON}"""

private class TestSessionManager : SessionManager {
    var saved: UserSession? = null
    var readFailure = false
    var deletes = 0
    override suspend fun loadSession(): UserSession? {
        if (readFailure) throw IOException("Isolated storage failure")
        return saved
    }
    override suspend fun saveSession(session: UserSession) { saved = session }
    override suspend fun deleteSession() { deletes++; saved = null }
}

/** Exercises the actual SDK Auth plugin and the app adapter without real credentials or HTTP. */
class AccountAuthContractTest {
    private fun TestScope.client(manager: SessionManager, engine: MockEngine) =
        createSupabaseClient("https://example.supabase.co","sb_publishable_synthetic") {
            httpEngine = engine
            coroutineDispatcher = StandardTestDispatcher(testScheduler)
            configurePrivateTransport("sb_publishable_synthetic")
            install(Auth) {
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
                sessionManager = manager
                codeVerifierCache = MemoryCodeVerifierCache()
            }
        }

    @Test fun freshInstallFinishesLoadingWithoutNetworkOrASession() = runTest {
        var requests = 0
        val manager = TestSessionManager()
        val client = client(manager,MockEngine { requests++; error("Unexpected HTTP request") })
        try {
            val auth = SupabaseAccountAuth(client,backgroundScope)
            assertNull(auth.session.first { it.initialized }.userId)
            assertEquals(0,requests)
            assertEquals(LogLevel.NONE,io.github.jan.supabase.SupabaseClient.LOGGER.level)
            assertEquals(LogLevel.NONE,Auth.logger.level)
            assertEquals(0,manager.deletes)
        } finally { client.close() }
    }

    @Test fun signInPersistsTheSessionAndOfflineSignOutClearsOnlyLocalCredentials() = runTest {
        val manager = TestSessionManager()
        var requests = 0
        val client = client(manager,MockEngine { request ->
            requests++
            assertEquals("sb_publishable_synthetic",request.headers["apikey"])
            if (request.url.encodedPath.endsWith("/token")) {
                assertEquals("password",request.url.parameters["grant_type"])
                assertNull(request.headers[HttpHeaders.Authorization])
                respond(SESSION_JSON,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
            } else {
                assertEquals("Bearer synthetic-access",request.headers[HttpHeaders.Authorization])
                throw IOException("Device offline during sign-out")
            }
        })
        try {
            val auth = SupabaseAccountAuth(client,backgroundScope)
            auth.session.first { it.initialized }
            auth.signIn("  $EMAIL  ","synthetic-password")
            assertEquals(OWNER,auth.session.first { it.userId != null }.userId)
            assertEquals(OWNER,manager.saved?.user?.id)
            auth.signOut()
            assertNull(auth.session.first { it.initialized && it.userId == null }.userId)
            assertNull(manager.saved)
            assertTrue(requests >= 2)
            assertTrue(manager.deletes > 0)
        } finally { client.close() }
    }

    @Test fun emailConfirmationSignupDoesNotPretendThatTheUserIsSignedIn() = runTest {
        val manager = TestSessionManager()
        val client = client(manager,MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/signup"))
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(USER_JSON,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        })
        try {
            val auth = SupabaseAccountAuth(client,backgroundScope)
            auth.session.first { it.initialized }
            auth.signUp(EMAIL,"synthetic-password")
            runCurrent()
            assertNull(auth.session.value.userId)
            assertNull(manager.saved)
        } finally { client.close() }
    }

    @Test fun storageReadFailureIsRetryableAndNeverDeletesTheSavedSession() = runTest {
        val manager = TestSessionManager().apply { readFailure = true }
        val client = client(manager,MockEngine { error("Unexpected HTTP request") })
        try {
            val auth = SupabaseAccountAuth(client,backgroundScope)
            assertFalse(auth.session.first { it.initializationFailed }.initialized)
            runCurrent()
            assertEquals(0,manager.deletes)
            manager.readFailure = false
            auth.retryInitialization()
            assertNull(auth.session.first { it.initialized }.userId)
            assertEquals(0,manager.deletes)
        } finally { client.close() }
    }

    @Test fun expiredStoredSessionRetainsItsAccountCacheWhenRefreshIsOffline() = runTest {
        val manager = TestSessionManager().apply {
            saved = UserSession(accessToken="synthetic-expired",refreshToken="synthetic-refresh",
                expiresIn=3600,tokenType="bearer",user=UserInfo(aud="authenticated",id=OWNER,email=EMAIL),
                expiresAt=kotlin.time.Instant.parse("2026-01-01T00:00:00Z"))
        }
        val client = client(manager,MockEngine { throw IOException("Offline token refresh") })
        try {
            val auth = SupabaseAccountAuth(client,backgroundScope)
            assertEquals(OWNER,auth.session.first { it.initialized }.userId)
            runCurrent()
            assertEquals(OWNER,auth.session.value.userId)
            assertNotNull(manager.saved)
            assertEquals(0,manager.deletes)
        } finally { client.close() }
    }
}
