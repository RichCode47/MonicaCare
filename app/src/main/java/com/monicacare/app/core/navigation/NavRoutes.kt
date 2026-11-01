package com.monicacare.app.core.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object ProfileSetup : Screen("profile_setup")
    data object Home : Screen("home")
    data object Journey : Screen("journey")
    data object Appointments : Screen("appointments")
    data object HealthJournal : Screen("health_journal")
    data object Education : Screen("education")
    data object Emergency : Screen("emergency")
    data object Profile : Screen("profile")
}
