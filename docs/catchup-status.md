# Catch-up status (brief v0.6 -> v1.3)

The brief was written against a v0.6 baseline. This project had already moved on in its own direction (v0.7 to v1.3.1:
Firebase pairing-code sync, a separate family app, safety flags, vitals). Where the brief and the code disagree, finished
work is kept and the gap is filled; nothing is rewritten for the sake of matching the brief's wording.

Version mapping: the brief's v0.7 to v1.3 would collide with tags that already exist, so these phases ship as
**v1.4 (A), v1.5 (B), v1.6 (C), v1.7 (D), v1.8 (E), v1.9 (F), v2.0 (G)**.

Nothing in this file was verified on a phone or emulator. Verification so far is unit tests, lint and assembleDebug
(Android) and vitest and tsc (backend). Phone checks are listed per phase under "needs a device".

Baseline before starting (2026-11-04): backend 39 tests and typecheck green; Android unit tests, lint, assembleDebug green.

| Phase | Item | Status | Evidence |
|---|---|---|---|
| A1 | Boot / update / time / time-zone / exact-alarm receivers | done | `BootReceiver` manifest actions, `RescheduleWorker.enqueue` |
| A1 | `rescheduleAll(reason)` via WorkManager, 12 h safety net | done | `ReminderScheduler.rescheduleAll`, `RescheduleWorker.ensurePeriodic` |
| A1 | Health issues NO_RECENT_RESCHEDULE, ALARM_COUNT_MISMATCH | done | `domain/ReminderHealth.kt`, `ReminderHealthTest` |
| A1 | Names EXACT_ALARM_DENIED / BATTERY_OPTIMIZED | kept existing names | `EXACT_ALARMS_OFF`, `BATTERY_RESTRICTED` (same meaning) |
| A1 | Health screen, test reminder, 20-entry log | done | `HealthScreen.kt`, `Prefs.rescheduleLog`. Test reminder posts straight away, not in 1 minute (existing behaviour) |
| A1 | Missed while phone off | done | `RescheduleWorker.sweepMissed`, `MissedSweep` + test |
| A2 | `/api/policy-question` with citation verifier | done | `backend/lib/policyQuestion.ts`, `policyQuestion.test.ts` |
| A2 | "Ask about your policy" UI, consent each time, prefill from tag | done | `ui/AskPolicy.kt`. Clauses sent are the saved summary quotes (the document itself is never kept on the phone) |
| A3 | Daily LLM budget, JSON request log, `/health` | done | `budget.ts`, `log.ts`, `health.ts`, `budget.test.ts`. Default store is per-instance memory; see `docs/backend-production.md` |
| G2 | Claim card + backend confidence/clauseRef/excerpt | done early in A2 | `domain/ClaimAnswer.kt`, `ClaimAnswerTest` |
| B1 | Home-screen widget | done, **RemoteViews instead of Glance** | `reminder/DoseWidget.kt`, `domain/NextDose.kt`, `NextDoseTest`. No new dependency; same states (due, upcoming, +N more, all done with tomorrow, empty, overdue) |
| B2 | Refill history, "I bought more", undo | done | `RefillEvent`, migration 10 to 11, `RefillUi.kt`, `RefillTest`, `MigrationTest` (instrumented, not run) |
| B2 | Monthly order message | done | `Messages.monthlyOrder` + button on Medicines; user-initiated share only |
| B2 | Backup / delete-all with refills | done | backup version 9, `BackupTest`; old files still import |
| B3 | Text size, high contrast, Simple mode | done | `domain/Display.kt`, `DisplayTest`, Settings > Display |
| B3 | Screen audit and semantics tests | partial | dose rows, Taken, source links, chart days and the family ring fixed; `AccessibilityTest` compiles but has not run. A full TalkBack walk-through of every screen is **needs a device** |
| C1 | First-run onboarding (5 steps, resumable) | done | `domain/Onboarding.kt`, `OnboardingTest`, `ui/OnboardingScreen.kt`. Step 3 hands off to the existing scan / add screens |
| C1 | "What is new" for existing users, replay in Settings | done | `Prefs.whatsNewSeen`, `vm.replayGuide` |
| C2 | Privacy page, policy, Play docs, privacy.html | done | `ui/PrivacyScreen.kt`, `docs/privacy-policy.md`, `docs/play-store/*`, `backend/scripts/build-privacy.mjs`, `PrivacyDocsTest`, `privacy.test.ts`. `SUPPORT_EMAIL` / `PRIVACY_URL` are blank placeholders (TODO). Legal review still needed |
| C3 | `UserDataRegistry` drives export, restore, delete-all | done | `data/UserDataRegistry.kt`, `UserDataRegistryTest` |
| C3 | Password-protected backups | done | `data/BackupCrypto.kt`, `BackupCryptoTest`. UI not run on a device |
| C3 | Transactional restore with preview, delete-all summary | done | `BackupService.restore` uses `withTransaction`; the SQL-in-transaction path **needs a device check** |
| D1 | Anonymous auth, consent screen, invite code, single use, expiry | done, **different from the brief** | existing `pairings/{code}` design: the code is the document id (no listing, so no hash needed), 15-minute expiry (brief: 24 h), 8-character alphabet as briefed |
| D1 | No names or medicine names by default, separate switch | done | `SyncPrivacy`, `Prefs.shareNames`, `SyncPrivacyAndLockoutTest` |
| D1 | Brute-force limit: 5 wrong codes, 15 minutes | done | `CodeLockout`, tests |
| D1 | `links/{linkId}/days` slot documents, `buildDaySnapshot` | **not built** | the family view uses events plus the status document; `FamilyView.merge` makes taken win over a stale missed |
| D1 | Stale warning over 2 h, unlink either side | done | `FamilyView.isStale`; unlink existed |
| D1 | `BuildConfig.SYNC_AVAILABLE` | equivalent | `CareSync.isConfigured` (no `google-services.json` means "not set up"); both builds verified without the file |
| D1 | Rules tests with the emulator | done, ran here | `firebase/rules-test`, 16 tests pass |
| D2 | Signing from env or properties, R8, version in gradle.properties | done | `app/build.gradle.kts`, `proguard-rules.pro`. R8 build succeeds; **a release build has not been run on a phone** |
| D2 | `check-release.sh`, `android.yml`, `signing.md` | done | `scripts/check-release.sh` passes locally; the workflow has not run on GitHub |
| D3 | Key rotation, headers, body limit, CORS, `/health`, loadtest, docs | done | `lib/http.ts`, `hardening.test.ts` (71 backend tests), `docs/backend-production.md`. The loadtest script was not run against a deployment |
| E1 | `/api/notify-missed`, Admin SDK, rate limits, data-only push | done | `backend/lib/notifyMissed.ts`, `notifyMissed.test.ts` (fakes for Firebase; **not run against a real Firebase project**). Status codes as briefed plus 409 for no token and 503 when unconfigured |
| E1 | Family phone token, switch, local notification, dedupe, pending retry | done in code | `Push.kt`, `CaregiverMessagingService`, `CaregiverAlert`, `CaregiverAlertTest`. **Needs two phones, the service account and republished rules to verify** |
| E1 | Rules for the token and tests | done, ran here | `firebase/rules-test` (20 tests) |
| E2 | Smoke tests, debug-only hook, CI emulator job, `docs/testing.md` | written, compile only | `app/src/androidTest/.../smoke`, `android-emulator.yml`. **Not run on an emulator or phone; the CI job has not run** |
| E3 | Weekly summary, preview, names toggle, Sunday reminder | done | `WeeklySummary`, `WeeklySummaryTest` (incl. a clock-change case), `ui/WeeklyText.kt` |
| D1 gap fixed | Daily summary event leaked the person's name with names off | fixed | `DailySummaryReceiver` |
| F1 | Up to 3 caregivers per parent, labels, remove, limit, per-link push | done, **different storage** | links live in the Room table `sync_link` (with a new local `label`), not a Prefs JSON list; `ParentLinks`, `ParentLinksTest`, migration 11 to 12. The Firestore schema needed no change, rules tested for two links |
| F1 | "A caregiver supports one claimed link" | **not applied** | the family app already follows several people and that was kept |
| F1 | Removed caregiver sees "no longer have access" and clears the link | done | `vm.checkAccess`, banner on the family home |
| F1 | Legacy single-link migration | not needed | there was never a single-link Prefs value |
| F2 | Quiet hours, snooze, one overnight digest | done | `QuietHours`, `QuietHoursTest` (cross-midnight, snooze chain), `QuietDigest`. **Needs a family phone to verify the digest and the timing** |
| F3 | Live scanner: crop to the guide, 2048 px, torch, tap to focus, 64 dp capture, cancel, photo picker, settings button, fallback camera | done | the CameraX scanner already existed; `CropMath` + `CropMathTest`. **The live behaviour (focus, crop alignment) needs a phone** |
| G1 | Acknowledgements: notification button, status button, merge into every link, rules | done in code | `Acks`, `AcksTest`, `AckReceiver`, `CareSync.mergeAcks`, rules + 6 rules tests (ran here). Differences: the key is `UTC-day_doseTimeMillis` (the push carries `slotId` = dose time); the family phone cannot read acks, it sees them through the parent's status. **Needs two phones and republished rules to verify the whole loop** |
| G2 | Claim card, verdict chip, citations, confidence | done earlier (A2) | `ClaimAnswer`, `AskPolicy.kt`, `policyQuestion.ts` and their tests |
| G3 | Diagnostics screen, opt-in crash log, enum-only events | done | `Diagnostics.kt`, `DiagLog.kt`, `DiagnosticsScreen.kt`, `DiagLogTest` (incl. a source scan). Debug-only crash button in `src/debug`, empty twin in `src/release` |

## What was verified, and how

| Check | Result |
|---|---|
| `cd backend && npm ci && npm test && npm run typecheck && npm run build:privacy` | green (84 backend tests) |
| `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease` | green |
| `scripts/check-release.sh` | passes locally (permissions documented, no secrets, no debug hooks in release) |
| Firestore rules against the emulator (`firebase/rules-test`) | 28 tests pass |
| Builds with no `google-services.json` and no signing keys | yes, debug and unsigned release |

## What needs a phone, an emulator or a real Firebase project (not done)

- Running `./gradlew :app:connectedDebugAndroidTest` (smoke tests, `AccessibilityTest`, `MigrationTest` through every version). This wipes the app's data on that device.
- The CI emulator job and the release job have never run on GitHub.
- Family push and "mark as handled" end to end: two phones, a service account in Vercel (`FIREBASE_SERVICE_ACCOUNT_JSON`) and the republished `firestore.rules`.
- R8: a release build has not been started on a phone.
- The widget, the live scanner crop and focus, TalkBack on every screen, Extra large text in Hindi and Gujarati.
- Hindi and Gujarati text for everything added since v1.3 is machine-drafted and needs a native speaker.
- Legal review of `docs/privacy-policy.md` and the Data safety answers; `SUPPORT_EMAIL` and `PRIVACY_URL` are blank.
- Nothing in this catch-up was deployed to Vercel.

## Where the work deliberately differs from the brief

1. The Firebase sync keeps its first design (pairing codes as document ids, events plus a status document) instead of the brief's `links/{linkId}/days` slots and hashed codes; names are off by default as the brief requires.
2. The widget stays on RemoteViews, not Glance (no new dependency, same states).
3. A family phone can still follow several people.
4. Versions are v1.4 to v2.0 because the brief's v0.7 to v1.3 tags already existed.
