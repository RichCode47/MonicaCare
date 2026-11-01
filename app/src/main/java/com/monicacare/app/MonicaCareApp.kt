package com.monicacare.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.monicacare.app.core.navigation.navigateToTopLevel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.monicacare.app.core.designsystem.theme.MonicaCareTheme
import com.monicacare.app.core.designsystem.theme.RosePinkPrimary
import com.monicacare.app.core.navigation.MonicaCareNavHost
import com.monicacare.app.core.navigation.Screen
import com.monicacare.app.core.navigation.bottomNavItems

@Composable
fun MonicaCareApp() {
    MonicaCareTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val showBottomBar = currentRoute in listOf(
            Screen.Home.route,
            Screen.Journey.route,
            Screen.Appointments.route,
            Screen.HealthJournal.route,
            Screen.Profile.route
        )

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                    ) {
                        bottomNavItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    navController.navigateToTopLevel(item.route)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                },
                                label = { Text(text = item.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = RosePinkPrimary,
                                    selectedTextColor = RosePinkPrimary,
                                    indicatorColor = com.monicacare.app.core.designsystem.theme.LightBlush
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            MonicaCareNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
