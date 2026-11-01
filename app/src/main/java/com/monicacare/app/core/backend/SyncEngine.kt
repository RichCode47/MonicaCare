package com.monicacare.app.core.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

data class SyncStatus(val isRunning: Boolean = false, val message: String? = null)
class SyncEngine(
    private val auth: AccountAuth,
    private val store: BackendStore,
    private val remote: BackendRemote?,
    private val now: () -> String = { Instant.now().toString() }
) {
    private val mutex = Mutex()
    private val status = MutableStateFlow(SyncStatus())
    val state = status.asStateFlow()

    suspend fun ensureLoaded(userId: String) {
        if (store.snapshot().accounts[userId]?.initialized != true) synchronize(userId, includeCatalog = false, initialLoadOnly = true)
    }

    suspend fun synchronize(expectedUser: String? = auth.session.value.userId, includeCatalog: Boolean = true, initialLoadOnly: Boolean = false) {
        val userId = expectedUser ?: error("Sign in before syncing.")
        val api = remote ?: error("Supabase is not configured.")
        mutex.withLock initialSync@ {
            if (initialLoadOnly && store.snapshot().accounts[userId]?.initialized == true) return@initialSync
            auth.sessionMutex.withLock {
                check(auth.session.value.userId == userId) { "Account changed; retry from the current account." }
                status.value = SyncStatus(isRunning = true)
                try {
                    // Push first; pending records are never overwritten by the subsequent pull.
                    for (table in PrivateTable.entries) {
                        val pending = store.snapshot().accounts[userId]?.records?.values.orEmpty()
                            .filter { it.table == table.wire && it.pending != null && it.conflict == null }
                        for (record in pending) {
                            check(auth.session.value.userId == userId)
                            try {
                                val result = api.write(table, record.id, record.pending!!, record.payload)
                                require(result.text(if (table == PrivateTable.PROFILE) "id" else "user_id") == userId) {
                                    "Invalid record owner in sync response."
                                }
                                val serverRecord = remoteRecord(table, result)
                                require(serverRecord.id == record.id && serverRecord.version > record.pending.expectedVersion) {
                                    "Invalid record identity or version in sync response."
                                }
                                acknowledge(userId, record, serverRecord)
                            } catch (_: SyncConflictException) {
                                val server = api.read(table).firstOrNull {
                                    it.text(if (table == PrivateTable.SETTINGS) "user_id" else "id") == record.id
                                } ?: error("A conflicting record is unavailable. Local changes remain saved.")
                                require(server.text(if (table == PrivateTable.PROFILE) "id" else "user_id") == userId)
                                store.update { cache ->
                                    cache.updateAccount(userId) { account ->
                                        val current = account.records[record.key] ?: return@updateAccount account
                                        account.copy(records = account.records + (record.key to current.copy(conflict = server)))
                                    }
                                }
                            }
                        }
                    }
                    for (table in PrivateTable.entries) {
                        val rows = api.read(table).map { row ->
                            require(row.text(if (table == PrivateTable.PROFILE) "id" else "user_id") == userId) {
                                "Unexpected owner in server response."
                            }
                            remoteRecord(table, row)
                        }
                        store.update { cache ->
                            cache.updateAccount(userId) { account ->
                                var records = account.records
                                for (server in rows) {
                                    val local = records[server.key]
                                    if (local?.pending == null) records = records + (server.key to server)
                                    else if (server.version > local.pending.expectedVersion &&
                                        server.payload.text("last_mutation_id") != local.pending.mutationId) {
                                        records = records + (server.key to local.copy(conflict = server.payload))
                                    }
                                }
                                account.copy(records = records)
                            }
                        }
                    }
                    if (includeCatalog) readCatalog(api, force = false)
                    store.update { it.updateAccount(userId) { a -> a.copy(initialized = true, lastSyncedAt = now()) } }
                    status.value = SyncStatus(message = "Sync complete. Any conflicts need your review.")
                } catch (e: CancellationException) {
                    status.value = SyncStatus()
                    throw e
                } catch (e: Exception) {
                    status.value = SyncStatus(message = "Sync could not finish. Your local changes are saved; check your connection and retry.")
                    throw e
                }
            }
        }
    }

    /** Public content can be refreshed without creating an account or uploading private records. */
    suspend fun refreshCatalog() {
        val api = remote ?: error("Cloud content is unavailable in this build.")
        mutex.withLock {
            auth.sessionMutex.withLock {
                status.value = SyncStatus(isRunning = true)
                try {
                    readCatalog(api, force = true)
                    status.value = SyncStatus(message = "Published guidance refreshed.")
                } catch (e: CancellationException) {
                    status.value = SyncStatus()
                    throw e
                } catch (e: Exception) {
                    status.value = SyncStatus(message = "Content refresh failed. Previously downloaded content is still available.")
                    throw e
                }
            }
        }
    }

    private suspend fun readCatalog(api: BackendRemote, force: Boolean) {
        val previous = store.snapshot().catalogUpdatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val current = Instant.parse(now())
        if (!force && previous != null && !current.isBefore(previous) &&
            java.time.Duration.between(previous, current).toHours() < 24) return
        val catalog = api.catalog()
        store.update { it.copy(catalogUpdatedAt = now(), weeks = catalog.weeks,
            articles = catalog.articles, emergencyContacts = catalog.contacts) }
    }

    private suspend fun acknowledge(userId: String, sent: CachedRecord, server: CachedRecord) {
        store.update { cache ->
            cache.updateAccount(userId) { account ->
                val current = account.records[sent.key] ?: return@updateAccount account
                val replacement = if (current.pending?.mutationId == sent.pending?.mutationId) server
                else current.copy(version = server.version,
                    pending = current.pending?.copy(expectedVersion = server.version))
                account.copy(records = account.records + (sent.key to replacement))
            }
        }
    }
}
fun BackendCache.updateAccount(id: String, transform: (AccountCache) -> AccountCache) =
    copy(accounts = accounts + (id to transform(accounts[id] ?: AccountCache())))
