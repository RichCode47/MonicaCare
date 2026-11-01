package com.monicacare.app.feature.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monicacare.app.core.backend.*
import com.monicacare.app.core.designsystem.component.MonicaCareCard
import kotlinx.serialization.json.*

@Composable
fun AccountScreen(viewModel: BackendViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.backend.auth.session.collectAsStateWithLifecycle()
    val sync by viewModel.backend.sync.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }
    val busy = state.isBusy || sync.isRunning
    val signedIn = session.userId != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Account & Sync", style = MaterialTheme.typography.headlineMedium)
        Text("Your account downloads once after first sign-in. Later sync runs when you choose Sync. Device-only records are never uploaded automatically.",
            style = MaterialTheme.typography.bodyMedium)
        BackendNotices(state, viewModel::load)

        if (!viewModel.backend.config.isConfigured || viewModel.backend.configurationError != null) {
            Text("Cloud accounts are unavailable in this build. You can continue using device storage.")
        } else if (!signedIn) {
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password (at least 8 characters to register)") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.signIn(email, password); password = "" }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
            OutlinedButton(onClick = { viewModel.signUp(email, password); password = "" }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("Create account") }
            Text("Confirm your email, then return here to sign in. Device records are not automatically uploaded.")
        } else {
            Text(session.email ?: "Signed in", style = MaterialTheme.typography.titleMedium)
            val pending = state.records.count { it.pending != null }
            Text("$pending pending changes")
            Text(state.lastSyncedAt?.let { "Last complete sync: $it" } ?: "No complete sync recorded.")
            Button(onClick = viewModel::synchronize, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text(if (sync.isRunning) "Syncing…" else "Sync now") }
            if (!state.localDataImported) {
                OutlinedButton(onClick = { showImport = true }, enabled = !busy && !state.isLoading && state.error == null,
                    modifier = Modifier.fillMaxWidth()) { Text("Copy this device's records to my account") }
            }
            OutlinedTextField(displayName, { displayName = it }, label = { Text("Account display name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = { viewModel.saveDisplayName(displayName) },
                enabled = !busy && !state.isLoading && state.error == null) { Text("Save account name") }
            OutlinedButton(onClick = { showSignOut = true }, enabled = !busy) { Text("Sign out on this device") }
        }
        OutlinedButton(onClick = { showSettings = true },
            enabled = !busy && !state.isLoading && state.error == null) { Text("Language & reminder preferences") }

        state.records.filter { it.conflict != null }.forEach { record ->
            MonicaCareCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Changes need review", style = MaterialTheme.typography.titleMedium)
                    Text(record.payload.text("title").ifBlank { recordTitle(record.table) })
                    Text("Another device changed this record. Keep the server copy or deliberately replace it with your saved edits.")
                    Text("Your saved edits:\n" + recordSummary(record.payload))
                    Text("Server copy:\n" + recordSummary(record.conflict!!))
                    OutlinedButton(onClick = { viewModel.resolve(record.key, false) }, enabled = !busy) { Text("Use server copy") }
                    OutlinedButton(onClick = { viewModel.resolve(record.key, true) }, enabled = !busy) { Text("Keep my edits for next sync") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showImport) AlertDialog(
        onDismissRequest = { if (!busy) showImport = false },
        title = { Text("Copy device records?") },
        text = { Text("This copies the local pregnancy profile, appointments and journal entries to this account's cache. Originals remain on the device. Sync uploads the copies. Existing account pregnancy records prevent this import.") },
        confirmButton = { TextButton(onClick = { showImport = false; viewModel.importLocal() }, enabled = !busy) { Text("Copy records") } },
        dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
    )
    if (showSignOut) AlertDialog(
        onDismissRequest = { showSignOut = false },
        title = { Text("Sign out on this device?") },
        text = { Text("Pending changes remain encrypted for this account. Other accounts cannot view them. Device-only records remain in device mode. Signing out offline does not guarantee remote session revocation.") },
        confirmButton = { TextButton(onClick = { showSignOut = false; viewModel.signOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { showSignOut = false }) { Text("Cancel") } }
    )
    if (showSettings) SettingsDialog(
        current = state.records.firstOrNull { it.table == "user_settings" && !it.deleted }?.payload?.let {
            BackendJson.decodeFromJsonElement<SettingsRecord>(it)
        } ?: SettingsRecord(),
        busy = busy, onDismiss = { showSettings = false },
        onSave = { value -> viewModel.saveSettings(value) { showSettings = false } }
    )
}

@Composable
fun BackendNotices(state: BackendUiState, onRetry: () -> Unit) {
    if (state.isLoading || state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry, enabled = !state.isBusy) { Text("Retry load") }
    }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun SettingsDialog(current: SettingsRecord, busy: Boolean, onDismiss: () -> Unit, onSave: (SettingsRecord) -> Unit) {
    var language by remember { mutableStateOf(current.preferred_language) }
    var appointments by remember { mutableStateOf((current.notification_preferences["appointments"] as? JsonPrimitive)?.booleanOrNull ?: false) }
    var journal by remember { mutableStateOf((current.notification_preferences["journal"] as? JsonPrimitive)?.booleanOrNull ?: false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Preferences") }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preferred language is saved for future localized content. Current app content is English.")
            listOf("en" to "English", "bem" to "Bemba", "ny" to "Nyanja").forEach { (code, label) ->
                Row { RadioButton(selected = language == code, onClick = { language = code }); Text(label, Modifier.padding(top = 12.dp)) }
            }
            Text("Reminder preferences are stored only. This build does not send notifications.")
            Row { Checkbox(appointments, { appointments = it }); Text("Appointments", Modifier.padding(top = 12.dp)) }
            Row { Checkbox(journal, { journal = it }); Text("Health journal", Modifier.padding(top = 12.dp)) }
        }
    }, confirmButton = {
        TextButton(onClick = { onSave(SettingsRecord(language, buildJsonObject {
            put("appointments", appointments); put("journal", journal)
        })) }, enabled = !busy) { Text("Save preferences") }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } })
}
