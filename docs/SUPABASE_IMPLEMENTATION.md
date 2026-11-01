# MonicaCare backend engineering report

Date: 1 November 2026

Status: **PARTIALLY VERIFIED** — local implementation verified by build/JVM/database tests; the final emulator run was interrupted and is not green. Hosted deployment and live acceptance remain outstanding.

## A. Baseline

The starting Git working tree was clean at `a49733dd892571bee0e2178154070464b934b220`, the existing stabilization rollback point. The initial debug build and 51 existing JVM tests passed. The prior stabilization report also recorded nine passing emulator tests.

The actual repository has one Android `:app` module. It uses AGP 9.5.0, Kotlin/Compose tooling 2.2.10, MVVM/StateFlow, Navigation Compose, and DataStore. A shared KMP module is not present. The frontend architecture, application package, theme, onboarding, local pregnancy model, and calculator were retained.

No Supabase URL/publishable key or connected Supabase management tools were available. The supplied backend request ends mid-sentence in its RLS section; clarification and a project reference were requested. No hosted schema, Auth settings, real account, Storage object, or production pregnancy record was changed.

## B. Confirmed gaps and reliability findings

- Appointments, journal, education, and some profile/settings controls previously relied on demo or placeholder behavior. There was no authenticated backend or account ownership boundary.
- A backend read failure must block blind saves until account initialization succeeds. The new repositories enforce this explicitly rather than creating an apparently empty account.
- Offline retries can duplicate records or overwrite concurrent edits without idempotency/version checks. The new RPC/outbox provide mutation identity and optimistic concurrency.
- The compatible SDK line sends its key as an anonymous bearer fallback. A transport hook retains the publishable `apikey` header and removes that fallback; authenticated requests retain the user's JWT.
- SDK static loggers capture their initial log level. The pinned SDK's mutable-level API can throw after setting the level because its Kermit config is immutable. The transport helper verifies that logging is disabled and fails closed if that guarantee changes.
- An encrypted-session read failure during automatic SDK startup could remain unhandled. App-owned initialization now displays Retry without deleting the saved session. An expired session retains its account context during an offline refresh.
- A new appointment editor initially displayed a generic error for an invalid date. Emulator verification exposed it; date parsing failures now receive a field-validation message.

## C. Implementation

### Client and accounts

Public build configuration accepts `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from ignored local properties/build configuration. HTTPS and a public-key prefix are validated; secret and legacy JWT keys are rejected. Only INTERNET was added. No notification, phone-call, broad file, or contact permissions were introduced.

Profile > Account & Sync supports email/password registration, email-confirmation guidance, login, local sign-out, account display name, language/reminder preferences, manual synchronization, explicit device-data copying, and conflict review. Signup awaiting confirmation does not create an authenticated UI session. Account display-name rows are created when the name is saved.

Navigation is recreated per account identity so an editor or restored destination from another account cannot write through a stale callback. Repositories independently validate their captured owner.

### Persistence and synchronization

Existing guest pregnancy/preferences DataStore files remain unchanged. New caches and sessions use authenticated AES-GCM encryption with an Android Keystore key and no-backup files. Read/decryption failures are surfaced and never silently replaced with empty data.

Private writes first enter an encrypted, account-scoped outbox. Multiple edits collapse to the latest local mutation. Manual sync pushes pending writes in relationship order, then reconciles private records. Acknowledgement removes only the mutation actually sent; an edit made while HTTP is in flight remains pending. Retry after a lost response uses the same mutation ID.

The RPC assigns ownership from Auth, checks expected versions, and advances server-managed versions/timestamps. Conflicts preserve both local and server values until the user chooses. Removing a private record queues a tombstone rather than a hard delete. Initial downloads are shared across concurrent screen subscriptions. No continuous health-data polling or Realtime subscription was added.

A one-time, explicit copy imports guest pregnancy/appointments/journal into an initially empty pregnancy account. It retains original device records and queues changes locally. Login alone never uploads legacy records.

### Database and content

The migration creates all eight requested tables, foreign keys, ownership/date/status/size constraints, indexes, review flags, forced RLS, and a SECURITY INVOKER synchronization RPC. Composite pregnancy/owner foreign keys prevent cross-account links. Catalog writes are unavailable to mobile roles.

Published/reviewed pregnancy weeks and articles and active/verified emergency contacts are read-only public content. Refresh works without registration. Existing bundled pregnancy content remains available offline; no clinical or emergency data is fabricated or seeded.

The app's real navigation uses editable cached appointments and journal entries, current upcoming appointments on Home, downloaded journey guidance, published articles, and a verified emergency directory/personal contact. Existing educational disclaimers and medical calculations are retained. Emergency calls open the dialer rather than placing a call.

A private Storage bucket enforces owner folders, PDF/JPEG/PNG, a 5 MiB limit, and no overwrite/public URLs. An explicit-upload client method is provided. An attachment picker, file-to-record associations, download UI, and retention workflow remain outside this foundation.

## D. Files modified/added

Existing files modified:

- `.gitignore`
- `README.md`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/monicacare/app/MonicaCareApp.kt`
- `app/src/main/java/com/monicacare/app/core/navigation/MonicaCareNavHost.kt`
- `app/src/main/java/com/monicacare/app/core/navigation/NavRoutes.kt`
- `app/src/main/java/com/monicacare/app/feature/home/HomeViewModel.kt`
- `app/src/main/java/com/monicacare/app/feature/home/HomeDashboardScreen.kt`
- `app/src/main/java/com/monicacare/app/feature/pregnancy/JourneyViewModel.kt`
- `app/src/main/java/com/monicacare/app/feature/pregnancy/JourneyScreen.kt`
- `app/src/main/java/com/monicacare/app/feature/profile/ProfileScreen.kt`
- `app/src/main/java/com/monicacare/app/feature/emergency/EmergencyScreen.kt`

New files:

- `app/src/main/java/com/monicacare/app/core/backend/BackendModels.kt`
- `app/src/main/java/com/monicacare/app/core/backend/BackendStore.kt`
- `app/src/main/java/com/monicacare/app/core/backend/AndroidSecureStorage.kt`
- `app/src/main/java/com/monicacare/app/core/backend/AccountAuth.kt`
- `app/src/main/java/com/monicacare/app/core/backend/BackendRemote.kt`
- `app/src/main/java/com/monicacare/app/core/backend/SyncEngine.kt`
- `app/src/main/java/com/monicacare/app/core/backend/BackendRepositories.kt`
- `app/src/main/java/com/monicacare/app/core/backend/BackendContainer.kt`
- `app/src/main/java/com/monicacare/app/core/backend/BackendTransport.kt`
- `app/src/main/java/com/monicacare/app/feature/account/BackendViewModel.kt`
- `app/src/main/java/com/monicacare/app/feature/account/AccountScreen.kt`
- `app/src/main/java/com/monicacare/app/feature/account/ManagedRecordsScreens.kt`
- `app/src/main/java/com/monicacare/app/feature/account/RecordSummary.kt`
- `app/src/test/java/com/monicacare/app/backend/BackendSyncTest.kt`
- `app/src/test/java/com/monicacare/app/backend/AccountAuthContractTest.kt`
- `app/src/androidTest/java/com/monicacare/app/backend/BackendDeviceTest.kt`
- `supabase/migrations/202611010001_monicacare_foundation.sql`
- `supabase/config.toml`
- `supabase/.gitignore`
- `supabase.env.example`
- `tools/backend-tests/package.json`
- `tools/backend-tests/package-lock.json`
- `tools/backend-tests/backend-schema.test.mjs`
- `docs/SUPABASE_SETUP.md`
- `docs/SUPABASE_IMPLEMENTATION.md`
- `.stabilization/backend/AndroidUiResults.xml`

## E. Executed checks

| Check | Actual result |
| --- | --- |
| Initial debug build and existing JVM suite | PASS, 51 tests |
| `:app:assembleDebug :app:testDebugUnitTest` | PASS, 74 tests, no failures/skips |
| Final normal debug/unit/Android-test APK compilation | PASS (`:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`); no installation |
| Isolated PostgreSQL migration/policy tests | PASS, 13 tests on the final migration, no failures/skips |
| Latest QA emulator suite | NOT PASSING: four tests passed, one selector assertion failed, one session test was interrupted, and the runner failed before completing the expected 16 tests |
| Hosted Supabase deployment/Auth/REST/Storage | Not run: no configured project |

The 23 new JVM tests cover pending writes, account isolation/stale callbacks, initialization failure/retry, duplicate download prevention, lost-response retries, edits during requests, cancellation, version conflicts/resolution, tombstones, explicit copying, serialization, public cache refresh, response identity validation, key/URL rejection, real SDK login/signup/session restoration/offline sign-out, and transport authentication headers.

SQL tests execute the actual migration in isolated PostgreSQL WASM with Auth/Storage stand-ins. They exercise all eight tables, grants/RLS, owner/anonymous denial, forged identity, cross-owner references, CAS/idempotency, public publishing restrictions, private storage folders, date constraints, and preservation of an unrelated existing schema's permissions. They are not a substitute for hosted gateway/Storage acceptance.

Auth contract tests use Ktor MockEngine and synthetic tokens. A JVM Android OS-information warning is expected from SDK initialization against Android stubs; it is not an application crash and contains no real user credentials.

## F. Navigation and regression verification

The existing JVM suite remains green for pregnancy dates, leap years, trimester boundaries, due/overdue progress, Home/Journey state correctness, repository errors/Retry, profile observation, date refresh, onboarding persistence, and preferences.

The emulator suite includes the existing onboarding, top-level tab/back-state, profile editor, missing-development/error states, and normal/enlarged-font Home tests. New tests exercise the actual app's offline navigation, editable appointments/journal, account preferences, Android Keystore authentication/tamper rejection, encrypted cache reopening, and encrypted session round-trip.

No production-package installation or real record edit was used for backend verification. The separate QA build forces cloud configuration empty.

The latest run completed account preferences, encrypted cache reopening, actual app navigation, and journal save/removal successfully. The appointment error text appeared in both the page and dialog, causing an overly strict test selector to fail; that selector is corrected, but the correction has not been rerun on a device. The session round-trip test then stalled and the runner reported an incomplete run. Earlier backend verification had passed this session test, the cipher test, and the nine existing stability UI tests; those earlier results do not establish a final green suite.

The IDE call timed out after five minutes. Captured logs show repeated emulator UWB-service crashes; no MonicaCare fatal exception was found in the captured portion. Subsequent Logcat, process, and system-property reads could not complete. A data-preserving ADB reconnect returned successfully, but the emulator then reported offline. The exact cause of the interrupted session test remains unconfirmed. Results are preserved in `.stabilization/backend/AndroidUiResults.xml`.

The temporary emulator display override (720x1600, density 260; original 1344x2992, density 480) could not be restored while the device was offline. Once ADB responds, restore display defaults with `adb -s emulator-5554 shell wm size reset` and `adb -s emulator-5554 shell wm density reset`. These commands restore display overrides, not app data. No emulator reboot/factory reset or data wipe was performed. The final UI rerun and its Logcat review remain outstanding.

## G. Data integrity

No DataStore clearing, emulator data reset, production app uninstall, schema replacement, or destructive Git command was performed. Original pregnancy records and preferences are not migrated automatically. Synthetic test files/accounts are isolated. SQL migrations are transactional and refuse collisions instead of replacing existing objects. New cache read failures preserve bytes for investigation. Sign-out does not discard pending account records.

No secret, service-role key, database password, or management token was added. No hosted database/Storage operation was performed.

## H. Operating and remaining work

See [setup instructions](SUPABASE_SETUP.md) for deployment review, public configuration, Auth email delivery, clinical content publishing, cost controls, and safe live acceptance.

Remaining deployment/production work:

1. Recover emulator connectivity without wiping data, restore the temporary display override, and rerun the corrected QA suite.
2. Supply the intended project URL/reference and configure its publishable key locally; clarify the truncated remainder of the request.
3. Review the existing hosted schema/history, apply the additive migration, and configure email confirmation, a real verification landing URL, and email delivery.
4. Run two-account hosted RLS/REST/RPC/Storage, restart/offline/reconnect, token revocation, and multi-device conflict checks with disposable data.
5. Publish clinically reviewed education and independently verified emergency contacts. No live directory has been supplied.
6. Establish backup/recovery, retention, account deletion, monitoring, and clinical/privacy operating procedures before real healthcare use.
7. Implement document associations/picker/download UX only after its intended workflow and retention requirements are defined.
8. Review SDK/toolchain upgrades together. The pinned compatibility helper uses SDK internal configuration APIs and must be retested on upgrade.
9. Plan shared source sets/platform adapters separately if full iOS/KMP delivery is required; this work did not convert the repository.

The foundation uses manual synchronization, bounded pages/uploads, and cached public content to limit traffic. It does not guarantee a free production deployment at every scale or emergency-service availability. No cloud AI, Firebase integration, paid service, or separate Ktor backend was introduced.

## I. Final status

**PARTIALLY VERIFIED.** Local backend functionality is implemented, with hosted connection/deployment and live end-to-end verification outstanding. This report does not certify production readiness or completed hosted integration.
