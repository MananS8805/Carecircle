# Testing

## What runs where

| Suite | Command | Needs | Where it runs |
|---|---|---|---|
| Android unit tests (domain logic, backup, registry, docs checks) | `./gradlew :app:testDebugUnitTest` | JDK 17 | every push (`android.yml`) |
| Lint (missing translations and hard-coded text are errors) | `./gradlew :app:lintDebug` | JDK 17 | every push |
| Backend tests and type check | `cd backend && npm test && npm run typecheck` | Node 20 | every push |
| Firestore rules | `cd firebase/rules-test && npm ci && npm test` | Java | on demand (starts the Firestore emulator) |
| Emulator smoke tests | `./gradlew :app:connectedDebugAndroidTest` | emulator or phone | pull requests touching `app/` and version tags (`android-emulator.yml`) |
| Release checks | `scripts/check-release.sh` | JDK, SDK | version tags, and by hand before an upload |

## The emulator smoke tests

They live in `app/src/androidTest/java/com/carecircle/smoke` and use AndroidX Test and Compose UI test (test dependencies only):

- `AddMedicineAndTakenTest`: a medicine added in the Edit screen shows in the list; tapping Taken marks the dose and uses one pill.
- `DoubleTapTakenTest`: two taps, or the notification path and the widget path, never use a second pill.
- `WidgetRefreshTest`: the widget's state machine from due to taken to all done (no RemoteViews, so it is fast).
- `BackupRoundTripTest`: plain and password-protected backups restore everything, a wrong password is refused, a newer file is refused.
- `ReminderSchedulingTest`: asks the system (`dumpsys alarm`) what is scheduled, so a bookkeeping bug cannot hide.
- `OnboardingSkipTest`: a fresh install shows the guide and Skip lands on Today.
- `SyncDisabledTest`: a build without Firebase explains that Family sync is not set up.
- `AccessibilityTest` (in `ui/`): the dose row is one sentence and a live region, button sizes.

Rules they follow: they never sleep (they `waitUntil` a condition with a limit); animations are off (`testOptions.animationsDisabled`
and the runner flag); notifications are granted by `GrantPermissionRule`; each test starts from `DebugReset.reset`, a hook that exists
only in the debug build (`app/src/debug`). `scripts/check-release.sh` fails if that class is found in a release build. The whole
suite is meant to finish in under five minutes.

Tests that depend on the time of day (a dose "one minute ago") skip themselves in the first five minutes after midnight.

## Running them on your phone

1. Enable USB debugging and connect the phone.
2. `./gradlew :app:connectedDebugAndroidTest`

**This wipes the app's data on that phone**: every test starts with `DebugReset`, which calls "Delete all my data". Use a spare phone or
an emulator, or export a backup first. The instrumented migration test (`MigrationTest`) also replaces the database. Do not run either
on a phone that holds real medicine records you have not backed up.

## Status

As of the catch-up work (see `catchup-status.md`) the smoke tests compile (`:app:compileDebugAndroidTestKotlin`) but have **not** been
run on an emulator or phone, and the CI emulator job has not yet run on GitHub.


## Running everything (v2.0.1)

| Layer | Command | Count |
|---|---|---|
| Android JVM (domain, contracts, strings) | `./gradlew :app:testDebugUnitTest` | 332 |
| Backend | `cd backend && npm test && npm run typecheck` | 84 |
| Firestore rules | `cd firebase/rules-test && npm test` (needs Java) | 44 |
| On a phone | `./gradlew :app:connectedDebugAndroidTest -Pcarecircle.clone=true` | 72 |
| Release checks | `scripts/check-release.sh` | 9 checks |

- **Always run the phone tests with `-Pcarecircle.clone=true`.** They wipe the app's data, and this installs them as a separate
  copy (`com.carecircle.clone`) so your real app and its records are untouched. The copy needs a `com.carecircle.clone` client
  entry in your local `app/google-services.json` (copy the existing client and change the package name).
- `ContractTest` compares the app, `firebase/firestore.rules` and the backend field by field. If it fails, one side changed.
- `RepositoryGuardTest` and `ContractTest.noFunctionThatOnlyReadsContainsADeletingCall` exist because two stray lines in
  `Repository.adherence()` once ended every family link. A read must never delete.
- Smoke-test the minified release on a phone without replacing the real app:
  `./gradlew :app:assembleRelease -Pcarecircle.releaseSuffix=.r8test` (needs that package in `google-services.json`).
