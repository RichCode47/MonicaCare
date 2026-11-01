package com.monicacare.app.feature.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monicacare.app.core.backend.EmergencyContactRecord
import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.data.DataStorePregnancyProfileRepository
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import kotlinx.coroutines.flow.catch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monicacare.app.core.designsystem.component.MonicaCareCard
import com.monicacare.app.core.designsystem.theme.EmergencyRed
import com.monicacare.app.core.designsystem.theme.EmergencyRedContainer

private val urgentSymptoms = listOf(
    "Severe headache, blurred vision, or severe swelling of face and hands (Preeclampsia risk)",
    "Vaginal bleeding or unusual fluid discharge",
    "Severe abdominal pain or cramping",
    "Noticeable decrease or absence of fetal movement",
    "High fever or chills",
    "Difficulty breathing or chest tightness"
)

@Composable
fun EmergencyScreen(
    profileRepository: PregnancyProfileRepository? = null,
    contacts: List<EmergencyContactRecord> = emptyList()
) {
    val context = LocalContext.current
    val repository = profileRepository ?: remember(context) { DataStorePregnancyProfileRepository(context) }
    var contactError by remember(repository) { mutableStateOf<String?>(null) }
    val profileFlow = remember(repository) {
        repository.pregnancyProfile.catch { contactError = "Your personal emergency contact is unavailable." }
    }
    val profile by profileFlow.collectAsStateWithLifecycle(initialValue = null)
    fun dial(phone: String) {
        if (!Regex("^\\+?[0-9][0-9 ()-]{2,39}$").matches(phone)) {
            contactError = "This contact does not have a valid phone number."
            return
        }
        try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
        catch (_: ActivityNotFoundException) { contactError = "No phone dialer is available on this device." }
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = EmergencyRedContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EmergencyRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Emergency Guidance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmergencyRed
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "If you experience any of the following warning signs, seek immediate care at the nearest healthcare facility or contact your emergency contact.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        contactError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        profile?.takeIf { it.emergencyContactPhone.isNotBlank() }?.let { current ->
            Button(
                onClick = { dial(current.emergencyContactPhone) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open dialer: " + current.emergencyContactName.ifBlank { "Personal emergency contact" })
            }
        }
        contacts.forEach { contact ->
            MonicaCareCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(contact.location + " • " + contact.category)
                    OutlinedButton(onClick = { dial(contact.phone_number) }) { Text("Open dialer: " + contact.phone_number) }
                }
            }
        }

        Text(
            text = "Urgent Warning Symptoms",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        urgentSymptoms.forEach { symptom ->
            MonicaCareCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️ ", fontWeight = FontWeight.Bold)
                    Text(
                        text = symptom,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
