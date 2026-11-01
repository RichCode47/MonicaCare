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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monicacare.app.core.backend.BackendContainer
import com.monicacare.app.core.backend.LOCAL_OWNER
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
        val context = LocalContext.current
        val backend = remember(context) { BackendContainer.get(context) }
        val session by backend.auth.session.collectAsStateWithLifecycle()
        if (!session.initialized) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (session.initializationFailed) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Your saved account could not be read. Your data has not been reset.")
                        androidx.compose.material3.Button(onClick = backend.auth::retryInitialization) { Text("Retry") }
                    }
                } else CircularProgressIndicator()
            }
        } else key(session.userId ?: LOCAL_OWNER) {
            MonicaCareNavigation(backend)
        }
    }
}

@Composable
private fun MonicaCareNavigation(backend: BackendContainer) {
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
                modifier = Modifier.padding(innerPadding),
                backend = backend
            )
        }
}
