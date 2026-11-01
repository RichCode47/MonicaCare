@file:Suppress("NewApi")

package com.monicacare.app.feature.pregnancy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monicacare.app.core.calculator.PregnancyCalculator
import com.monicacare.app.core.designsystem.component.MonicaCareCard
import com.monicacare.app.core.designsystem.theme.LightBlush
import com.monicacare.app.core.designsystem.theme.RosePinkPrimary
import com.monicacare.app.core.designsystem.theme.TealContainer
import com.monicacare.app.core.designsystem.theme.TealSecondary
import com.monicacare.app.core.model.DatingMethod
import com.monicacare.app.core.model.PregnancyProfile
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PregnancyProfileScreen(
    viewModel: PregnancyProfileViewModel,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSavedSuccess) {
        if (uiState.isSavedSuccess) {
            onSaveSuccess()
        }
    }

    if (showDatePicker) {
        val initialIso = if (uiState.datingMethod == DatingMethod.LMP) uiState.lmpDateIso else uiState.eddDateIso
        val initialParsed = PregnancyCalculator.parseIsoDate(initialIso)
        val initialMillis = initialParsed?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            val isoString = selectedDate.toString()
                            if (uiState.datingMethod == DatingMethod.LMP) {
                                viewModel.onLmpDateChanged(isoString)
                            } else {
                                viewModel.onEddDateChanged(isoString)
                            }
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", color = RosePinkPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pregnancy Profile Setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = RosePinkPrimary)
            }
        } else if (uiState.loadError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(uiState.loadError!!)
                Button(onClick = viewModel::loadExistingProfile) { Text("Retry") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header description
                Text(
                    text = "Configure your pregnancy profile to receive personalized weekly updates and dynamic calculation of your due date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Display Name Field
                MonicaCareCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Mother's Preferred Name *",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.displayName,
                            onValueChange = { viewModel.onDisplayNameChanged(it) },
                            placeholder = { Text("e.g., Monica") },
                            isError = uiState.displayNameError != null,
                            supportingText = uiState.displayNameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RosePinkPrimary,
                                cursorColor = RosePinkPrimary
                            )
                        )
                    }
                }

                // Dating Method Selection
                MonicaCareCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pregnancy Dating Method *",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select how your pregnancy timeline is determined.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Method option 1: LMP
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onDatingMethodChanged(DatingMethod.LMP) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.datingMethod == DatingMethod.LMP,
                                onClick = { viewModel.onDatingMethodChanged(DatingMethod.LMP) },
                                colors = RadioButtonDefaults.colors(selectedColor = RosePinkPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Last Menstrual Period (LMP)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Calculate EDD based on the first day of your last period (280-day cycle).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Method option 2: Clinician EDD
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onDatingMethodChanged(DatingMethod.CLINICIAN_EDD) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = uiState.datingMethod == DatingMethod.CLINICIAN_EDD,
                                onClick = { viewModel.onDatingMethodChanged(DatingMethod.CLINICIAN_EDD) },
                                colors = RadioButtonDefaults.colors(selectedColor = RosePinkPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Clinician-Provided Due Date (EDD)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Use due date provided by healthcare provider or ultrasound.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Date Entry based on selected method
                        if (uiState.datingMethod == DatingMethod.LMP) {
                            OutlinedTextField(
                                value = uiState.lmpDateIso,
                                onValueChange = { viewModel.onLmpDateChanged(it) },
                                label = { Text("Last Menstrual Period Date (YYYY-MM-DD)") },
                                placeholder = { Text("YYYY-MM-DD e.g. 2024-11-01") },
                                isError = uiState.dateError != null,
                                supportingText = uiState.dateError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = "Pick date from calendar",
                                            tint = RosePinkPrimary
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RosePinkPrimary,
                                    cursorColor = RosePinkPrimary
                                )
                            )
                        } else {
                            OutlinedTextField(
                                value = uiState.eddDateIso,
                                onValueChange = { viewModel.onEddDateChanged(it) },
                                label = { Text("Estimated Delivery Date (YYYY-MM-DD)") },
                                placeholder = { Text("YYYY-MM-DD e.g. 2025-08-08") },
                                isError = uiState.dateError != null,
                                supportingText = uiState.dateError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = "Pick date from calendar",
                                            tint = RosePinkPrimary
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RosePinkPrimary,
                                    cursorColor = RosePinkPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Clinician Confirmation Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onClinicianConfirmedChanged(!uiState.isClinicianConfirmed) }
                        ) {
                            Checkbox(
                                checked = uiState.isClinicianConfirmed,
                                onCheckedChange = { viewModel.onClinicianConfirmedChanged(it) },
                                colors = CheckboxDefaults.colors(checkedColor = TealSecondary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Estimated delivery date confirmed by clinician/ultrasound",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live Timeline Preview Card
                val progress = uiState.calculatedProgress
                if (progress != null) {
                    MonicaCareCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = TealContainer.copy(alpha = 0.4f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Timeline Calculation Preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TealSecondary
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = LightBlush
                                ) {
                                    Text(
                                        text = "Trimester ${progress.trimester}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RosePinkPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Week ${progress.currentWeek}, Day ${progress.currentDay}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { PregnancyCalculator.safeProgress(progress.progressPercent) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RosePinkPrimary,
                                trackColor = LightBlush
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Estimated EDD: ${progress.estimatedDueDateFormatted}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${progress.daysRemaining} days left",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TealSecondary
                                )
                            }
                        }
                    }
                }

                // Clinical Disclaimer Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TealSecondary,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notice: These calculations provide estimated approximations based on standard 280-day gestational reference conventions. They do not replace professional healthcare assessment or clinical diagnosis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Additional Optional Info Card
                MonicaCareCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Additional Information (Optional)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.healthcareFacility,
                            onValueChange = { viewModel.onHealthcareFacilityChanged(it) },
                            label = { Text("Primary Healthcare Facility / Hospital") },
                            placeholder = { Text("e.g., Lusaka General Hospital") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.emergencyContactName,
                            onValueChange = { viewModel.onEmergencyContactNameChanged(it) },
                            label = { Text("Emergency Contact Name") },
                            placeholder = { Text("e.g., Partner or Family Member") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.emergencyContactPhone,
                            onValueChange = { viewModel.onEmergencyContactPhoneChanged(it) },
                            label = { Text("Emergency Contact Phone Number") },
                            placeholder = { Text("e.g., +260 97 1234567") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.pregnancyNotes,
                            onValueChange = { viewModel.onPregnancyNotesChanged(it) },
                            label = { Text("Pregnancy Notes") },
                            placeholder = { Text("Add any special care notes or clinical recommendations...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Save Button
                uiState.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(
                    enabled = !uiState.isSaving,
                    onClick = { viewModel.saveProfile() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RosePinkPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Pregnancy Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
