package com.monicacare.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monicacare.app.core.data.DataStorePregnancyProfileRepository
import com.monicacare.app.core.designsystem.component.MonicaCareCard
import com.monicacare.app.core.designsystem.theme.AccentGold
import com.monicacare.app.core.designsystem.theme.EmergencyRed
import com.monicacare.app.core.designsystem.theme.EmergencyRedContainer
import com.monicacare.app.core.designsystem.theme.LightBlush
import com.monicacare.app.core.designsystem.theme.RosePinkPrimary
import com.monicacare.app.core.designsystem.theme.TealContainer
import com.monicacare.app.core.designsystem.theme.TealSecondary
import com.monicacare.app.core.model.AntenatalAppointment
import com.monicacare.app.core.model.BabyDevelopment
import com.monicacare.app.core.model.DailyPregnancyTip
import com.monicacare.app.core.model.PregnancyProfile
import com.monicacare.app.core.model.PregnancyProgress
import com.monicacare.app.core.calculator.PregnancyCalculator
import com.monicacare.app.core.designsystem.component.CalendarDateRefreshEffect

@Composable
fun HomeDashboardScreen(
    onNavigateToProfileSetup: () -> Unit,
    onNavigateToJourney: () -> Unit,
    onNavigateToAppointments: () -> Unit,
    onNavigateToHealthJournal: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToEducation: () -> Unit,
    viewModel: HomeViewModel = run {
        val context = LocalContext.current
        val repository = remember(context) { DataStorePregnancyProfileRepository(context) }
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = HomeViewModel.Factory(repository)
        )
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarDateRefreshEffect(viewModel::refreshCalculations)

    HomeDashboardContent(
        uiState = uiState,
        onNavigateToProfileSetup = onNavigateToProfileSetup,
        onNavigateToJourney = onNavigateToJourney,
        onNavigateToAppointments = onNavigateToAppointments,
        onNavigateToHealthJournal = onNavigateToHealthJournal,
        onNavigateToEmergency = onNavigateToEmergency,
        onNavigateToEducation = onNavigateToEducation,
        onRetryLoad = { viewModel.loadProfile() }
    )
}

@Composable
fun HomeDashboardContent(
    uiState: HomeUiState,
    onNavigateToProfileSetup: () -> Unit,
    onNavigateToJourney: () -> Unit,
    onNavigateToAppointments: () -> Unit,
    onNavigateToHealthJournal: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToEducation: () -> Unit,
    onRetryLoad: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = RosePinkPrimary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        HomeHeader(
            displayName = uiState.displayName.ifBlank { "Mama" },
            onEditProfile = if (uiState.errorMessage == null || uiState.hasProfile) onNavigateToProfileSetup else null
        )

        // Emergency Guidance Banner
        EmergencyQuickBanner(onNavigateToEmergency = onNavigateToEmergency)

        // Error message if any
        if (uiState.errorMessage != null) {
            ErrorCard(
                message = uiState.errorMessage,
                onRetry = onRetryLoad
            )
        }

        // Pregnancy Progress Card or Empty State
        if (uiState.hasProfile && uiState.pregnancyProgress != null && uiState.pregnancyProfile != null) {
            PregnancyProgressCard(
                progress = uiState.pregnancyProgress,
                profile = uiState.pregnancyProfile,
                onNavigateToJourney = onNavigateToJourney,
                onEditProfile = onNavigateToProfileSetup
            )
        } else if (uiState.hasProfile) {
            Text("Pregnancy dates are unavailable. Review your saved profile to restore the timeline.")
            OutlinedButton(onClick = onNavigateToProfileSetup) { Text("Review Pregnancy Profile") }
        } else if (uiState.errorMessage == null) {
            NoProfileSetupCard(onNavigateToProfileSetup = onNavigateToProfileSetup)
        }

        // Weekly Baby Development Card
        uiState.babyDevelopment?.let { babyDev ->
            BabyDevelopmentCard(
                babyDevelopment = babyDev,
                onNavigateToJourney = onNavigateToJourney
            )
        }

        if (uiState.hasProfile && uiState.babyDevelopment == null) {
            Text("Baby development information is unavailable for this stage.")
        }

        // Upcoming Appointment Card
        uiState.upcomingAppointment?.let { appointment ->
            UpcomingAppointmentCard(
                appointment = appointment,
                onNavigateToAppointments = onNavigateToAppointments
            )
        }

        // Maternal Health Journal Quick Log Shortcut
        HealthJournalShortcutSection(onNavigateToHealthJournal = onNavigateToHealthJournal)

        // Daily Pregnancy Tip
        uiState.dailyTip?.let { tip ->
            DailyTipCard(
                tip = tip,
                onNavigateToEducation = onNavigateToEducation
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HomeHeader(
    displayName: String,
    onEditProfile: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Hello, $displayName 👋",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Caring for Mother and Baby",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = CircleShape,
                color = LightBlush,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(onClick = { onEditProfile?.invoke() }, enabled = onEditProfile != null) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = RosePinkPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmergencyQuickBanner(onNavigateToEmergency: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigateToEmergency() },
        color = EmergencyRedContainer
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = EmergencyRed,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Emergency Guidance & Urgent Symptoms",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed
                )
                Text(
                    text = "Recognise warning signs and find immediate care advice",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = EmergencyRed
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    MonicaCareCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Notice",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun NoProfileSetupCard(onNavigateToProfileSetup: () -> Unit) {
    MonicaCareCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 3.dp,
        containerColor = LightBlush.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = RosePinkPrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Set Up Your Pregnancy Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter your last menstrual period or estimated due date to get custom week-by-week calculations and milestones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNavigateToProfileSetup,
                colors = ButtonDefaults.buttonColors(containerColor = RosePinkPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Up Pregnancy Profile", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PregnancyProgressCard(
    progress: PregnancyProgress,
    profile: PregnancyProfile,
    onNavigateToJourney: () -> Unit,
    onEditProfile: () -> Unit
) {
    MonicaCareCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 3.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = LightBlush
                ) {
                    Text(
                        text = "Trimester ${progress.trimester}",
                        style = MaterialTheme.typography.labelLarge,
                        color = RosePinkPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (progress.isOverdue) MaterialTheme.colorScheme.errorContainer else TealContainer
                ) {
                    Text(
                        text = if (progress.isOverdue) "Overdue (${progress.daysOverdue} days)" else "${progress.daysRemaining} Days Left",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (progress.isOverdue) MaterialTheme.colorScheme.onErrorContainer else TealSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Week ${progress.currentWeek}, Day ${progress.currentDay}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onEditProfile) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = TealSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { PregnancyCalculator.safeProgress(progress.progressPercent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = RosePinkPrimary,
                trackColor = LightBlush
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = "Estimated Due Date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = progress.estimatedDueDateFormatted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (profile.isClinicianConfirmed && profile.datingMethod == com.monicacare.app.core.model.DatingMethod.CLINICIAN_EDD) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Clinician Confirmed",
                                tint = TealSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToJourney,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("View Journey")
                }
            }
        }
    }
}

@Composable
private fun BabyDevelopmentCard(
    babyDevelopment: BabyDevelopment,
    onNavigateToJourney: () -> Unit
) {
    MonicaCareCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LightBlush.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = RosePinkPrimary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("👶", fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Development Illustration",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Illustration: ${babyDevelopment.fruitComparison}",
                        style = MaterialTheme.typography.titleLarge,
                        color = RosePinkPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoStatChip(label = "Length", value = "~ ${babyDevelopment.lengthCm} cm")
                InfoStatChip(label = "Weight", value = "~ ${babyDevelopment.weightGrams} g")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = babyDevelopment.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "General developmental estimates, not measurements of your baby.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onNavigateToJourney,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RosePinkPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Explore Week ${babyDevelopment.week} Milestones")
            }
        }
    }
}

@Composable
private fun InfoStatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TealSecondary
            )
        }
    }
}

@Composable
private fun UpcomingAppointmentCard(
    appointment: AntenatalAppointment,
    onNavigateToAppointments: () -> Unit
) {
    MonicaCareCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = TealSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Upcoming ANC Visit",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TealContainer
                ) {
                    Text(
                        text = "Scheduled",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = appointment.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "${appointment.appointmentDate} • ${appointment.appointmentTime}",
                style = MaterialTheme.typography.bodyMedium,
                color = TealSecondary,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "${appointment.providerName} • ${appointment.facilityName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onNavigateToAppointments,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("View All Appointments")
                }
            }
        }
    }
}

@Composable
private fun HealthJournalShortcutSection(onNavigateToHealthJournal: () -> Unit) {
    Column {
        Text(
            text = "Maternal Health Journal",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Quick entry for today's wellness check",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickLogButton(
                icon = Icons.Default.Favorite,
                label = "Blood Pressure",
                color = RosePinkPrimary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToHealthJournal
            )
            QuickLogButton(
                icon = Icons.Default.MonitorWeight,
                label = "Weight",
                color = TealSecondary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToHealthJournal
            )
            QuickLogButton(
                icon = Icons.Default.Speed,
                label = "Symptoms",
                color = AccentGold,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToHealthJournal
            )
            QuickLogButton(
                icon = Icons.Default.Science,
                label = "Labs",
                color = TealSecondary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToHealthJournal
            )
        }
    }
}

@Composable
private fun QuickLogButton(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DailyTipCard(
    tip: DailyPregnancyTip,
    onNavigateToEducation: () -> Unit
) {
    MonicaCareCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = AccentGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily Pregnancy Tip",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LightBlush
                ) {
                    Text(
                        text = tip.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = RosePinkPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tip.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tip.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Explore Education Library →",
                style = MaterialTheme.typography.labelLarge,
                color = TealSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNavigateToEducation() }
            )
        }
    }
}
