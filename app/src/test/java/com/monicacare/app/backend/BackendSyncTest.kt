package com.monicacare.app.backend

import com.monicacare.app.core.backend.*
import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.model.PregnancyProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

private const val A = "11111111-1111-4111-8111-111111111111"
private const val B = "22222222-2222-4222-8222-222222222222"
private const val ID = "33333333-3333-4333-8333-333333333333"

class FakeAccountAuth(owner: String = A) : AccountAuth {
    val value = MutableStateFlow(AccountSession(true, owner, "qa@example.invalid"))
    override val session = value.asStateFlow()
    override val sessionMutex = Mutex()
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String) = Unit
    override suspend fun signOut() { value.value = AccountSession(true) }
}
class FakeRemote(private val owner: String = A) : BackendRemote {
    val rows = mutableMapOf<String, JsonObject>()
    var writes = 0
    var catalogReads = 0
    var privateReads = 0
    var failReads = false
    var failBeforeWrite = false
    var failAfterWrite = false
    var afterWrite: (suspend () -> Unit)? = null
    override suspend fun read(table: PrivateTable): List<JsonObject> {
        privateReads++
        if (failReads) throw IOException("Isolated read failure")
        return rows.filterKeys { it.startsWith(table.wire + "/") }.values.toList()
    }
    override suspend fun write(table: PrivateTable, id: String, pending: PendingWrite, payload: JsonObject): JsonObject {
        writes++
        if (failBeforeWrite) throw IOException("Offline")
        val key = "${table.wire}/$id"
        val previous = rows[key]
        if (previous?.text("last_mutation_id") == pending.mutationId) return previous
        if ((previous?.long("version") ?: 0) != pending.expectedVersion) throw SyncConflictException()
        val row = JsonObject(payload + mapOf(
            "id" to JsonPrimitive(id), "user_id" to JsonPrimitive(owner),
            "version" to JsonPrimitive(pending.expectedVersion + 1),
            "last_mutation_id" to JsonPrimitive(pending.mutationId)
        ))
        rows[key] = row
        afterWrite?.invoke()
        if (failAfterWrite) throw IOException("Reply lost after commit")
        return row
    }
    override suspend fun catalog(): Catalog {
        catalogReads++
        return Catalog(emptyList(), emptyList(), emptyList())
    }
}
class BackendSyncTest {
    private fun journal(notes: String = "Isolated observation") = JournalRecord(
        id = ID, entry_date = "2026-11-01", category = "notes", notes = notes
    )
    private fun initialized(owner: String = A) = BackendCache(accounts = mapOf(owner to AccountCache(initialized = true)))

    @Test fun offlineWritesAreDurablePendingAndReadOnlyWithinTheirOwner() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val engine = SyncEngine(auth, store, FakeRemote())
        val records = BackendRecordsRepository(auth, store, engine)
        records.saveJournal(A, journal())
        val entry = records.observe(A, PrivateTable.JOURNAL).first().single()
        assertNotNull(entry.pending)
        assertEquals(0, entry.pending!!.expectedVersion)
        assertEquals("Isolated observation", entry.payload.text("notes"))
        auth.value.value = AccountSession(true, B)
        assertTrue(records.observe(B, PrivateTable.JOURNAL).catch { emit(emptyList()) }.first().isEmpty())
        assertEquals(1, store.snapshot().accounts[A]!!.records.size)
    }

    @Test fun wrongAccountCannotUseAStaleRepositoryOrSaveCallback() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val records = BackendRecordsRepository(auth, store, SyncEngine(auth, store, FakeRemote()))
        val legacy = object : PregnancyProfileRepository {
            override val pregnancyProfile = flowOf<PregnancyProfile?>(null)
            override suspend fun savePregnancyProfile(profile: PregnancyProfile) = error("Must not write guest data")
            override suspend fun clearProfile() = error("Must not clear guest data")
        }
        val pregnancy = AccountPregnancyRepository(A, records, legacy)
        auth.value.value = AccountSession(true, B)
        assertFails { records.saveJournal(A, journal()) }
        assertFails { pregnancy.savePregnancyProfile(PregnancyProfile()) }
        assertTrue(store.snapshot().accounts[A]!!.records.isEmpty())
        assertNull(store.snapshot().accounts[B])
    }

    @Test fun initialReadFailureDoesNotBecomeAnEmptyAccountAndBlocksBlindSaves() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore()
        val remote = FakeRemote().apply { failReads = true }
        val records = BackendRecordsRepository(auth, store, SyncEngine(auth, store, remote))
        assertFails { records.observe(A, PrivateTable.PREGNANCY).first() }
        assertFails { records.saveJournal(A, journal()) }
        assertNull(store.snapshot().accounts[A])
        remote.failReads = false
        assertTrue(records.observe(A, PrivateTable.PREGNANCY).first().isEmpty())
        assertTrue(store.snapshot().accounts[A]!!.initialized)
    }

    @Test fun successfulSyncAcknowledgesWritesAndPreservesServerVersion() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val remote = FakeRemote()
        val engine = SyncEngine(auth, store, remote, { "2026-11-01T10:00:00Z" })
        val records = BackendRecordsRepository(auth, store, engine)
        records.saveJournal(A, journal())
        engine.synchronize()
        val entry = store.snapshot().accounts[A]!!.records.values.single()
        assertNull(entry.pending)
        assertEquals(1, entry.version)
        assertEquals("2026-11-01T10:00:00Z", store.snapshot().accounts[A]!!.lastSyncedAt)
    }

    @Test fun retryAfterLostReplyUsesSameMutationWithoutDuplicatingTheRecord() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val remote = FakeRemote().apply { failAfterWrite = true }
        val engine = SyncEngine(auth, store, remote)
        val records = BackendRecordsRepository(auth, store, engine)
        records.saveJournal(A, journal())
        assertFails { engine.synchronize() }
        assertNotNull(store.snapshot().accounts[A]!!.records.values.single().pending)
        remote.failAfterWrite = false
        engine.synchronize()
        assertEquals(1, remote.rows.size)
        assertEquals(1, remote.rows.values.single().long("version"))
        assertNull(store.snapshot().accounts[A]!!.records.values.single().pending)
    }

    @Test fun editsDuringAnInFlightRequestAreNotLostByAcknowledgement() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val remote = FakeRemote()
        val engine = SyncEngine(auth, store, remote)
        val records = BackendRecordsRepository(auth, store, engine)
        records.saveJournal(A, journal("First"))
        val requestSent = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        remote.afterWrite = { requestSent.complete(Unit); reply.await() }
        val job = launch { engine.synchronize() }
        requestSent.await()
        records.saveJournal(A, journal("Edited while syncing"))
        reply.complete(Unit)
        job.join()
        val entry = store.snapshot().accounts[A]!!.records.values.single()
        assertEquals("Edited while syncing", entry.payload.text("notes"))
        assertEquals(1, entry.pending!!.expectedVersion)
        remote.afterWrite = null
        engine.synchronize()
        assertEquals("Edited while syncing", remote.rows.values.single().text("notes"))
        assertNull(store.snapshot().accounts[A]!!.records.values.single().pending)
    }

    @Test fun conflictRequiresAnExplicitChoiceBeforeReplacingNewerServerData() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val remote = FakeRemote()
        val engine = SyncEngine(auth, store, remote)
        val records = BackendRecordsRepository(auth, store, engine)
        records.saveJournal(A, journal("Original"))
        engine.synchronize()
        val key = "health_journal/$ID"
        remote.rows[key] = JsonObject(remote.rows[key]!! + mapOf("version" to JsonPrimitive(2), "notes" to JsonPrimitive("Other device")))
        records.saveJournal(A, journal("Local edit"))
        engine.synchronize()
        assertEquals("Local edit", store.snapshot().accounts[A]!!.records[key]!!.payload.text("notes"))
        assertNotNull(store.snapshot().accounts[A]!!.records[key]!!.conflict)
        assertFails { records.saveJournal(A, journal("Must review first")) }
        records.resolve(A, key, keepLocal = true)
        assertEquals(2, store.snapshot().accounts[A]!!.records[key]!!.pending!!.expectedVersion)
        engine.synchronize()
        assertEquals(3, remote.rows[key]!!.long("version"))
        assertEquals("Local edit", remote.rows[key]!!.text("notes"))
    }

    @Test fun choosingServerClearsPendingEditsWithoutAnotherUpload() = runTest {
        val auth = FakeAccountAuth()
        val remote = FakeRemote()
        val server = buildJsonObject { put("id",ID); put("user_id",A); put("version",2); put("notes","Server") }
        val local = CachedRecord("health_journal",ID,buildJsonObject { put("notes","Local") },1,PendingWrite(ID,1),server)
        val store = MemoryBackendStore(BackendCache(accounts = mapOf(A to AccountCache(mapOf(local.key to local),true))))
        val records = BackendRecordsRepository(auth,store,SyncEngine(auth,store,remote))
        records.resolve(A,local.key,false)
        assertNull(store.snapshot().accounts[A]!!.records[local.key]!!.pending)
        assertEquals("Server",store.snapshot().accounts[A]!!.records[local.key]!!.payload.text("notes"))
        assertEquals(0,remote.writes)
    }

    @Test fun cancellationKeepsThePendingMutationRecoverable() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val remote = FakeRemote()
        val engine = SyncEngine(auth,store,remote)
        val records = BackendRecordsRepository(auth,store,engine)
        records.saveJournal(A,journal())
        val entered = CompletableDeferred<Unit>()
        remote.afterWrite = { entered.complete(Unit); awaitCancellation() }
        val job = launch { engine.synchronize() }
        entered.await()
        job.cancelAndJoin()
        assertFalse(engine.state.value.isRunning)
        assertNotNull(store.snapshot().accounts[A]!!.records.values.single().pending)
        remote.afterWrite = null
        engine.synchronize()
        assertNull(store.snapshot().accounts[A]!!.records.values.single().pending)
    }

    @Test fun deletingQueuesATombstoneAndNeverClearsOtherRecords() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val engine = SyncEngine(auth,store,FakeRemote())
        val records = BackendRecordsRepository(auth,store,engine)
        records.saveJournal(A,journal())
        records.saveJournal(A,journal("Second").copy(id = B))
        records.delete(A,store.snapshot().accounts[A]!!.records["health_journal/$ID"]!!)
        assertEquals(2,store.snapshot().accounts[A]!!.records.size)
        assertTrue(store.snapshot().accounts[A]!!.records["health_journal/$ID"]!!.deleted)
        engine.synchronize()
        assertTrue(store.snapshot().accounts[A]!!.records["health_journal/$ID"]!!.deleted)
        assertNull(store.snapshot().accounts[A]!!.records["health_journal/$ID"]!!.pending)
    }

    @Test fun unexpectedServerOwnershipFailsClosed() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore()
        val remote = FakeRemote().apply {
            rows["health_journal/$ID"] = buildJsonObject { put("id",ID); put("user_id",B); put("version",1); put("notes","Forbidden") }
        }
        val engine = SyncEngine(auth,store,remote)
        assertFails { engine.synchronize() }
        assertTrue(store.snapshot().accounts[A]?.records.orEmpty().isEmpty())
        assertFalse(store.snapshot().accounts[A]?.initialized == true)
    }

    @Test fun signingInDoesNotAutomaticallyUploadLegacyOrGuestRecords() = runTest {
        val auth = FakeAccountAuth()
        val guest = CachedRecord("health_journal",ID,BackendJson.encodeToJsonElement(journal()).jsonObject)
        val store = MemoryBackendStore(BackendCache(accounts = mapOf(LOCAL_OWNER to AccountCache(mapOf(guest.key to guest)))))
        val remote = FakeRemote()
        val records = BackendRecordsRepository(auth,store,SyncEngine(auth,store,remote))
        records.observe(A,PrivateTable.PREGNANCY).first()
        assertEquals(0,remote.writes)
        assertTrue(store.snapshot().accounts[A]!!.records.isEmpty())
        assertEquals(1,store.snapshot().accounts[LOCAL_OWNER]!!.records.size)
    }

    @Test fun explicitImportCopiesRecordsOnceAndPreservesTheOriginals() = runTest {
        val auth = FakeAccountAuth()
        val guest = CachedRecord("health_journal",ID,BackendJson.encodeToJsonElement(journal()).jsonObject)
        val store = MemoryBackendStore(BackendCache(accounts = mapOf(
            LOCAL_OWNER to AccountCache(mapOf(guest.key to guest)), A to AccountCache(initialized = true))))
        var legacyWrites = 0
        val original = PregnancyProfile(displayName="Isolated QA",lmpDateIso="2026-06-01",
            healthcareFacility="",emergencyContactName="",emergencyContactPhone="")
        val legacy = object : PregnancyProfileRepository {
            override val pregnancyProfile = flowOf(original)
            override suspend fun savePregnancyProfile(profile: PregnancyProfile) { legacyWrites++ }
            override suspend fun clearProfile() { legacyWrites++ }
        }
        val remote = FakeRemote()
        val records = BackendRecordsRepository(auth,store,SyncEngine(auth,store,remote))
        records.importLocalData(A,legacy)
        val account = store.snapshot().accounts[A]!!
        assertEquals(2,account.records.size)
        assertTrue(account.localDataImported)
        assertTrue(account.records.values.all { it.pending != null })
        assertEquals(guest,store.snapshot().accounts[LOCAL_OWNER]!!.records[guest.key])
        assertEquals(0,legacyWrites)
        assertEquals(0,remote.writes)
        assertFails { records.importLocalData(A,legacy) }
    }

    @Test fun cacheSerializationPreservesPendingWritesAndConflictsAcrossReopen() {
        val row = CachedRecord("health_journal",ID,buildJsonObject { put("notes","Local") },1,
            PendingWrite(ID,1),buildJsonObject { put("version",2) })
        val cache = BackendCache(accounts=mapOf(A to AccountCache(mapOf(row.key to row),true,true)))
        assertEquals(cache,BackendJson.decodeFromString<BackendCache>(BackendJson.encodeToString(cache)))
    }

    @Test fun guidanceRefreshIsAnonymousReadOnlyAndUsesACacheDuringPrivateSync() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val remote = FakeRemote()
        val engine = SyncEngine(auth,store,remote, { "2026-11-01T10:00:00Z" })
        val records = BackendRecordsRepository(auth,store,engine)
        records.saveJournal(A,journal())
        auth.value.value = AccountSession(true)
        engine.refreshCatalog()
        assertEquals(0,remote.writes)
        assertEquals(1,remote.catalogReads)
        assertNotNull(store.snapshot().accounts[A]!!.records.values.single().pending)
        auth.value.value = AccountSession(true,A)
        engine.synchronize()
        engine.synchronize()
        assertEquals(1,remote.catalogReads)
        engine.refreshCatalog()
        assertEquals(2,remote.catalogReads)
    }

    @Test fun incorrectWriteResponseIdentityNeverAcknowledgesThePendingChange() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore(initialized())
        val remote = object : BackendRemote {
            override suspend fun read(table: PrivateTable) = emptyList<JsonObject>()
            override suspend fun catalog() = Catalog(emptyList(),emptyList(),emptyList())
            override suspend fun write(table: PrivateTable,id: String,pending: PendingWrite,payload: JsonObject) =
                buildJsonObject { put("id",B); put("user_id",A); put("version",1) }
        }
        val engine = SyncEngine(auth,store,remote)
        val records = BackendRecordsRepository(auth,store,engine)
        records.saveJournal(A,journal())
        assertFails { engine.synchronize() }
        assertNotNull(store.snapshot().accounts[A]!!.records["health_journal/$ID"]!!.pending)
    }

    @Test fun simultaneousScreenSubscriptionsShareOneInitialDownload() = runTest {
        val auth = FakeAccountAuth()
        val store = MemoryBackendStore()
        val remote = FakeRemote()
        val engine = SyncEngine(auth,store,remote)
        coroutineScope { List(5) { async { engine.ensureLoaded(A) } }.awaitAll() }
        assertEquals(PrivateTable.entries.size,remote.privateReads)
        assertEquals(0,remote.writes)
        assertEquals(0,remote.catalogReads)
    }

    @Test fun clientConfigurationRejectsSecretKeysAndInsecureUrls() {
        listOf("sb_secret_invalid","eyJserviceRoleToken").forEach {
            assertThrows(IllegalArgumentException::class.java) { BackendConfig("https://project.supabase.co",it).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { BackendConfig("http://project.supabase.co","sb_publishable_example").validate() }
        BackendConfig("https://project.supabase.co","sb_publishable_example").validate()
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        var failed = false
        try { block() } catch (_: Exception) { failed = true }
        assertTrue("Expected a safe failure",failed)
    }
}
