package com.monicacare.app.feature.pregnancy

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monicacare.app.core.data.DataStorePregnancyProfileRepository
import com.monicacare.app.core.designsystem.component.MonicaCareCard
import com.monicacare.app.core.designsystem.theme.LightBlush
import com.monicacare.app.core.designsystem.theme.RosePinkPrimary
import com.monicacare.app.core.designsystem.theme.TealContainer
import com.monicacare.app.core.designsystem.theme.TealSecondary
import com.monicacare.app.core.model.BabyDevelopment
import com.monicacare.app.core.model.PregnancyProgress
import com.monicacare.app.core.backend.WeekRecord
import com.monicacare.app.core.calculator.PregnancyCalculator
import com.monicacare.app.core.designsystem.component.CalendarDateRefreshEffect

@Composable
fun JourneyScreen(
    onNavigateToProfileSetup: () -> Unit,
    weekContent: List<WeekRecord> = emptyList(),
    viewModel: JourneyViewModel = run {
        val context = LocalContext.current
        val repository = remember(context) { DataStorePregnancyProfileRepository(context) }
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = JourneyViewModel.Factory(repository)
        )
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarDateRefreshEffect(viewModel::refreshCalculations)

    JourneyScreenContent(
        uiState = uiState,
        onNavigateToProfileSetup = onNavigateToProfileSetup,
        onRetry = { viewModel.loadJourney() },
        weekContent = weekContent
    )
}

@Composable
fun JourneyScreenContent(
    uiState: JourneyUiState,
    onNavigateToProfileSetup: () -> Unit,
    onRetry: () -> Unit = {},
    weekContent: List<WeekRecord> = emptyList()
) {
    val scrollState = rememberScrollState()

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = RosePinkPrimary)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Pregnancy Journey",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            if (uiState.errorMessage != null) {
                MonicaCareCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Notice",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.errorMessage,
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
            if (uiState.hasProfile) {
                weekContent.firstOrNull { it.week_number == uiState.pregnancyProgress?.currentWeek }?.let { week ->
                    MonicaCareCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(week.title, style = MaterialTheme.typography.titleLarge)
                            Text(week.baby_development)
                            if (week.maternal_changes.isNotBlank()) Text(week.maternal_changes)
                            if (week.educational_guidance.isNotBlank()) Text(week.educational_guidance)
                            Text("General educational guidance. Your clinician can advise on your individual pregnancy.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (uiState.pregnancyProgress != null) {
                    JourneyTimelineCard(
                        progress = uiState.pregnancyProgress,
                        onEditProfile = onNavigateToProfileSetup
                    )
                } else {
                    Text("Pregnancy dates are unavailable. Review your saved profile to restore the timeline.")
                }

                if (uiState.babyDevelopment != null) {
                    JourneyBabyCard(babyDevelopment = uiState.babyDevelopment)

                    Text(
                        text = "Week ${uiState.babyDevelopment.week} Milestones",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )

                    uiState.babyDevelopment.milestoneDetails.forEach { milestone ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = LightBlush.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    color = RosePinkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = milestone,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                } else {
                    Text("Baby development information is unavailable for this stage.")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onNavigateToProfileSetup,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Pregnancy Profile")
                }
            } else if (uiState.errorMessage == null) {
                NoProfileJourneyCard(onNavigateToProfileSetup = onNavigateToProfileSetup)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun JourneyTimelineCard(
    progress: PregnancyProgress,
    onEditProfile: () -> Unit
) {
    MonicaCareCard(modifier = Modifier.fillMaxWidth()) {
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
                        text = "Trimester ${progress.trimester} • Week ${progress.currentWeek}, Day ${progress.currentDay}",
                        style = MaterialTheme.typography.labelLarge,
                        color = RosePinkPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onEditProfile) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = TealSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val clampedPercent = PregnancyCalculator.safeProgress(progress.progressPercent)
            val displayPercent = (clampedPercent * 100).toInt()

            LinearProgressIndicator(
                progress = { clampedPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .semantics {
                        contentDescription = "Pregnancy progress: $displayPercent%"
                    },
                color = RosePinkPrimary,
                trackColor = LightBlush
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "EDD: ${progress.estimatedDueDateFormatted}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Progress: $displayPercent%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun JourneyBabyCard(babyDevelopment: BabyDevelopment) {
    MonicaCareCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = LightBlush.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = RosePinkPrimary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("👶", fontSize = 22.sp)
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Length: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "~ ${babyDevelopment.lengthCm} cm",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weight: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "~ ${babyDevelopment.weightGrams} g",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TealSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = babyDevelopment.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "* Illustrative developmental estimate based on gestational averages, not individual clinical measurements.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun NoProfileJourneyCard(onNavigateToProfileSetup: () -> Unit) {
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
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add pregnancy profile",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Set Up Your Pregnancy Journey",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Configure your pregnancy profile with your LMP or clinician-confirmed due date to view your personalized baby growth, developmental milestones, and trimester insights.",
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
