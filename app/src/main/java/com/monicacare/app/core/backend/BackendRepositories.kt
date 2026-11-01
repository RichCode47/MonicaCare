package com.monicacare.app.core.backend

import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.model.PregnancyProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

const val LOCAL_OWNER = "local"
@OptIn(ExperimentalCoroutinesApi::class)
class BackendRecordsRepository(
    val auth: AccountAuth, val store: BackendStore, val sync: SyncEngine,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    fun owner() = auth.session.value.userId ?: LOCAL_OWNER
    fun observe(ownerId: String, table: PrivateTable): Flow<List<CachedRecord>> = flow {
        check(owner() == ownerId) { "Account changed." }
        if (ownerId != LOCAL_OWNER) sync.ensureLoaded(ownerId)
        emitAll(store.data.map { cache ->
            check(owner() == ownerId) { "Account changed." }
            cache.accounts[ownerId]?.records?.values.orEmpty().filter { it.table == table.wire && !it.deleted }
        })
    }
    suspend fun save(ownerId: String, table: PrivateTable, id: String, payload: JsonObject) {
        check(owner() == ownerId && auth.session.value.initialized) { "Account changed." }
        if (table != PrivateTable.PROFILE && table != PrivateTable.SETTINGS) UUID.fromString(id)
        require(payload.toString().toByteArray().size <= 20000) { "Record is too large." }
        store.update { cache ->
            check(owner() == ownerId) { "Account changed." }
            cache.updateAccount(ownerId) { account ->
                check(ownerId == LOCAL_OWNER || account.initialized) { "Load your account before saving." }
                val parent = payload.text("pregnancy_profile_id")
                if (parent.isNotBlank()) {
                    check(account.records["pregnancy_profiles/$parent"] != null) {
                        "The selected pregnancy does not belong to this account."
                    }
                }
                val key = "${table.wire}/$id"
                val existing = account.records[key]
                check(existing?.conflict == null) { "Resolve this record's conflict before editing." }
                val record = CachedRecord(table.wire, id, payload, existing?.version ?: 0,
                    PendingWrite(newId(), existing?.pending?.expectedVersion ?: existing?.version ?: 0))
                account.copy(records = account.records + (key to record))
            }
        }
    }
    suspend fun delete(ownerId: String, record: CachedRecord) {
        val table = PrivateTable.entries.first { it.wire == record.table }
        save(ownerId, table, record.id, JsonObject(record.payload + ("deleted_at" to JsonPrimitive(Instant.now().toString()))))
    }
    suspend fun resolve(ownerId: String, key: String, keepLocal: Boolean) {
        store.update { cache ->
            check(owner() == ownerId) { "Account changed." }
            cache.updateAccount(ownerId) { account ->
                val record = account.records[key] ?: error("Record unavailable.")
                val server = record.conflict ?: error("No conflict to resolve.")
                val table = PrivateTable.entries.first { it.wire == record.table }
                val remote = remoteRecord(table, server)
                check(remote.id == record.id)
                val replacement = if (keepLocal) record.copy(version = remote.version,
                    pending = PendingWrite(newId(), remote.version), conflict = null) else remote
                account.copy(records = account.records + (key to replacement))
            }
        }
    }
    suspend fun saveAppointment(ownerId: String, record: AppointmentRecord) {
        record.validate()
        save(ownerId, PrivateTable.APPOINTMENT, record.id, BackendJson.encodeToJsonElement(record).jsonObject)
    }
    suspend fun saveJournal(ownerId: String, record: JournalRecord) {
        record.validate()
        save(ownerId, PrivateTable.JOURNAL, record.id, BackendJson.encodeToJsonElement(record).jsonObject)
    }
    suspend fun saveSettings(ownerId: String, record: SettingsRecord) {
        require(record.preferred_language in listOf("en","bem","ny"))
        save(ownerId, PrivateTable.SETTINGS, ownerId, BackendJson.encodeToJsonElement(record).jsonObject)
    }

    /** Explicit local copy, never a move, and never an automatic upload after sign-in. */
    suspend fun importLocalData(ownerId: String, legacy: PregnancyProfileRepository) {
        check(ownerId != LOCAL_OWNER && auth.session.value.userId == ownerId)
        sync.ensureLoaded(ownerId)
        auth.sessionMutex.withLock {
            check(owner() == ownerId)
            val pregnancy = legacy.pregnancyProfile.first()
            val payload = pregnancy?.let(::pregnancyPayload)
            store.update { cache ->
                cache.updateAccount(ownerId) { account ->
                    check(!account.localDataImported) { "This device's records have already been copied to this account." }
                    check(account.records.values.none { it.table == "pregnancy_profiles" && !it.deleted }) {
                        "Your account already has pregnancy records. Review them before importing device data."
                    }
                    var records = account.records
                    val pregnancyId = payload?.let { newId() }
                    if (pregnancyId != null && payload != null) {
                        val p = CachedRecord("pregnancy_profiles", pregnancyId, payload, pending = PendingWrite(newId(),0))
                        records = records + (p.key to p)
                    }
                    cache.accounts[LOCAL_OWNER]?.records?.values.orEmpty()
                        .filter { it.table in listOf("appointments","health_journal") && !it.deleted }
                        .forEach { source ->
                            val id = newId()
                            val copied = JsonObject(source.payload + mapOf(
                                "id" to JsonPrimitive(id),
                                "pregnancy_profile_id" to (pregnancyId?.let(::JsonPrimitive) ?: JsonNull)
                            ))
                            val record = CachedRecord(source.table, id, copied, pending = PendingWrite(newId(),0))
                            records = records + (record.key to record)
                        }
                    account.copy(records = records, localDataImported = true)
                }
            }
        }
    }
}

class AccountPregnancyRepository(
    private val ownerId: String,
    private val records: BackendRecordsRepository,
    private val legacy: PregnancyProfileRepository
) : PregnancyProfileRepository {
    override val pregnancyProfile: Flow<PregnancyProfile?> =
        if (ownerId == LOCAL_OWNER) legacy.pregnancyProfile
        else records.observe(ownerId, PrivateTable.PREGNANCY).map { list ->
            list.firstOrNull { it.payload.text("pregnancy_status") == "active" }?.payload?.let(::pregnancyFromPayload)
        }

    override suspend fun savePregnancyProfile(profile: PregnancyProfile) {
        check(records.owner() == ownerId) { "Account changed." }
        if (ownerId == LOCAL_OWNER) legacy.savePregnancyProfile(profile)
        else {
            val account = records.store.snapshot().accounts[ownerId] ?: error("Load your account before saving.")
            val current = account.records.values.firstOrNull {
                it.table == "pregnancy_profiles" && !it.deleted && it.payload.text("pregnancy_status") == "active"
            }
            records.save(ownerId, PrivateTable.PREGNANCY, current?.id ?: UUID.randomUUID().toString(), pregnancyPayload(profile))
        }
    }
    override suspend fun clearProfile() {
        check(records.owner() == ownerId) { "Account changed." }
        if (ownerId == LOCAL_OWNER) legacy.clearProfile()
        else records.store.snapshot().accounts[ownerId]?.records?.values.orEmpty()
            .filter { it.table == "pregnancy_profiles" && !it.deleted && it.payload.text("pregnancy_status") == "active" }
            .forEach { records.delete(ownerId, it) }
    }
}
