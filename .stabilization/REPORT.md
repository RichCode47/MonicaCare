# MonicaCare stabilization report — 1 November 2026

## A. Baseline

The initial Android Studio build of `:app:assembleDebug :app:testDebugUnitTest` succeeded. All 33 existing unit tests were preserved and continued to pass after the repairs.

The current project contains one Android app module (`:app`), rather than a separate shared KMP module. The requested `Screen` declaration is in `core/navigation/NavRoutes.kt`; the DataStore profile implementation is in `core/data/PregnancyProfileRepository.kt`.

Git resolves to `C:/Users/gaspe`, an unrelated parent repository on an unborn master branch. MonicaCare is untracked there. No files were staged, no commits were created, and no parent Git state was changed. To avoid including unrelated personal files, original IDE-buffer contents of every edited existing file were saved under `.stabilization/baseline/`, preserving relative paths with a `.txt` suffix. These copies provide a per-file rollback point. Existing source and unsaved IDE contents were read through Android Studio tools before modification.

## B. Confirmed bugs and root causes

| Location / function | Root cause |
| --- | --- |
| HomeDashboardScreen.kt / HomeDashboardContent | The setup fallback ran independently of the error card. A failed read or an existing profile with uncalculable dates could appear to be an absent profile. |
| JourneyScreen.kt / JourneyScreenContent | Timeline rendering required both progress and development data; missing development incorrectly selected the setup card. Errors also hid previously loaded progress. |
| HomeViewModel.kt / loadProfile, refreshCalculations | Missing development was replaced by a sample week-24 baby. |
| MonicaCareNavHost.kt / onboarding and Splash destinations | Navigation ran before preference persistence completed. Splash used an initial false value before the first repository emission. There was no persistence error handling or duplicate-completion guard. |
| MonicaCareApp.kt / bottom-navigation callback | popUpTo targeted the graph start destination, Splash, after Splash had been removed. This could fail to pop tab entries and accumulate duplicate destinations. |
| HomeDashboardScreen.kt / PregnancyProgressCard | The displayed due date came from the stored EDD field rather than the calculator's selected dating method, so inconsistent stored fields could disagree with the timeline. |
| HomeDashboardScreen.kt / HomeHeader, pregnancy and journal cards | Unweighted header text competed with controls; badges and due-date actions used single rows; four single-line journal shortcuts could clip. The notification button had an empty action, with no notifications destination available. |
| Home/Journey screen wrappers | refreshCalculations existed but was never wired to resume or calendar changes. |
| PregnancyProfileViewModel.kt / loadExistingProfile, saveProfile | Repository exceptions were unhandled. Loading could remain stuck, write failures could escape, and duplicate saves were not guarded. |
| PregnancyCalculator.kt and progress indicators | Home passed progress through unchecked, and Journey only clamped values, leaving NaN unhandled. Extreme valid ISO dates could overflow date arithmetic; future inferred LMP dates produced a misleading zero-week timeline. |

## C. Fixes applied

- Kept the existing UI states and MVVM structure. Loading, confirmed absence, available content, invalid stored dates, and repository errors now render separately.
- Retry cancels the previous load job and re-observes the repository. Previously loaded profiles and progress remain visible with the error. Failed first reads do not offer setup or an enabled Home profile-edit action.
- Journey renders progress independently of development content and displays an unavailable-content message where needed. Home no longer substitutes sample development.
- Added a small onboarding ViewModel using the existing preference repository. Completion waits for a successful write, blocks duplicate requests, and exposes Retry. Splash waits for both its animation and the first preference result. Read Retry remains a read even after a prior preference emission.
- Anchored top-level navigation on Home, preserving saveState, restoreState and launchSingleTop. Home shortcuts use the same navigation helper.
- Added a shared resume/calendar-change effect, registered only while resumed and disposed on pause. It refreshes on date, clock and timezone changes without polling.
- Kept the established 280-day dating rule, selected clinician-EDD precedence, trimester boundaries, due-date and overdue semantics. Added guarded invalid-date arithmetic and a shared finite progress sanitizer used by Home, Journey and the profile preview.
- Home displays the calculator's due date. The clinician confirmation marker requires clinician-EDD dating.
- Used wrapping rows for badges, due-date controls and development statistics; gave header text bounded space; arranged the four journal actions in two wrapping columns and removed their one-line restriction. Colors, typography and card hierarchy remain in place.
- Hid the unfinished notification control. Added clear illustrative/general-estimate labels to baby cards and retained Journey's educational disclaimer.
- Profile read failures now block the editor until Retry succeeds. Save failures preserve drafts, successful saves alone trigger navigation, and duplicate save actions are blocked.
- Made repositories accept an injected DataStore for isolated tests. Existing context constructors, DataStore filenames, preference keys and serialized enum values remain unchanged.
- Added an explicit `-PmonicacareIsolatedQa=true` build option. Only that opt-in installs a separate `com.monicacare.app.qa` copy; normal builds remain `com.monicacare.app`. No dependencies were upgraded.

## D. Files modified or added

Existing files modified:

- `app/build.gradle.kts`
- `app/src/main/java/com/monicacare/app/MonicaCareApp.kt`
- `app/src/main/java/com/monicacare/app/core/navigation/MonicaCareNavHost.kt`
- `app/src/main/java/com/monicacare/app/core/calculator/PregnancyCalculator.kt`
- `app/src/main/java/com/monicacare/app/core/data/PregnancyProfileRepository.kt`
- `app/src/main/java/com/monicacare/app/core/data/UserPreferencesRepository.kt`
- `app/src/main/java/com/monicacare/app/feature/home/HomeDashboardScreen.kt`
- `app/src/main/java/com/monicacare/app/feature/home/HomeViewModel.kt`
- `app/src/main/java/com/monicacare/app/feature/pregnancy/JourneyScreen.kt`
- `app/src/main/java/com/monicacare/app/feature/pregnancy/JourneyViewModel.kt`
- `app/src/main/java/com/monicacare/app/feature/pregnancy/PregnancyProfileViewModel.kt`
- `app/src/main/java/com/monicacare/app/feature/pregnancy/PregnancyProfileScreen.kt`

New source and tests:

- `app/src/main/java/com/monicacare/app/core/navigation/TopLevelNavigation.kt`
- `app/src/main/java/com/monicacare/app/core/designsystem/component/CalendarDateRefreshEffect.kt`
- `app/src/main/java/com/monicacare/app/feature/onboarding/OnboardingViewModel.kt`
- `app/src/test/java/com/monicacare/app/stability/StabilityRegressionTest.kt`
- `app/src/test/java/com/monicacare/app/stability/PersistenceRegressionTest.kt`
- `app/src/androidTest/java/com/monicacare/app/stability/StabilityUiTest.kt`

Supporting artifacts are confined to `.stabilization/`: baseline notes, original-file copies, this report, and verification evidence. Routes, bottomNavItems, the design system and development-data catalog were inspected and retained.

## E. Executed tests and results

| Command run through Android Studio | Actual final result |
| --- | --- |
| `:app:assembleDebug :app:testDebugUnitTest` | Successful normal debug build; **51 passed, 0 skipped, 0 failed** |
| `:app:connectedDebugAndroidTest -PmonicacareIsolatedQa=true` | **9 passed, 0 failed** on emulator-5554, Android 17 |
| `:app:installDebug -PmonicacareIsolatedQa=true` | Successful isolated QA installation and launch |

The 18 added unit tests cover state transitions, read failures and real resubscription on Retry, last-known content retention, missing development, invalid stored dates, reactive updates, calendar refresh, save failure/draft retention, duplicate actions, delayed onboarding IO, read-versus-save Retry, clinician dating precedence, leap/invalid/extreme dates, overdue results, and non-finite/out-of-range progress. Existing boundary and leap-year tests still pass.

Temporary-file tests verify save, update, reopen, existing preference-key compatibility, preservation of unrelated keys, onboarding persistence, and corruption propagation without replacing the file with empty data.

The nine device tests exercise delayed Splash preferences, onboarding save ordering/failure/Retry, read Retry after an initial value, Home loading/empty/error states, Journey missing development/error/empty states, repeated tab switching, Back to Home, saved navigation restoration, isolated profile editing/saving and Home/Journey updates, resume calculation refresh, and dashboard callbacks at 1x and 2x font scale. Text checks inspect rendered line bounds and truncation, including the trimester badge, Journey button, development illustration/statistic and journal labels.

Earlier runs included a corrected test matcher compilation error, a low-memory termination of the QA process, and an overly broad paragraph-allocation overflow assertion. Those are not counted as successful runs. The final suite passed after correcting the tests and using a smaller display. The original display configuration was restored afterward.

Evidence: `.stabilization/verification/AndroidUiResults.xml`, `app/build/reports/tests/testDebugUnitTest/index.html`, and `app/build/reports/androidTests/connected/debug/index.html`.

## F. Navigation verification

Automated checks passed for Home, Journey, Appointments, Health Journal and Profile destinations over repeated switching; Back from Journey returns to Home. Profile save pops the editor only after success, Home observes the changed name, and Journey remains available. The selected Journey tab restores after saved-instance-state recreation.

Onboarding remains on its destination while persistence is pending or failed, navigates to Home after a successful save, and leaves no onboarding entry behind. Splash remains until initial preferences arrive. Home Journey and journal callbacks work at both tested font scales.

The separate QA app was also launched with its real preference repository; after onboarding completion and an app-only restart, Home reappeared, confirming persisted completion. Home, Appointments and Emergency guidance were visually observed in that QA session. No emergency call action was invoked.

## G. Data integrity

No production pregnancy profile was cleared, saved, replaced or migrated during verification. The original installed app was not updated, uninstalled, force-stopped or reset by the repair commands. All app-only restart commands targeted the separate QA package. Unit persistence tests used temporary files; device tests injected fake repositories.

Production DataStore filenames and keys are unchanged. The existing transactional edit operations remain; reads still propagate failures. No corruption handler, destructive migration, schema change, authentication system, database or notification permission was introduced. Rollback copies protect existing source changes independently of the unrelated parent Git repository.

After verification, the production package was confirmed still installed. The emulator was not reset or wiped; its original 1344x2992 / 480 dpi display was restored and confirmed. The final APK metadata identifies the normal package as `com.monicacare.app`.

## H. Regression check

All existing calculation, Home, Journey and profile-editor unit tests passed. The targeted UI, navigation, save callbacks, onboarding, reactive profile updates and resume paths passed device tests. Unchanged appointment, journal, profile, education and emergency screen implementations remain part of the successful debug build.

The current development lookup uses the existing nearest earlier catalog entry. That catalog and its medical content were not rewritten. Existing demonstration appointment/tip content was also retained.

## I. Remaining risks and verification limits

- Device coverage is one Android 17 emulator. Full instrumentation used a temporary 720x1600 / 260 dpi display (about 443 dp wide) to avoid host/emulator memory pressure. It passed at 1x and 2x fonts; this is not certification for every device, tablet, orientation or accessibility setting.
- At native resolution the emulator showed severe frame delays, and its initial test process was killed for memory pressure. Final test logs contained no matching application fatal exception, failed-test entry or MonicaCare low-memory kill. App performance was not benchmarked or optimized in this task.
- Midnight and timezone broadcasts were wired and inspected; the clock was not changed to force a real midnight event. Deterministic date changes and the actual pause/resume refresh path were tested.
- Profile UI saves were verified against isolated fake repositories, and real DataStore persistence was verified separately with temporary files. No production records were used to prove an end-to-end save.
- Education content and every action on untouched screens were not exhaustively exercised. This report does not claim a full medical-content audit or a complete device-matrix regression run.
- The project still needs a project-scoped Git repository/history for ordinary versioned rollback. The parent repository was deliberately left untouched.

## J. Final status

**FIX VERIFIED** for the targeted repairs, within the build, automated test and emulator coverage recorded above. The normal debug build, all 51 unit tests and all nine device behavioural tests succeeded. Broader coverage limits remain explicitly listed in section I.
