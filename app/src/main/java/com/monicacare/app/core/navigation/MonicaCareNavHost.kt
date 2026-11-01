package com.monicacare.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.monicacare.app.core.backend.*
import com.monicacare.app.core.model.AntenatalAppointment
import com.monicacare.app.core.model.AppointmentStatus
import com.monicacare.app.feature.account.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*

import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.monicacare.app.core.data.DataStorePregnancyProfileRepository
import com.monicacare.app.core.data.UserPreferencesRepository
import com.monicacare.app.core.data.OnboardingPreferences
import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.feature.home.HomeViewModel
import com.monicacare.app.feature.appointments.AppointmentsScreen
import com.monicacare.app.feature.education.EducationScreen
import com.monicacare.app.feature.emergency.EmergencyScreen
import com.monicacare.app.feature.health.HealthJournalScreen
import com.monicacare.app.feature.home.HomeDashboardScreen
import com.monicacare.app.feature.onboarding.OnboardingScreen
import com.monicacare.app.feature.pregnancy.JourneyScreen
import com.monicacare.app.feature.pregnancy.JourneyViewModel
import com.monicacare.app.feature.pregnancy.PregnancyProfileScreen
import com.monicacare.app.feature.pregnancy.PregnancyProfileViewModel
import com.monicacare.app.feature.profile.ProfileScreen
import com.monicacare.app.feature.splash.SplashScreen
import com.monicacare.app.feature.onboarding.OnboardingViewModel

@Composable
fun MonicaCareNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Splash.route,
    preferences: OnboardingPreferences? = null,
    profileRepository: PregnancyProfileRepository? = null,
    backend: BackendContainer? = null
) {
    val context = LocalContext.current
    val userPreferencesRepository = preferences ?: remember(context) { UserPreferencesRepository(context) }
    val owner = backend?.records?.owner() ?: LOCAL_OWNER
    val pregnancyRepository = profileRepository ?: remember(context, backend, owner) {
        backend?.pregnancyRepository(owner) ?: DataStorePregnancyProfileRepository(context)
    }
    val onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = OnboardingViewModel.Factory(userPreferencesRepository)
    )
    val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    onboardingState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Unable to continue") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onboardingViewModel::retry) { Text("Retry") }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            var splashFinished by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(splashFinished, onboardingState.completed) {
                val completed = onboardingState.completed
                if (splashFinished && completed != null) {
                    navController.navigate(if (completed) Screen.Home.route else Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            }
            SplashScreen(onSplashFinished = { splashFinished = true })
        }

        composable(Screen.Onboarding.route) {
            LaunchedEffect(onboardingState.completionSaved) {
                if (onboardingState.completionSaved) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            }
            OnboardingScreen(onOnboardingComplete = onboardingViewModel::completeOnboarding)
        }

        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = if (backend == null) HomeViewModel.Factory(pregnancyRepository) else HomeViewModel.Factory(
                    pregnancyRepository,
                    developmentProvider = backend::developmentForWeek,
                    appointments = remember(backend, owner) {
                        backend.records.observe(owner, PrivateTable.APPOINTMENT).map { rows ->
                            rows.map { row ->
                                val value = BackendJson.decodeFromJsonElement<AppointmentRecord>(row.payload)
                                AntenatalAppointment(value.id, value.title, value.provider_name, value.facility_name,
                                    value.appointment_date, value.appointment_time,
                                    AppointmentStatus.valueOf(value.status.uppercase()), value.notes)
                            }
                        }
                    }
                )
            )
            HomeDashboardScreen(
                viewModel = homeViewModel,
                onNavigateToProfileSetup = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToJourney = { navController.navigateToTopLevel(Screen.Journey.route) },
                onNavigateToAppointments = { navController.navigateToTopLevel(Screen.Appointments.route) },
                onNavigateToHealthJournal = { navController.navigateToTopLevel(Screen.HealthJournal.route) },
                onNavigateToEmergency = { navController.navigate(Screen.Emergency.route) },
                onNavigateToEducation = { navController.navigate(Screen.Education.route) }
            )
        }

        composable(Screen.ProfileSetup.route) {
            val profileViewModel: PregnancyProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = PregnancyProfileViewModel.Factory(pregnancyRepository)
            )
            PregnancyProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        composable(Screen.Journey.route) {
            val journeyViewModel: JourneyViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = if (backend == null) JourneyViewModel.Factory(pregnancyRepository)
                    else JourneyViewModel.Factory(pregnancyRepository, developmentProvider = backend::developmentForWeek)
            )
            JourneyScreen(
                viewModel = journeyViewModel,
                weekContent = backend?.cached?.collectAsStateWithLifecycle()?.value?.weeks.orEmpty(),
                onNavigateToProfileSetup = {
                    navController.navigate(Screen.ProfileSetup.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Appointments.route) {
            if (backend == null) AppointmentsScreen()
            else ManagedRecordsScreen(backendViewModel(backend, owner), journal = false)
        }

        composable(Screen.HealthJournal.route) {
            if (backend == null) HealthJournalScreen()
            else ManagedRecordsScreen(backendViewModel(backend, owner), journal = true)
        }

        composable(Screen.Education.route) {
            if (backend == null) EducationScreen()
            else ManagedEducationScreen(backendViewModel(backend, owner))
        }

        composable(Screen.Emergency.route) {
            EmergencyScreen(
                profileRepository = pregnancyRepository,
                contacts = backend?.cached?.collectAsStateWithLifecycle()?.value?.emergencyContacts.orEmpty()
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateToProfileSetup = { navController.navigate(Screen.ProfileSetup.route) },
                profileRepository = pregnancyRepository,
                onNavigateToAccount = if (backend != null) ({ navController.navigate(Screen.Account.route) }) else null
            )
        }
        composable(Screen.Account.route) {
            if (backend != null) AccountScreen(backendViewModel(backend, owner)) { navController.popBackStack() }
        }

    }
}

@Composable
private fun backendViewModel(backend: BackendContainer, owner: String): BackendViewModel =
    androidx.lifecycle.viewmodel.compose.viewModel(factory = BackendViewModel.Factory(backend, owner))
