package com.monicacare.app.core.navigation

import androidx.navigation.NavHostController

/** Home survives Splash removal and anchors the saved top-level stacks. */
fun NavHostController.navigateToTopLevel(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
