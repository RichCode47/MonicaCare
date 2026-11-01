# MonicaCare Supabase setup

The Android integration and database migration are supplied locally. No hosted project has been linked or modified. The application remains usable with device storage when public configuration is absent.

## Configure an existing project

1. Open your Supabase project's Connect dialog or Settings > API Keys. Copy the project URL and a **publishable** key.
2. Add these two entries to the project's existing, ignored `local.properties`, keeping its Android SDK setting:
   ```properties
   SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_REPLACE_LOCALLY
   ```
3. Rebuild the app. Gradle reads public Gradle properties first, then `local.properties`, then environment variables. `supabase.env.example` is a reference; Gradle does not load .env files.
4. Never put secret/service-role keys, database passwords, or management tokens in the app, repository, chat, or build arguments. Legacy JWT keys are deliberately rejected. The publishable key is recoverable from an APK and grants access only through database/Storage policies.

See [Supabase API keys](https://supabase.com/docs/guides/getting-started/api-keys).

## Review and deploy the schema

Review `supabase/migrations/202611010001_monicacare_foundation.sql` against the existing project's schema and migration history before applying it. It creates new objects in one transaction and deliberately fails on name collisions. It does not drop/replace existing tables, clear rows, or migrate Android DataStore.

For a non-empty project, reconcile its remote schema/history in a separate review checkout first. Do not force this foundation over existing tables with the same names. Supabase's [migration workflow](https://supabase.com/docs/guides/deployment/database-migrations) explains baseline/history management.

Using the locally authenticated Supabase CLI from this repository:

```powershell
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase migration list
supabase db push --dry-run
```

Check the project reference and pending migration list. Dry-run lists migrations; it does not validate every SQL statement. Apply the reviewed migration only to the intended project:

```powershell
supabase db push
```

Enter CLI credentials in its local authentication/prompt flow. Do not commit them. Never use database reset, migration repair, or destructive commands to bypass a deployment error. The checked-in `config.toml` configures an optional local stack; it does not update hosted Auth settings. Local HTTP endpoints are deliberately not accepted by the Android production configuration.

## Auth and first use

Enable email/password authentication and email confirmation in the hosted Auth settings. Set a valid HTTPS Site URL for your verification landing page. Configure email delivery and rate limits appropriate to real users; Supabase's default mail delivery is intended for development. No mobile deep link is registered: after confirming email in the browser, users return to MonicaCare and sign in.

Open Profile > Account & Sync to register or sign in. A signup awaiting confirmation is not treated as a signed-in session. Initial account access downloads private records before allowing changes. Existing device pregnancy information remains separate. The explicit **Copy device records to account** action copies the device pregnancy, appointments, and journal once, only into an account without existing pregnancy records. Originals are retained, and copying does not upload immediately.

Changes save locally. **Sync now** uploads pending writes and then downloads server records. A failed request preserves the outbox. Version conflicts require an explicit choice between the displayed server and local values. Sign-out removes this device's credentials; offline sign-out cannot promise server token revocation. Encrypted account caches remain on the device for subsequent sign-in.

## Data, policies, and content

| Data | Access and behavior |
| --- | --- |
| profiles, pregnancy_profiles, appointments, health_journal, user_settings | Authenticated owner only; forced RLS, validated writes, server versions, recoverable deletion markers |
| pregnancy_weeks, educational_articles | Anonymous/authenticated reads of published, reviewed entries; no mobile writes |
| emergency_contacts | Reads of active, verified entries; no mobile writes |
| monicacare-private Storage bucket | Private owner UID folders; PDF/JPEG/PNG, maximum 5 MiB, no overwrite/public URLs |

Appointments and journal entries use a composite foreign key that prevents linking another user's pregnancy. The synchronization RPC is SECURITY INVOKER and derives ownership from the user JWT. Publishable keys cannot edit educational content or directory entries.

Publish content through a trusted administrator using the Dashboard/SQL workflow. Set `reviewed_at` before `published=true`; verify emergency telephone numbers and set `verified_at` before `is_active=true`. These flags record review workflow, not independent proof of clinical accuracy. No fictional clinical content or emergency numbers are seeded. Keep the existing educational disclaimer. Fruit comparisons are illustrations and growth values are general estimates, not individual fetal measurements.

The app retains bundled journey information offline. Education offers a read-only public refresh without registration. Explicit account sync caches public content for 24 hours; explicit guidance refresh bypasses that cache. Downloads are bounded to 43 weeks, 50 recent articles, and 200 contacts.

The container exposes private document upload infrastructure; an attachments picker, record-to-file associations, download UI, and retention workflow are not implemented. Do not treat this as a completed medical-document feature.

## Offline storage and operating costs

The original guest pregnancy/preferences DataStore files and calculations are unchanged. New account/device record caches and sessions use Android Keystore AES-GCM encryption in no-backup files. Encryption/read failures are surfaced; no corruption handler silently resets records. Loss of the encryption key cannot recover unsynced data. Device records still require a deliberate backup/export policy before clinical production use.

There is no Realtime subscription, continuous health-data polling, AI integration, paid backend, or separate Ktor server. Ktor is the Supabase HTTP client. Manual synchronization, collapsed pending edits, paged reads of 200 records (10,000 limit per private table), a public-content cache, and bounded uploads limit traffic. Full private reconciliation is intentional for this initial small-data foundation; revisit cursor-based sync before large deployments.

Check [current plan limits](https://supabase.com/pricing) and [production guidance](https://supabase.com/docs/guides/platform/going-into-prod). A free project can be paused and does not provide an emergency-care availability guarantee. Arrange database/Storage backups, recovery exercises, retention, account deletion, monitoring, and clinical/privacy review appropriate to the deployment.

## Verify safely

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
npm --prefix tools/backend-tests ci --ignore-scripts
npm --prefix tools/backend-tests test
.\gradlew.bat :app:connectedDebugAndroidTest -PmonicacareIsolatedQa=true
```

The QA flag installs `com.monicacare.app.qa` alongside the real app and forces cloud configuration empty. Automated tests use synthetic records, mock HTTP, and isolated PostgreSQL storage. To test a separate hosted QA project manually, explicitly add `-PmonicacareQaCloud=true` with that QA project's public configuration; the offline-only device tests expect cloud configuration to remain empty.

Live acceptance still requires two disposable accounts in the intended test project: email confirmation/login, cross-account REST/RPC/Storage denial, offline edit/restart/reconnect, simultaneous-device conflict resolution, revoked-session recovery, and server persistence after reinstalling only a disposable QA app. Never clear or uninstall the user's production app for verification.

## Compatibility

This repository currently contains one Android module, not a shared Kotlin Multiplatform source set. The Supabase client and serialized DTOs provide a future sharing boundary; Android Keystore, Java date/UUID helpers, and UI adapters remain Android/JVM-specific.

Supabase Kotlin 3.2.6/Ktor 3.3.1 are pinned for the existing Kotlin 2.2 toolchain. Newer SDK lines require reviewing Kotlin compatibility. The transport helper disables static SDK loggers and removes the anonymous publishable-key bearer fallback while retaining the apikey header and authenticated user JWT. Auth contract tests cover these safeguards. Revalidate them when updating the SDK; no unrelated compiler/architecture upgrade was made.
