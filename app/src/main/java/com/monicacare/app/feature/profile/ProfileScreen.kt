package com.monicacare.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import com.monicacare.app.core.data.PregnancyProfileRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.monicacare.app.core.data.DataStorePregnancyProfileRepository
import com.monicacare.app.core.designsystem.component.MonicaCareCard
import com.monicacare.app.core.designsystem.theme.LightBlush
import com.monicacare.app.core.designsystem.theme.RosePinkPrimary
import com.monicacare.app.core.model.PregnancyProfile

private val settingsItems = listOf(
    "Notification Preferences",
    "Language & Region (Zambia / English)",
    "Theme & Accessibility",
    "Privacy & Data Safety",
    "Help & Support"
)

@Composable
fun ProfileScreen(
    onNavigateToProfileSetup: () -> Unit = {},
    profileRepository: PregnancyProfileRepository? = null,
    onNavigateToAccount: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = profileRepository ?: remember(context) { DataStorePregnancyProfileRepository(context) }
    var retry by remember(repository) { mutableIntStateOf(0) }
    var loading by remember(repository) { mutableStateOf(true) }
    var error by remember(repository) { mutableStateOf<String?>(null) }
    val profileFlow = remember(repository, retry) {
        repository.pregnancyProfile.onEach { loading = false; error = null }.catch {
            loading = false; error = "Unable to load your profile. Retry before making changes."
        }
    }
    val profile by profileFlow.collectAsStateWithLifecycle(initialValue = null)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Profile & Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        onNavigateToAccount?.let { navigate ->
            Button(onClick = navigate, modifier = Modifier.fillMaxWidth()) { Text("Account & Sync") }
        }
        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { loading = true; error = null; retry++ }) { Text("Retry") }
        }
        val activeProfile = profile ?: PregnancyProfile(
            displayName = if (loading) "Loading profile…" else "No pregnancy profile",
            lmpDateIso = null, eddDateIso = null, isClinicianConfirmed = false,
            healthcareFacility = "", emergencyContactName = "", emergencyContactPhone = "", pregnancyNotes = ""
        )

        MonicaCareCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = LightBlush,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = RosePinkPrimary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeProfile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeProfile.healthcareFacility.isNotBlank()) {
                            Text(
                                text = "Facility: ${activeProfile.healthcareFacility}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "EDD: ${activeProfile.estimatedDeliveryDateFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = RosePinkPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onNavigateToProfileSetup,
                    enabled = !loading && error == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RosePinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Edit Pregnancy Profile", fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            text = "Preferences",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        settingsItems.forEach { item ->
            MonicaCareCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* Open Setting */ }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
