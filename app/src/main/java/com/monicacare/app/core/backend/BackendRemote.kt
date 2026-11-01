package com.monicacare.app.core.backend

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.*

interface BackendRemote {
    suspend fun read(table: PrivateTable): List<JsonObject>
    suspend fun write(table: PrivateTable, id: String, pending: PendingWrite, payload: JsonObject): JsonObject
    suspend fun catalog(): Catalog
}
data class Catalog(
    val weeks: List<WeekRecord>, val articles: List<ArticleRecord>,
    val contacts: List<EmergencyContactRecord>
)
class SyncConflictException : Exception("Changes on another device need review.")

class SupabaseRemote(private val client: SupabaseClient) : BackendRemote {
    private suspend fun rows(table: String, key: String = "id"): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        var offset = 0L
        while (true) {
            val page = client.from(table).select {
                order(key, Order.ASCENDING)
                range(offset, offset + 199)
            }.decodeList<JsonObject>()
            result += page
            if (page.size < 200) return result
            offset += 200
            check(offset < 10000) { "Download limit reached; narrow the dataset before retrying." }
        }
    }
    override suspend fun read(table: PrivateTable) =
        rows(table.wire, if (table == PrivateTable.SETTINGS) "user_id" else "id")

    override suspend fun write(table: PrivateTable, id: String, pending: PendingWrite, payload: JsonObject): JsonObject {
        return try {
            client.postgrest.rpc("sync_private_record", buildJsonObject {
                put("p_table", table.wire); put("p_id", id)
                put("p_expected_version", pending.expectedVersion)
                put("p_mutation_id", pending.mutationId); put("p_payload", payload)
            }).decodeAs<JsonObject>()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (e.message?.contains("SYNC_CONFLICT") == true || e.message?.contains("23505") == true)
                throw SyncConflictException()
            throw e
        }
    }
    private suspend fun publicRows(table: String, maximum: Long): List<JsonObject> =
        client.from(table).select {
            order("updated_at", Order.DESCENDING)
            order("id", Order.ASCENDING)
            limit(maximum)
        }.decodeList<JsonObject>()

    override suspend fun catalog() = Catalog(
        publicRows("pregnancy_weeks", 43).map { BackendJson.decodeFromJsonElement<WeekRecord>(it) },
        publicRows("educational_articles", 50).map { BackendJson.decodeFromJsonElement<ArticleRecord>(it) },
        publicRows("emergency_contacts", 200).map { BackendJson.decodeFromJsonElement<EmergencyContactRecord>(it) }
    )
}
