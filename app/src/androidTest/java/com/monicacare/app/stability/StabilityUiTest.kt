package com.monicacare.app.stability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.monicacare.app.core.calculator.PregnancyCalculator
import com.monicacare.app.core.data.OnboardingPreferences
import com.monicacare.app.core.data.PregnancyProfileRepository
import com.monicacare.app.core.designsystem.theme.MonicaCareTheme
import com.monicacare.app.core.model.PregnancyProfile
import com.monicacare.app.core.navigation.MonicaCareNavHost
import com.monicacare.app.core.navigation.Screen
import com.monicacare.app.core.navigation.navigateToTopLevel
import com.monicacare.app.feature.home.HomeDashboardContent
import com.monicacare.app.feature.home.HomeUiState
import com.monicacare.app.feature.pregnancy.JourneyScreenContent
import com.monicacare.app.feature.pregnancy.JourneyUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

/** Run with -PmonicacareIsolatedQa=true. No production repositories are used. */
class StabilityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val record = PregnancyProfile(displayName = "Isolated QA", lmpDateIso = LocalDate.now().minusDays(140).toString())
    private val progress get() = PregnancyCalculator.calculateProgress(record, LocalDate.now())!!

    private class Records : PregnancyProfileRepository {
        val data = MutableStateFlow<PregnancyProfile?>(null)
        override val pregnancyProfile = data
        override suspend fun savePregnancyProfile(profile: PregnancyProfile) { data.value = profile }
        override suspend fun clearProfile() { data.value = null }
    }

    @Composable private fun Home(state: HomeUiState, retry: () -> Unit = {}, journey: () -> Unit = {}, health: () -> Unit = {}) {
        HomeDashboardContent(state, {}, journey, {}, health, {}, {}, retry)
    }

    @Test fun homeLoadingEmptyAndFailureNeverOverlap() {
        val state = mutableStateOf(HomeUiState())
        var retries = 0
        compose.setContent { MonicaCareTheme { Home(state.value, { retries++ }) } }
        compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertExists()
        compose.onNodeWithText("Set Up Pregnancy Profile").assertDoesNotExist()
        compose.runOnIdle { state.value = HomeUiState(isLoading = false) }
        compose.onNodeWithText("Set Up Pregnancy Profile").assertExists()
        compose.runOnIdle { state.value = HomeUiState(isLoading = false, errorMessage = "Read unavailable") }
        compose.onNodeWithText("Read unavailable").assertIsDisplayed()
        compose.onNodeWithText("Set Up Pregnancy Profile").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithContentDescription("Notifications").assertDoesNotExist()
    }

    @Test fun journeyMissingDevelopmentShowsTimelineAndFailureNeverShowsSetup() {
        val state = mutableStateOf(JourneyUiState(isLoading = false, hasProfile = true,
            pregnancyProfile = record, pregnancyProgress = progress, babyDevelopment = null))
        compose.setContent { MonicaCareTheme { JourneyScreenContent(state.value, {}) } }
        compose.onNodeWithText("Baby development information is unavailable for this stage.").assertExists()
        compose.onNodeWithText("EDD: " + progress.estimatedDueDateFormatted).assertExists()
        compose.onNodeWithText("Set Up Pregnancy Profile").assertDoesNotExist()
        compose.runOnIdle { state.value = JourneyUiState(isLoading = false, errorMessage = "Timeline unavailable") }
        compose.onNodeWithText("Timeline unavailable").assertIsDisplayed()
        compose.onNodeWithText("Set Up Pregnancy Profile").assertDoesNotExist()
        compose.runOnIdle { state.value = JourneyUiState(isLoading = false) }
        compose.onNodeWithText("Set Up Pregnancy Profile").assertExists()
    }

    private fun assertTextFits(text: String) {
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Missing text layout: $text", layouts.isNotEmpty())
        val layout = layouts.single()
        // The paragraph may keep the parent's maximum width after Text shrinks to its
        // intrinsic width. Check rendered line bounds rather than that allocation width.
        for (line in 0 until layout.lineCount) {
            assertTrue("Text clipped horizontally: $text; scale=${layout.layoutInput.density.fontScale}",
                layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("Text clipped vertically: $text", layout.getLineBottom(line) <= layout.size.height + 1f)
            assertFalse("Text ellipsized: $text", layout.isLineEllipsized(line))
        }
        assertEquals("Text truncated: $text", text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    @Test fun dashboardLargeFontsKeepBadgesJourneyAndAllJournalActionsReachable() {
        var journeyClicks = 0
        var journalClicks = 0
        val font = mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font.floatValue)) {
                MonicaCareTheme { Home(HomeUiState(isLoading = false, hasProfile = true,
                    pregnancyProfile = record, pregnancyProgress = progress,
                    babyDevelopment = com.monicacare.app.core.model.BabyDevelopmentRepository.getForWeek(20)),
                    journey = { journeyClicks++ }, health = { journalClicks++ }) }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { font.floatValue = scale }
            compose.onNodeWithText("Trimester 2").performScrollTo().assertIsDisplayed()
            assertTextFits("Trimester 2")
            compose.onNodeWithText("View Journey").performScrollTo().assertIsDisplayed().performClick()
            assertTextFits("View Journey")
            compose.onNodeWithText("Illustration: Banana").performScrollTo().assertIsDisplayed()
            assertTextFits("Illustration: Banana")
            compose.onNodeWithText("~ 25.6 cm").performScrollTo().assertIsDisplayed()
            assertTextFits("~ 25.6 cm")
            for (label in listOf("Blood Pressure", "Weight", "Symptoms", "Labs")) {
                compose.onNode(hasText(label) and hasClickAction()).performScrollTo().assertIsDisplayed().performClick()
                if (label != "Weight") assertTextFits(label)
            }
        }
        compose.runOnIdle {
            assertEquals(2, journeyClicks)
            assertEquals(8, journalClicks)
        }
    }

    @Test fun splashWaitsForInitialPreferencesBeforeChoosingRoute() {
        val read = CompletableDeferred<Boolean>()
        val prefs = object : OnboardingPreferences {
            override val isOnboardingCompleted = flow { emit(read.await()) }
            override suspend fun setOnboardingCompleted(completed: Boolean) {}
        }
        lateinit var nav: NavHostController
        compose.setContent { MonicaCareTheme {
            nav = rememberNavController()
            MonicaCareNavHost(nav, preferences = prefs, profileRepository = Records())
        } }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(Screen.Splash.route, nav.currentDestination!!.route) }
        read.complete(true)
        compose.waitUntil(5000) { nav.currentDestination?.route == Screen.Home.route }
        compose.runOnIdle { assertNull(nav.previousBackStackEntry) }
    }

    @Test fun onboardingWaitsForPersistenceAndRecoversFromFailure() {
        val save = CompletableDeferred<Unit>()
        var fail = true
        var saves = 0
        val prefs = object : OnboardingPreferences {
            override val isOnboardingCompleted = MutableStateFlow(false)
            override suspend fun setOnboardingCompleted(completed: Boolean) {
                saves++
                save.await()
                if (fail) throw java.io.IOException("Isolated failure")
            }
        }
        lateinit var nav: NavHostController
        val records = Records()
        compose.setContent { MonicaCareTheme {
            nav = rememberNavController()
            MonicaCareNavHost(nav, startDestination = Screen.Onboarding.route, preferences = prefs, profileRepository = records)
        } }
        compose.onNodeWithText("Skip").performClick().performClick()
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals(Screen.Onboarding.route, nav.currentDestination!!.route)
        }
        save.complete(Unit)
        compose.onNodeWithText("Unable to continue").assertExists()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("Retry").performClick()
        compose.waitUntil(5000) { nav.currentDestination?.route == Screen.Home.route }
        compose.runOnIdle { assertEquals(2, saves); assertNull(nav.previousBackStackEntry) }
    }

    @Test fun preferenceReadRetryDoesNotCompleteOnboarding() {
        var failRead = true
        var saves = 0
        val prefs = object : OnboardingPreferences {
            override val isOnboardingCompleted = flow {
                emit(false)
                if (failRead) throw java.io.IOException("Read failed after initial preference")
            }
            override suspend fun setOnboardingCompleted(completed: Boolean) { saves++ }
        }
        val records = Records()
        lateinit var nav: NavHostController
        compose.setContent { MonicaCareTheme {
            nav = rememberNavController()
            MonicaCareNavHost(nav, startDestination = Screen.Onboarding.route, preferences = prefs, profileRepository = records)
        } }
        compose.onNodeWithText("Unable to continue").assertExists()
        compose.runOnIdle { failRead = false }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Skip").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves); assertEquals(Screen.Onboarding.route, nav.currentDestination!!.route) }
    }

    @Test fun savedNavigationStateRestoresSelectedTab() {
        val records = Records().apply { data.value = record }
        val prefs = object : OnboardingPreferences {
            override val isOnboardingCompleted = MutableStateFlow(true)
            override suspend fun setOnboardingCompleted(completed: Boolean) {}
        }
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        lateinit var nav: NavHostController
        restoration.setContent { MonicaCareTheme {
            nav = rememberNavController()
            MonicaCareNavHost(nav, startDestination = Screen.Home.route, preferences = prefs, profileRepository = records)
        } }
        compose.runOnIdle { nav.navigateToTopLevel(Screen.Journey.route) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(Screen.Journey.route, nav.currentDestination!!.route) }
        compose.runOnIdle { nav.navigateToTopLevel(Screen.Home.route) }
        compose.waitUntil(5000) { nav.currentDestination?.route == Screen.Home.route }
    }

    @Test fun resumingScreenRefreshesCalendarCalculations() {
        val records = Records().apply { data.value = record.copy(lmpDateIso = "2025-01-01") }
        var date = LocalDate.of(2025, 1, 7)
        lateinit var vm: com.monicacare.app.feature.home.HomeViewModel
        compose.runOnUiThread { vm = com.monicacare.app.feature.home.HomeViewModel(records, { date }) }
        compose.setContent { MonicaCareTheme {
            com.monicacare.app.feature.home.HomeDashboardScreen({}, {}, {}, {}, {}, {}, vm)
        } }
        compose.onNodeWithText("Week 0, Day 6").assertExists()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        date = LocalDate.of(2025, 1, 8)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("Week 1, Day 0").assertExists()
    }

    @Test fun tabsRestoreStacksBackToHomeAndProfileSaveUpdatesBothScreens() {
        val records = Records().apply { data.value = record }
        val prefs = object : OnboardingPreferences {
            override val isOnboardingCompleted = MutableStateFlow(true)
            override suspend fun setOnboardingCompleted(completed: Boolean) {}
        }
        lateinit var nav: NavHostController
        compose.setContent { MonicaCareTheme {
            nav = rememberNavController()
            MonicaCareNavHost(nav, startDestination = Screen.Home.route, preferences = prefs, profileRepository = records)
        } }
        val tabs = listOf(Screen.Journey, Screen.Appointments, Screen.HealthJournal, Screen.Profile, Screen.Home)
        repeat(3) {
            for (screen in tabs) {
                compose.runOnIdle { nav.navigateToTopLevel(screen.route) }
                compose.waitUntil(5000) { nav.currentDestination?.route == screen.route }
            }
        }
        compose.runOnIdle { nav.navigateToTopLevel(Screen.Journey.route) }
        compose.waitUntil(5000) { nav.currentDestination?.route == Screen.Journey.route }
        compose.runOnIdle { assertTrue(nav.popBackStack()); assertEquals(Screen.Home.route, nav.currentDestination!!.route) }
        compose.runOnIdle { nav.navigate(Screen.ProfileSetup.route) }
        compose.onNodeWithText("Isolated QA").performScrollTo().performTextReplacement("Updated isolated QA")
        compose.onNodeWithText("Save Pregnancy Profile").performScrollTo().performClick()
        compose.waitUntil(5000) { nav.currentDestination?.route == Screen.Home.route }
        compose.onNodeWithText("Hello, Updated isolated QA 👋").assertExists()
        compose.runOnIdle { nav.navigateToTopLevel(Screen.Journey.route) }
        compose.onNodeWithText("EDD: " + progress.estimatedDueDateFormatted).assertExists()
        compose.runOnIdle { assertEquals("Updated isolated QA", records.data.value!!.displayName) }
    }
}
