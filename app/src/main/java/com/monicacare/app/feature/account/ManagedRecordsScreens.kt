package com.monicacare.app.feature.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monicacare.app.core.backend.*
import com.monicacare.app.core.designsystem.component.MonicaCareCard
import kotlinx.serialization.json.*
import java.time.LocalDate

@Composable
fun ManagedRecordsScreen(viewModel: BackendViewModel, journal: Boolean) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val table = if (journal) "health_journal" else "appointments"
    val records = state.records.filter { it.table == table && !it.deleted }
        .sortedByDescending { it.payload.text(if (journal) "entry_date" else "appointment_date") }
    var editing by remember { mutableStateOf<CachedRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CachedRecord?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (journal) "Maternal Health Journal" else "Antenatal Appointments",
            style = MaterialTheme.typography.headlineMedium)
        Text(if (viewModel.owner == LOCAL_OWNER) "Device-only records" else "Changes are saved on this device. Use Account & Sync to upload them.",
            style = MaterialTheme.typography.bodyMedium)
        if (!journal) Text("Appointment times use Zambia time (Africa/Lusaka).")
        BackendNotices(state, viewModel::load)
        Button(onClick = { editing = null; showEditor = true },
            enabled = !state.isLoading && !state.isBusy && state.error == null, modifier = Modifier.fillMaxWidth()) {
            Text(if (journal) "+ Record Today's Health Log" else "+ Schedule New ANC Visit")
        }
        if (!state.isLoading && records.isEmpty() && state.error == null) {
            Text(if (journal) "No saved journal entries yet." else "No saved appointments yet.")
        }
        records.forEach { record ->
            MonicaCareCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (journal) {
                        Text(categoryLabel(record.payload.text("category")), style = MaterialTheme.typography.titleMedium)
                        Text(record.payload.text("entry_date"))
                    } else {
                        Text(record.payload.text("title"), style = MaterialTheme.typography.titleMedium)
                        Text(record.payload.text("appointment_date") + " at " + record.payload.text("appointment_time"))
                        listOf("facility_name", "provider_name").map { record.payload.text(it) }
                            .filter { it.isNotBlank() }.forEach { Text(it) }
                        Text(statusLabel(record.payload.text("status")))
                    }
                    Text(record.payload.text("notes"))
                    Text(when {
                        record.conflict != null -> "Needs review in Account & Sync"
                        viewModel.owner == LOCAL_OWNER -> "Device only"
                        record.pending != null -> "Pending sync"
                        else -> "Synced"
                    }, style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = record; showEditor = true },
                            enabled = !state.isBusy && record.conflict == null) { Text("Edit") }
                        TextButton(onClick = { deleting = record }, enabled = !state.isBusy && record.conflict == null) { Text("Remove") }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (showEditor) {
        val pregnancyId = state.records.firstOrNull {
            it.table == "pregnancy_profiles" && !it.deleted && it.payload.text("pregnancy_status") == "active"
        }?.id
        if (journal) JournalEditor(editing, pregnancyId, state, { showEditor = false }) { value ->
            viewModel.saveJournal(value) { showEditor = false }
        } else AppointmentEditor(editing, pregnancyId, state, { showEditor = false }) { value ->
            viewModel.saveAppointment(value) { showEditor = false }
        }
    }
    deleting?.let { record ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove record?") },
            text = { Text("This removes the record from this device's list. For a signed-in account, the removal is sent on your next sync.") },
            confirmButton = { TextButton(onClick = { deleting = null; viewModel.delete(record) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun AppointmentEditor(record: CachedRecord?, pregnancyId: String?, state: BackendUiState, dismiss: () -> Unit, save: (AppointmentRecord) -> Unit) {
    val initial = remember(record?.id) {
        record?.payload?.let { BackendJson.decodeFromJsonElement<AppointmentRecord>(it) }
            ?: AppointmentRecord(pregnancy_profile_id = pregnancyId, title = "",
                appointment_date = LocalDate.now().toString(), appointment_time = "09:00")
    }
    var title by rememberSaveable(record?.id) { mutableStateOf(initial.title) }
    var date by rememberSaveable(record?.id) { mutableStateOf(initial.appointment_date) }
    var time by rememberSaveable(record?.id) { mutableStateOf(initial.appointment_time) }
    var facility by rememberSaveable(record?.id) { mutableStateOf(initial.facility_name) }
    var provider by rememberSaveable(record?.id) { mutableStateOf(initial.provider_name) }
    var notes by rememberSaveable(record?.id) { mutableStateOf(initial.notes) }
    var status by rememberSaveable(record?.id) { mutableStateOf(initial.status) }
    RecordDialog("Appointment", state, dismiss, {
        save(initial.copy(title = title.trim(), appointment_date = date.trim(), appointment_time = time.trim(),
            facility_name = facility.trim(), provider_name = provider.trim(), notes = notes.trim(), status = status))
    }) {
        OutlinedTextField(title, { title = it }, label = { Text("Visit title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(time, { time = it }, label = { Text("Zambia time (HH:MM)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(facility, { facility = it }, label = { Text("Facility") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(provider, { provider = it }, label = { Text("Provider") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("upcoming", "completed", "cancelled").forEach { value ->
                FilterChip(status == value, { status = value }, label = { Text(statusLabel(value)) })
            }
        }
    }
}

@Composable
private fun JournalEditor(record: CachedRecord?, pregnancyId: String?, state: BackendUiState, dismiss: () -> Unit, save: (JournalRecord) -> Unit) {
    val initial = remember(record?.id) {
        record?.payload?.let { BackendJson.decodeFromJsonElement<JournalRecord>(it) }
            ?: JournalRecord(pregnancy_profile_id = pregnancyId, entry_date = LocalDate.now().toString(), category = "notes", notes = "")
    }
    var date by rememberSaveable(record?.id) { mutableStateOf(initial.entry_date) }
    var category by rememberSaveable(record?.id) { mutableStateOf(initial.category) }
    var notes by rememberSaveable(record?.id) { mutableStateOf(initial.notes) }
    RecordDialog("Health journal entry", state, dismiss, {
        save(initial.copy(entry_date = date.trim(), category = category, notes = notes.trim()))
    }) {
        OutlinedTextField(date, { date = it }, label = { Text("Entry date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            journalCategories.forEach { value ->
                FilterChip(category == value, { category = value }, label = { Text(categoryLabel(value)) })
            }
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("Your notes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("These are your own observations. The app does not classify readings or diagnose symptoms.",
            style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun RecordDialog(title: String, state: BackendUiState, dismiss: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = { if (!state.isBusy) dismiss() }, title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = save, enabled = !state.isBusy) { Text("Save on device") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !state.isBusy) { Text("Cancel") } })
}
fun categoryLabel(value: String) = when (value) {
    "blood_pressure" -> "Blood pressure"
    "weight" -> "Weight"
    "symptoms" -> "Symptoms"
    "labs" -> "Lab notes"
    else -> "Notes"
}
private fun statusLabel(value: String) = value.replaceFirstChar { it.uppercase() }

@Composable
fun ManagedEducationScreen(viewModel: BackendViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Pregnancy Education", style = MaterialTheme.typography.headlineMedium)
        Text("Download the latest 50 published articles for offline reading. This does not upload your private records.")
        Button(onClick = viewModel::refreshGuidance, enabled = viewModel.backend.config.isConfigured && !state.isBusy) {
            Text("Refresh published guidance")
        }
        BackendNotices(state, viewModel::load)
        if (state.articles.isEmpty()) {
            Text("No published articles have been downloaded. Pregnancy Journey's bundled guidance remains available offline.")
        }
        state.articles.forEach { article ->
            MonicaCareCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(article.category, style = MaterialTheme.typography.labelLarge)
                    Text(article.title, style = MaterialTheme.typography.titleLarge)
                    Text(article.content)
                }
            }
        }
        Text("Educational information supports care from a qualified healthcare professional.",
            style = MaterialTheme.typography.bodySmall)
    }
}
