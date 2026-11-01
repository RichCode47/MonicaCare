package com.monicacare.app.core.backend

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.monicacare.app.BuildConfig
import com.monicacare.app.core.data.DataStorePregnancyProfileRepository
import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.model.BabyDevelopment
import com.monicacare.app.core.model.BabyDevelopmentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URI
import java.util.UUID

data class BackendConfig(val url: String, val publishableKey: String) {
    val isConfigured get() = url.isNotBlank() && publishableKey.isNotBlank()
    fun validate() {
        require(publishableKey.startsWith("sb_publishable_")) { "Use a public Supabase publishable key." }
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) {
            "Supabase requires an HTTPS project URL."
        }
    }
}

class BackendContainer private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val config = BackendConfig(BuildConfig.SUPABASE_URL.trim(), BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim())
    private val cipher = AndroidTextCipher()
    private val preferences = PreferenceDataStoreFactory.create(scope = scope) {
        File(context.noBackupFilesDir, "monicacare_backend.preferences_pb")
    }
    val store: BackendStore = EncryptedBackendStore(preferences, cipher)
    val configurationError = runCatching { if (config.isConfigured) config.validate() }.exceptionOrNull()
    private val client: SupabaseClient? = if (!config.isConfigured || configurationError != null) null else
        createSupabaseClient(config.url, config.publishableKey) {
            httpEngine = OkHttp.create()
            configurePrivateTransport(config.publishableKey)
            install(Auth) {
                autoLoadFromStorage = false
                sessionManager = AndroidSessionManager(context, cipher)
                enableLifecycleCallbacks = true
            }
            install(Postgrest)
            install(Storage)
        }
    val auth: AccountAuth = client?.let { SupabaseAccountAuth(it, scope) } ?: OfflineAccountAuth()
    val sync = SyncEngine(auth, store, client?.let(::SupabaseRemote))
    val records = BackendRecordsRepository(auth, store, sync)
    val legacyPregnancy: PregnancyProfileRepository = DataStorePregnancyProfileRepository(context)
    private val cache = MutableStateFlow<BackendCache?>(null)
    val cached = cache.asStateFlow()
    private val readFailure = MutableStateFlow(false)
    val storageReadFailed = readFailure.asStateFlow()
    private var cacheJob: Job? = null
    init { retryCacheRead() }
    fun retryCacheRead() {
        cacheJob?.cancel()
        cacheJob = scope.launch {
            store.data.catch { readFailure.value = true }.collect { cache.value = it; readFailure.value = false }
        }
    }
    fun pregnancyRepository(owner: String) = AccountPregnancyRepository(owner, records, legacyPregnancy)

    fun developmentForWeek(week: Int): BabyDevelopment? {
        val row = cache.value?.weeks?.firstOrNull { it.week_number == week }
        if (row != null && row.fruit_comparison != null && row.length_cm != null && row.weight_grams != null) {
            return BabyDevelopment(week, row.fruit_comparison, row.length_cm, row.weight_grams,
                row.baby_development, row.milestones)
        }
        return if (row == null) BabyDevelopmentRepository.getForWeek(week) else null
    }

    /** Caller chooses a file explicitly; no public URL or overwrite is used. */
    suspend fun uploadPrivateDocument(bytes: ByteArray, mimeType: String): String {
        val extension = mapOf("application/pdf" to "pdf", "image/jpeg" to "jpg", "image/png" to "png")[mimeType]
            ?: error("Unsupported document type.")
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024)
        return auth.sessionMutex.withLock {
            val owner = auth.session.value.userId ?: error("Sign in before uploading.")
            val path = "$owner/${UUID.randomUUID()}.$extension"
            (client ?: error("Supabase is not configured.")).storage.from("monicacare-private").upload(path, bytes) {
                upsert = false
                contentType = io.ktor.http.ContentType.parse(mimeType)
            }
            path
        }
    }
    companion object {
        @Volatile private var instance: BackendContainer? = null
        fun get(context: Context): BackendContainer = instance ?: synchronized(this) {
            instance ?: BackendContainer(context.applicationContext).also { instance = it }
        }
    }
}
