package com.monicacare.app.core.backend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface BackendStore {
    val data: Flow<BackendCache>
    suspend fun snapshot(): BackendCache = data.first()
    suspend fun update(transform: (BackendCache) -> BackendCache)
}
interface TextCipher {
    fun seal(value: String, purpose: String): String
    fun open(value: String, purpose: String): String
}
class EncryptedBackendStore(
    private val preferences: DataStore<Preferences>,
    private val cipher: TextCipher
) : BackendStore {
    private val key = stringPreferencesKey("backend_cache_v1")
    override val data = preferences.data.map { prefs ->
        prefs[key]?.let { BackendJson.decodeFromString<BackendCache>(cipher.open(it, "cache")) } ?: BackendCache()
    }.flowOn(Dispatchers.IO).distinctUntilChanged()
    override suspend fun update(transform: (BackendCache) -> BackendCache) {
        withContext(Dispatchers.IO) {
        preferences.edit { prefs ->
            val previous = prefs[key]?.let {
                BackendJson.decodeFromString<BackendCache>(cipher.open(it, "cache"))
            } ?: BackendCache()
            prefs[key] = cipher.seal(BackendJson.encodeToString(transform(previous)), "cache")
        }
        }
    }
}
/** Also useful for deterministic tests and isolated UI previews. */
class MemoryBackendStore(initial: BackendCache = BackendCache()) : BackendStore {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()
    override val data: StateFlow<BackendCache> = state.asStateFlow()
    override suspend fun update(transform: (BackendCache) -> BackendCache) {
        mutex.withLock { state.value = transform(state.value) }
    }
}
