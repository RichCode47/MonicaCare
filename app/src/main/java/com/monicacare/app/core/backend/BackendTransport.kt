package com.monicacare.app.core.backend

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.SupabaseClientBuilder
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.logging.SupabaseLogger
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders

@OptIn(SupabaseInternal::class)
private fun disableSdkLogger(logger: SupabaseLogger) {
    try { logger.setLevel(LogLevel.NONE) }
    catch (e: IllegalStateException) {
        // SDK 3.2.6 sets its level before attempting to mutate an immutable Kermit config.
        // All SDK log helpers gate on this level. Fail closed if a future SDK behaves differently.
        if (logger.level != LogLevel.NONE) throw e
    }
}

/** Compatibility safeguards for the Kotlin-2.2-compatible SDK line. */
@OptIn(SupabaseInternal::class)
fun SupabaseClientBuilder.configurePrivateTransport(publishableKey: String) {
    defaultLogLevel = LogLevel.NONE
    // This SDK's static loggers can capture their level before the builder is configured.
    listOf(SupabaseClient.LOGGER, Auth.logger, Postgrest.logger, Storage.logger).forEach(::disableSdkLogger)
    httpConfig {
        install(createClientPlugin("MonicaCarePublishableHeaders") {
            onRequest { request, _ ->
                // A publishable key identifies the app in apikey; only a user JWT is a bearer token.
                if (request.headers[HttpHeaders.Authorization] == "Bearer $publishableKey") {
                    request.headers.remove(HttpHeaders.Authorization)
                }
            }
        })
    }
}
